package net.mirky.redis;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

abstract class ReconstructionItem {
    abstract void apply(byte[] image, ExternalFileLoader loader) throws ReconstructionFailure.DataInconsistent;
    abstract void writeOut(PrintWriter writer);
    abstract boolean isTerminatingItem();
    
    // returns null or throws NumberFormatException to signal parse error
    static final ReconstructionItem parse(String[] fields) throws ReconstructionFailure.DataInconsistent {
        ReconstructionItem item;
        if (fields[0].equals("contiguous")) {
            if (fields.length != 5) {
                return null;
            }
            item = new ContiguousFile(fields[1], Hex.pt(fields[2]), Hex.pt(fields[3]),
                    Hex.pt(fields[4]));
        } else if (fields[0].equals("blocks")) {
            if (fields.length != 6) {
                return null;
            }
            item = new FileBlocks(fields[1], Hex.pt(fields[2]), Hex.pt(fields[3]),
                    Hex.pt(fields[4]), Hex.pts(fields[5]));
        } else if (fields[0].equals("patch")) {
            if (fields.length != 3) {
                return null;
            }
            item = new Patch(Hex.pt(fields[1]), Hex.pbs(fields[2]));
        } else if (fields[0].equals("crc32")) {
            if (fields.length != 2) {
                return null;
            }
            item = new CheckImageCRC32(Hex.pt(fields[1]));
        } else {
            return null;
        }
        return item;
    }

    static final class ContiguousFile extends ReconstructionItem {
        private final String filename;
        private final int size;
        private final int crc32;
        private final int offset;

        ContiguousFile(String filename, int size, int crc32, int offset) throws ReconstructionFailure.DataInconsistent {
            if (offset < 0) {
                throw new ReconstructionFailure.DataInconsistent("negative offset for " + filename);
            }
            this.filename = filename;
            this.size = size;
            this.crc32 = crc32;
            this.offset = offset;
        }

        @Override
        final void apply(byte[] image, ExternalFileLoader loader) throws ReconstructionFailure.DataInconsistent {
            if (size > image.length || offset > image.length - size) {
                throw new ReconstructionFailure.DataInconsistent("file " + filename + " outside image");
            }
            byte[] data = loader.loadFile(filename, size);
            if (BinaryUtil.crc32(data) != crc32) {
                throw new ReconstructionFailure.DataInconsistent("file " + filename + " checksum mismatch");
            }
            System.arraycopy(data, 0, image, offset, size);
        }

        @Override
        final void writeOut(PrintWriter writer) {
            writer.println("contiguous//" + filename + "//" + Hex.t(size) + "//" + Hex.t(crc32) + "//" + Hex.t(offset));
        }

        @Override
        final boolean isTerminatingItem() {
            return false;
        }
    }

    static final class FileBlocks extends ReconstructionItem {
        private final String filename;
        private final int fileSize;
        private final int crc32;
        private final int blockSize;
        private final int[] blockOffsets;
        private final int lastBlockSize;

        FileBlocks(String filename, int fileSize, int crc32, int blockSize,
                int[] blockOffsets) throws ReconstructionFailure.DataInconsistent {
            if (fileSize <= 0) {
                throw new ReconstructionFailure.DataInconsistent("invalid file size for " + filename);
            }
            if (blockSize <= 0) {
                throw new ReconstructionFailure.DataInconsistent("invalid block size for " + filename);
            }
            this.filename = filename;
            this.fileSize = fileSize;
            this.crc32 = crc32;
            this.blockSize = blockSize;
            this.blockOffsets = blockOffsets;
            lastBlockSize = fileSize - blockSize * (blockOffsets.length - 1);
            if (lastBlockSize <= 0 && lastBlockSize > blockSize) {
                throw new ReconstructionFailure.DataInconsistent("file size does not match block count for " + filename);
            }
        }

        @Override
        final void apply(byte[] image, ExternalFileLoader loader) throws ReconstructionFailure.DataInconsistent {
            byte[] data = loader.loadFile(filename, fileSize);
            if (BinaryUtil.crc32(data) != crc32) {
                throw new ReconstructionFailure.DataInconsistent("file " + filename + " checksum mismatch");
            }
            applyFile(image, data);
        }

        final void applyFile(byte[] image, byte[] fileData) {
            // copy non-last blocks
            int i;
            for (i = 0; i < blockOffsets.length - 1; i++) {
                System.arraycopy(fileData, i * blockSize, image, blockOffsets[i], blockSize);
            }
            // copy last block
            assert i == blockOffsets.length - 1;
            assert lastBlockSize > 0 && lastBlockSize <= blockSize;
            System.arraycopy(fileData, i * blockSize, image, blockOffsets[i], lastBlockSize);
        }

        @Override
        final void writeOut(PrintWriter writer) {
            writer.print("blocks//" + filename + "//" + Hex.t(fileSize) + "//" + Hex.t(crc32) + "//" + Hex.t(blockSize));
            boolean firstp = true;
            for (int offset : blockOffsets) {
                writer.print(firstp ? "//" : ".");
                writer.print(Hex.t(offset));
                firstp = false;
            }
            writer.println();
        }

        @Override
        final boolean isTerminatingItem() {
            return false;
        }
    }

    static final class Patch extends ReconstructionItem {
        private final int offset;
        private final byte[] data;

        Patch(int offset, byte[] data) throws ReconstructionFailure.DataInconsistent {
            if (data.length == 0) {
                throw new ReconstructionFailure.DataInconsistent("empty patch");
            }
            if (offset < 0) {
                throw new ReconstructionFailure.DataInconsistent("negative patch offset");
            }
            this.offset = offset;
            this.data = data;
        }

        @Override
        final void apply(byte[] image, ExternalFileLoader loader) throws ReconstructionFailure.DataInconsistent {
            if (data.length > image.length) {
                throw new ReconstructionFailure.DataInconsistent("patch longer than image");
            }
            if (offset > image.length - data.length) {
                throw new ReconstructionFailure.DataInconsistent("patch outside image");
            }
            System.arraycopy(data, 0, image, offset, data.length);
        }

        @Override
        final void writeOut(PrintWriter writer) {
            writer.println("patch//" + Hex.t(offset) + "//" + Hex.bs(data, '.'));
        }

        @Override
        final boolean isTerminatingItem() {
            return false;
        }
    }

    static final class CheckImageCRC32 extends ReconstructionItem {
        private final int crc32;

        CheckImageCRC32(int crc32) {
            this.crc32 = crc32;
        }

        @Override
        final void writeOut(PrintWriter writer) {
            writer.println("crc32//" + Hex.t(crc32)); // checksum method and value
        }

        @Override
        final void apply(byte[] image, ExternalFileLoader loader)
                throws ReconstructionFailure.DataInconsistent {
            if (BinaryUtil.crc32(image) != crc32) {
                throw new ReconstructionFailure.DataInconsistent("checksum mismatch");
            }
        }

        @Override
        final boolean isTerminatingItem() {
            return true;
        }
    }

    static class Collector {
        private final int size;
        private final ArrayList<ReconstructionItem> items;

        protected Collector(int size) {
            this.size = size;
            items = new ArrayList<ReconstructionItem>();
        }

        final void addItem(ReconstructionItem item) {
            items.add(item);
        }

        final void writeOut(String filename) throws FileNotFoundException {
            PrintWriter writer = new PrintWriter(new FileOutputStream(filename));
            writer.println("rcnimg//" + Hex.t(size)); // type indicator and image size
            for (ReconstructionItem item : items) {
                item.writeOut(writer);
            }
            writer.close();
        }

        final byte[] reconstruct(ExternalFileLoader loader) throws ReconstructionFailure.DataInconsistent {
            byte[] image = new byte[size];
            for (int i = 0; i < image.length; i++) {
                image[i] = 0;
            }
            for (ReconstructionItem item : items) {
                item.apply(image, loader);
            }
            return image;
        }

        static final Collector parse(
                String filename) throws ReconstructionFailure {
            BufferedReader reader;
            try {
                reader = new BufferedReader(new FileReader(filename));
            } catch (FileNotFoundException e) {
                throw new ReconstructionFailure("rcn file " + filename + " not found", e);
            }
            int lineno = 0;
            Collector rcn = null;
            boolean pastChecksum = false;
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    lineno++;
                    if (pastChecksum) {
                        throw new ReconstructionFailure.RcnParseError(filename, lineno);
                    }
                    String[] fields = line.split("//", -1);
                    if (fields.length == 0) {
                        throw new ReconstructionFailure.RcnParseError(filename, lineno);
                    }
                    if (lineno == 1) {
                        if (!fields[0].equals("rcnimg")) {
                            throw new ReconstructionFailure.RcnParseError(filename, lineno);
                        }
                        if (fields.length != 2) {
                            throw new ReconstructionFailure.RcnParseError(filename, lineno);
                        }
                        int imageSize = Hex.pt(fields[1]);
                        if (imageSize <= 0) {
                            throw new ReconstructionFailure.RcnParseError(filename, lineno);
                        }
                        rcn = new Collector(imageSize);
                    } else {
                        ReconstructionItem item = ReconstructionItem.parse(fields);
                        if (item == null) {
                            throw new ReconstructionFailure.RcnParseError(filename, lineno);
                        }
                        assert rcn != null;
                        rcn.addItem(item);
                        pastChecksum = item.isTerminatingItem();
                    }
                }
            } catch (IOException e) {
                throw new ReconstructionFailure("rcn file " + filename + " read error", e);
            } catch (NumberFormatException e) {
                throw new ReconstructionFailure.RcnParseError(filename, lineno, e);
            }
            if (!pastChecksum) {
                throw new ReconstructionFailure.RcnParseError(filename, lineno + 1);
            }
            return rcn;
        }
    }
}
