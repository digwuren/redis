package net.mirky.redis;

public abstract class Cursor {
    public abstract void seek(int newOffset) throws ImageError;
    public abstract void advance(int delta) throws ImageError;
    public abstract boolean regionBlank(int offset, int size);
    public abstract void walkAndExtractPatch(int size, ReconstructionDataCollector rcn)
            throws ImageError;
    public abstract boolean hasMagic(int offset, byte... etalon);
    public abstract void extractPatch(int offset, int size,
            ReconstructionDataCollector rcn) throws ImageError;
    public abstract void extractContiguousIfNotBlank(int offset,
            int size, ReconstructionDataCollector rcn, String filename) throws ImageError;
    public abstract void extractContiguousFile(int offset, int size,
            ReconstructionDataCollector rcn, String filename) throws ImageError;
    public abstract void getBytes(int offset, int count, byte[] buffer,
            int bufferOffset) throws ImageError;
    public abstract byte[] getBytes(int offset, int count)
            throws ImageError;
    public abstract byte[] getPaddedBytes(int offset, int maxLength,
            byte padding) throws ImageError;
    public abstract int getUnsignedLetribyte(int offset)
            throws ImageError;
    public abstract int getUnsignedBewyde(int offset) throws ImageError;
    public abstract int getUnsignedLewyde(int offset) throws ImageError;
    public abstract int getUnsignedByte(int offset);
    public abstract byte getByte(int offset);
    public abstract boolean atEnd();
    public abstract boolean probe(int amount);
    public abstract int tell();
    public abstract Cursor subcursor(int offset);

    public static final class ByteArrayCursor extends Cursor {
        private final byte[] data;
        private int pos;

        public ByteArrayCursor(byte[] data, int pos) {
            assert pos >= 0 && pos <= data.length;
            this.data = data;
            this.pos = pos;
        }

        @Override
        public final void seek(int newOffset) throws ImageError {
            assert newOffset >= 0;
            if (newOffset > data.length) {
                throw new ImageError("cursor outside image");
            }
            pos = newOffset;
        }

        @Override
        public final void advance(int delta) throws ImageError {
            assert delta > 0;
            seek(pos + delta);
        }

        @Override
        public final int tell() {
            return pos;
        }

        // Checks whether there are at least a specified number of bytes available after the cursor.
        @Override
        public final boolean probe(int amount) {
            assert amount > 0;
            return amount <= data.length && pos <= data.length - amount;
        }

        @Override
        public final boolean atEnd() {
            return pos == data.length;
        }

        @Override
        public final byte getByte(int offset) {
            int addr = pos + offset;
            assert addr >= 0 && addr < data.length;
            return data[addr];
        }

        @Override
        public final int getUnsignedByte(int offset) {
            return getByte(offset) & 0xFF;
        }

        @Override
        public final int getUnsignedLewyde(int offset) throws ImageError {
            int addr = pos + offset;
            assert addr >= 0;
            if (addr > data.length - 2) {
                throw new ImageError("cursor outside image");
            }
            return (data[addr] & 0xFF) + (data[addr + 1] & 0xFF) * 256;
        }

        @Override
        public final int getUnsignedBewyde(int offset) throws ImageError {
            int addr = pos + offset;
            assert addr >= 0;
            if (addr > data.length - 2) {
                throw new ImageError("cursor outside image");
            }
            return (data[addr + 1] & 0xFF) + (data[addr] & 0xFF) * 256;
        }

        @Override
        public final int getUnsignedLetribyte(int offset) throws ImageError {
            int addr = pos + offset;
            assert addr >= 0;
            if (addr > data.length - 3) {
                throw new ImageError("cursor outside image");
            }
            return (data[addr] & 0xFF) + (data[addr + 1] & 0xFF) * 0x100 + (data[addr + 2] & 0xFF) * 0x10000;
        }

        @Override
        public final byte[] getPaddedBytes(int offset, int maxLength, byte padding) throws ImageError {
            assert maxLength > 0;
            int end = offset + maxLength;
            while (end > offset && getByte(end - 1) == padding) {
                end--;
            }
            return getBytes(offset, end - offset);
        }

        @Override
        public byte[] getBytes(int offset, int count) throws ImageError {
            byte[] buffer = new byte[count];
            getBytes(offset, count, buffer, 0);
            return buffer;
        }

        @Override
        public final void getBytes(int offset, int count, byte[] buffer, int bufferOffset) throws ImageError {
            assert pos + offset >= 0;
            assert count > 0;
            assert bufferOffset >= 0;
            assert bufferOffset < buffer.length;
            assert count <= buffer.length;
            assert bufferOffset <= buffer.length - count;
            if (!(count <= data.length && pos + offset <= data.length - count)) {
                throw new ImageError("truncated image");
            }
            System.arraycopy(data, pos + offset, buffer, bufferOffset, count);
        }

        @Override
        public final void extractContiguousFile(int offset, int size,
                ReconstructionDataCollector rcn, String filename) throws ImageError {
            rcn.contiguousFile(filename, getBytes(offset, size), null, (pos + offset));
        }

        @Override
        public final void extractContiguousIfNotBlank(int offset, int size,
                ReconstructionDataCollector rcn, String filename) throws ImageError {
            byte[] fileData = getBytes(offset, size);
            if (!BinaryUtil.byteArrayBlank(fileData)) {
                rcn.contiguousFile(filename, fileData, null, (pos + offset));
            }
        }

        @Override
        public final void extractPatch(int offset, int size, ReconstructionDataCollector rcn) throws ImageError {
            try {
                rcn.patch(pos + offset, getBytes(offset, size));
            } catch (ReconstructionFailure.DataInconsistent e) {
                throw new RuntimeException("bug detected", e);
            }
        }

        @Override
        public final boolean hasMagic(int offset, byte... etalon) {
            if (data.length < etalon.length && offset > data.length - etalon.length) {
                return false;
            }
            for (int i = 0; i < etalon.length; i++) {
                if (getByte(offset + i) != etalon[i]) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public final void walkAndExtractPatch(int size, ReconstructionDataCollector rcn) throws ImageError {
            assert size > 0;
            if (!probe(size)) {
                throw new ImageError("truncated image");
            }
            extractPatch(0, size, rcn);
            advance(size);
        }

        @Override
        public final boolean regionBlank(int offset, int size) {
            for (int i = 0; i < size; i++) {
                if (getByte(offset + i) != 0) {
                    return false;
                }
            }
            return true;
        }
        
        @Override
        public final ByteArrayCursor subcursor(int offset) {
            return new ByteArrayCursor(data, pos + offset);
        }
    }
}
