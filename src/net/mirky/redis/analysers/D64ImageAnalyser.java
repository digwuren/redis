package net.mirky.redis.analysers;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import net.mirky.redis.Analyser;
import net.mirky.redis.BinaryUtil;
import net.mirky.redis.Cursor;
import net.mirky.redis.Decoding;
import net.mirky.redis.DisplayUtil;
import net.mirky.redis.Format;
import net.mirky.redis.Format.OptionError;
import net.mirky.redis.Hex;
import net.mirky.redis.ImageError;
import net.mirky.redis.ReconstructionDataCollector;
import net.mirky.redis.ReconstructionFailure;
import net.mirky.redis.ResourceManager;

@Format.Options(".d64/decoding:decoding=petscii")
public final class D64ImageAnalyser extends Analyser.Container {
    @Override
    protected ReconstructionDataCollector extractFiles(Format format, byte[] fileData) throws ImageError {
        return new D64ImageExtractor(format, fileData).rcn;
    }

    @Override
    public final void hexdump(Format format, byte[] data, PrintStream port) {
        int trackCount;
        switch (data.length) {
            case 683 * 256:
            case 683 * 257:
                trackCount = 35;
                break;
            case 768 * 256:
            case 768 * 257:
                trackCount = 40;
                break;
            case 802 * 256:
            case 802 * 257:
                trackCount = 42;
                break;
            default:
                trackCount = 0;
                port.println(DisplayUtil.brightRed("! unknown d64 image size, unable to provide sector cošrdinates"));
                super.hexdump(format, data, port);
                return;
        }
        D64ImageExtractor.D64ImageGeometry geometry = new D64ImageExtractor.D64ImageGeometry(trackCount);
        int blankSectorCount = 0;
        for (int diskSector = 0; diskSector < geometry.getDiskSectorCount(); diskSector++) {
            int offset = diskSector << 8;
            if (BinaryUtil.byteArraySliceBlank(data, offset, 256)) {
                blankSectorCount++;
            } else {
                if (blankSectorCount != 0) {
                    port.println("(" + blankSectorCount + " blank sector(s) not shown)");
                    blankSectorCount = 0;
                }
                byte[] sectorData = new byte[256];
                System.arraycopy(data, offset, sectorData, 0, 256);
                int track = D64ImageExtractor.D64ImageGeometry.diskSectorTrack(diskSector);
                int sector = D64ImageExtractor.D64ImageGeometry.diskSectorSector(diskSector);
                port.println("* track " + track + " sector " + sector + " (seq. no: " + diskSector + ")");
                Hex.dump(sectorData, format.getOrigin() + offset, format.getDecoding(), port);
            }
        }
        if (blankSectorCount != 0) {
            port.println("(" + blankSectorCount + " blank sector(s) not shown)");
        }
        int errorDataOffset = geometry.getDiskSectorCount() << 8;
        if (errorDataOffset != data.length) {
            byte[] errorData = new byte[data.length - errorDataOffset];
            System.arraycopy(data, errorDataOffset, errorData, 0, errorData.length);
            port.println("* sector error data");
            Hex.dump(errorData, format.getOrigin() + errorDataOffset, format.getDecoding(), port);
        }
    }

    private static final class D64ImageExtractor {
        private final Format format;
        private final D64ImageGeometry geometry;
        private final byte[] data;
        final ReconstructionDataCollector rcn;
        private final boolean[] diskSectorTraversed;
        private final Cursor errorDataCursor;

        private int[] freeSectorBitmaps;
        private D64ImageExtractor.DirEntry[] dirEntries;

        D64ImageExtractor(Format format, byte[] data) throws ImageError {
            this.format = format;
            int trackCount;
            int errorDataOffset;
            switch (data.length) {
                case 683 * 256:
                    trackCount = 35;
                    errorDataOffset = -1;
                    break;
                case 683 * 257:
                    trackCount = 35;
                    errorDataOffset = 174848;
                    break;
                case 768 * 256:
                    trackCount = 40;
                    errorDataOffset = -1;
                    break;
                case 768 * 257:
                    trackCount = 40;
                    errorDataOffset = 196608;
                    break;
                case 802 * 256:
                    trackCount = 42;
                    errorDataOffset = -1;
                    break;
                case 802 * 257:
                    trackCount = 42;
                    errorDataOffset = 205312;
                    break;
                default:
                    throw new ImageError("unknown d64 image size: " + data.length);
            }
            geometry = new D64ImageExtractor.D64ImageGeometry(trackCount);
            this.data = data;
            rcn = new ReconstructionDataCollector(data.length);
            diskSectorTraversed = new boolean[geometry.getDiskSectorCount()];
            for (int i = 0; i < diskSectorTraversed.length; i++) {
                diskSectorTraversed[i] = false;
            }
            if (errorDataOffset != -1) {
                errorDataCursor = new Cursor.ByteArrayCursor(data, errorDataOffset);
            } else {
                errorDataCursor = null;
            }
            parse();
        }

        private final void parse() throws ImageError {
            // Defensively, we calculate original checksum first even through we attach it last.
            int crc32 = BinaryUtil.crc32(data);
            if (errorDataCursor != null) {
                errorDataCursor.extractContiguousIfNotBlank(0, geometry.getDiskSectorCount(), this.rcn, "secterr.bin");
            }
            extractHeaderSector();
            parseDirectory();
            extractFiles();
            extractUntraversedNonblankSectors();
            rcn.checksum(crc32);
        }

        private final void extractHeaderSector() throws ImageError {
            Cursor cursor = new Cursor.ByteArrayCursor(data, 0);
            int headerDiskSectorNumber = D64ImageExtractor.D64ImageGeometry.calcDiskSectorNumber(18, 0);
            D64ImageExtractor.D64ImageGeometry.seekDiskSector(cursor, headerDiskSectorNumber);
            checkDiskSectorGoodness(headerDiskSectorNumber);
            checkAndMarkDiskSectorTraversed(headerDiskSectorNumber);
            byte[] header = cursor.getBytes(0, 256);
            Format headerFormat = Format.getGuaranteedFormat("d64-disk-header");
            headerFormat = format.imposeDecodingIfExplicit(headerFormat);
            rcn.contiguousFile("header.sct", header, headerFormat, headerDiskSectorNumber * 256);
            // The first two bytes of header link to the next sector -- start of the directory.
            // This must be T18S1.
            if (header[0x00] != 18 || header[0x01] != 1) {
                throw new ImageError("header inconsistency");
            }
            if (header[0x02] != 0x41) {
                throw new ImageError("unknown DOS version");
            }
            if (header[0x03] != 0) {
                throw new ImageError("header inconsistency");
            }
            if (header[0xA0] != (byte) 0xA0 || header[0xA1] != (byte) 0xA0
                                || header[0xA4] != (byte) 0xA0) {
                throw new ImageError("header inconsistency");
            }
            if (header[0xA5] != 0x32 || header[0xA6] != 0x41) {
                throw new ImageError("unknown DOS type");
            }
            if (header[0xA7] != (byte) 0xA0 || header[0xA8] != (byte) 0xA0
                                || header[0xA9] != (byte) 0xA0 || header[0xAA] != (byte) 0xA0) {
                throw new ImageError("header inconsistency");
            }
            for (int i = 0xAB; i <= 0xFF; i++) {
                if (header[i] != 0) {
                    throw new ImageError("header inconsistency");
                }
            }
            prepareFreeSectorBitmaps(cursor);
            // check that header sector is properly marked used
            checkSectorFreeFlag(18, 0);
        }

        private final void prepareFreeSectorBitmaps(Cursor cursor)
                throws ImageError {
            freeSectorBitmaps = new int[36];
            for (int track = 1; track <= 35; track++) {
                int entryOffset = track * 4;
                int bitmap = cursor.getUnsignedLetribyte(entryOffset + 1);
                if ((bitmap >>> D64ImageExtractor.D64ImageGeometry.trackSectorCount(track)) != 0) {
                    throw new ImageError("track " + track + " has non-existent sector(s) marked free");
                }
                if (cursor.getUnsignedByte(entryOffset) != countBits(bitmap)) {
                    throw new ImageError("track " + track + " free sector count mismatch");
                }
                freeSectorBitmaps[track] = bitmap;
            }
        }

        private final int countBits(int bitmap) {
            int count = 0;
            for (int sr = bitmap; sr != 0; sr >>>= 1) {
                count += sr & 1;
            }
            return count;
        }

        private final void parseDirectory() throws ImageError {
            byte[] directoryData = followSectorChain("directory.sct", 18, 1, true);
            int direntCount = directoryData.length / 32;
            dirEntries = new D64ImageExtractor.DirEntry[direntCount];
            Set<String> seenFilenames = new HashSet<String>();
            Cursor cursor = new Cursor.ByteArrayCursor(directoryData, 0);
            for (int i = 0; i < direntCount; i++) {
                parseDirEntry(cursor, i, seenFilenames);
                cursor.advance(32);
            }
        }

        private final void parseDirEntry(Cursor cursor, int entryNumber,
                Set<String> seenFilenames) throws ImageError {
            int fileType = cursor.getUnsignedByte(0x02);
            if (fileType == 0) {
                dirEntries[entryNumber] = null;
            } else {
                if (fileType != 0x81 && fileType != 0x82) {
                    throw new ImageError("directory entry #" + entryNumber
                                                + " has unknown file type 0x" + Hex.b(fileType));
                }
                int startTrack = cursor.getUnsignedByte(0x03);
                int startSector = cursor.getUnsignedByte(0x04);
                if (startTrack == 0 || startTrack > geometry.trackCount
                                        || startSector >= D64ImageExtractor.D64ImageGeometry.trackSectorCount(startTrack)) {
                    throw new ImageError("directory entry #" + entryNumber
                                                + " has invalid start sector "
                                                + D64ImageExtractor.D64ImageGeometry.sectorName(startTrack, startSector));
                }
                byte[] name = cursor.getPaddedBytes(0x05, 0x10, (byte) 0xA0);
                StringBuilder sb = new StringBuilder();
                format.getDecoding().decodeFilename(name, '%', sb);
                switch (fileType & 0x0F) {
                    case 1: sb.append(".seq"); break;
                    case 2: sb.append(".prg"); break;
                    default: throw new ImageError("directory entry #" + entryNumber
                                                         + " has invalid file type byte" + Hex.b(fileType));
                }
                int sectorCount = cursor.getUnsignedLewyde(0x1E);
                if (sectorCount == 0) {
                    throw new ImageError("directory entry #" + entryNumber
                                                + " is marked to hold zero sectors");
                }
                String entryFilename = sb.toString();
                if (seenFilenames.contains(entryFilename)) {
                    throw new ImageError("duplicate filename " + entryFilename);
                }
                seenFilenames.add(entryFilename);
                dirEntries[entryNumber] = new D64ImageExtractor.DirEntry(entryFilename,
                                        startTrack, startSector, sectorCount);
            }
        }

        private final void extractFiles() throws ImageError {
            for (int i = 0; i < dirEntries.length; i++) {
                if (dirEntries[i] != null) {
                    byte[] filedata = followSectorChain(dirEntries[i].name, dirEntries[i].startTrack, dirEntries[i].startSector, false);
                    if (!BinaryUtil.fileSizeMatchesBlockCount(filedata.length, dirEntries[i].sectorCount, 254)) {
                        throw new ImageError("file block chain length does not match declared length for " + dirEntries[i].name);
                    }
                }
            }
        }

        private final void extractUntraversedNonblankSectors() throws ImageError {
            Cursor cursor = new Cursor.ByteArrayCursor(data, 0);
            for (int diskSector = 0; diskSector < geometry.getDiskSectorCount(); diskSector++) {
                if (!diskSectorTraversed[diskSector]) {
                    D64ImageExtractor.D64ImageGeometry.seekDiskSector(cursor, diskSector);
                    cursor.extractContiguousIfNotBlank(0, 256, rcn,
                            D64ImageExtractor.D64ImageGeometry.diskSectorName(diskSector)
                            + (getSectorErrorByte(diskSector) != 0 ? "BAD" : "")
                            + ".sct");
                }
            }
        }

        private final byte[] followSectorChain(String filename, int startTrack, int startSector, boolean dirMode) throws ImageError {
            ArrayList<Integer> diskSectorNumbers = new ArrayList<Integer>();
            int track = startTrack;
            int sector = startSector;
            Cursor cursor = new Cursor.ByteArrayCursor(data, 0);
            do {
                int diskSector = D64ImageExtractor.D64ImageGeometry.calcDiskSectorNumber(track, sector);
                diskSectorNumbers.add(new Integer(diskSector));
                checkDiskSectorGoodness(diskSector);
                checkAndMarkDiskSectorTraversed(diskSector);
                checkSectorFreeFlag(track, sector);
                D64ImageExtractor.D64ImageGeometry.seekDiskSector(cursor, diskSector);
                // Note that diskSector stays the same for a little bit more so we can point to
                // the faulty sector properly in the error messages
                track = cursor.getUnsignedByte(0x00);
                sector = cursor.getUnsignedByte(0x01);
                if (track != 0) {
                    if (track > geometry.trackCount || sector >= D64ImageExtractor.D64ImageGeometry.trackSectorCount(track)) {
                        throw new ImageError(D64ImageExtractor.D64ImageGeometry.diskSectorName(diskSector) + " has invalid next sector link");
                    }
                } else {
                    // Last sector's next sector link holds the index of last byte in the last block.
                    if (dirMode) {
                        if (sector != 0xFF) {
                            throw new ImageError(D64ImageExtractor.D64ImageGeometry.diskSectorName(diskSector) + " is marked as an incomplete directory sector");
                        }
                    } else {
                        if (sector < 2) {
                            throw new ImageError(D64ImageExtractor.D64ImageGeometry.diskSectorName(diskSector) + " has invalid slack indicator");
                        }
                    }
                }
            } while (track != 0);
            int[] blockOffsets = new int[diskSectorNumbers.size()];
            int blockSize = dirMode ? 256 : 254;
            byte[] dataInChain;
            if (dirMode) {
                dataInChain = new byte[diskSectorNumbers.size() * blockSize];
                for (int i = 0; i < blockOffsets.length; i++) {
                    int dsn = diskSectorNumbers.get(i).intValue();
                    blockOffsets[i] = dsn * 256;
                    cursor.seek(dsn * 256);
                    cursor.getBytes(0, 256, dataInChain, i * blockSize);
                }
                Format dirFormat = Format.getGuaranteedFormat("array");
                try {
                    dirFormat = dirFormat.parseChange("/struct=d64-dirent-union");
                } catch (OptionError e) {
                    throw new RuntimeException("bug detected", e);
                }
                try {
                    dirFormat = format.imposeDecodingIfExplicit(dirFormat, Decoding.MANAGER.get("petscii"));
                } catch (ResourceManager.ResolutionError e) {
                    throw new RuntimeException("bug detected", e);
                }
                rcn.fileBlocks(filename, dataInChain, dirFormat, blockSize, blockOffsets);
            } else {
                try {
                    int lastBlockSize = sector - 1;
                    dataInChain = new byte[(diskSectorNumbers.size() - 1) * blockSize + lastBlockSize];
                    // copy non-last blocks
                    int i;
                    for (i = 0; i < blockOffsets.length - 1; i++) {
                        int dsn = diskSectorNumbers.get(i).intValue();
                        cursor.seek(dsn * 256);
                        rcn.patch(dsn * 256, cursor.getBytes(0, 2));
                        blockOffsets[i] = dsn * 256 + 2;
                        cursor.getBytes(2, 254, dataInChain, i * blockSize);
                    }
                    // copy last block
                    assert i == blockOffsets.length - 1;
                    int lastDsn = diskSectorNumbers.get(i).intValue();
                    cursor.seek(lastDsn * 256);
                    rcn.patch(lastDsn * 256, cursor.getBytes(0, 2));
                    blockOffsets[i] = lastDsn * 256 + 2;
                    cursor.getBytes(2, lastBlockSize, dataInChain, i * blockSize);
                    // extract slack
                    byte[] slack = cursor.getBytes(2 + lastBlockSize, 254 - lastBlockSize);
                    if (!BinaryUtil.byteArrayBlank(slack)) {
                        rcn.contiguousFile((filename + ".slack"), slack, null, (lastDsn * 256 + 2 + lastBlockSize));
                    }
                } catch (ReconstructionFailure.DataInconsistent e) {
                    throw new RuntimeException("bug detected");
                }
                rcn.fileBlocks(filename, dataInChain, null, blockSize, blockOffsets);
            }
            return dataInChain;
        }

        public void checkAndMarkDiskSectorTraversed(int diskSector)
                throws ImageError {
            if (diskSectorTraversed[diskSector]) {
                throw new ImageError("sector " + D64ImageExtractor.D64ImageGeometry.diskSectorName(diskSector) + " crosslinked");
            }
            diskSectorTraversed[diskSector] = true;
        }

        private final void checkSectorFreeFlag(int track, int sector) throws ImageError {
            assert track >= 1 && track <= 42;
            assert sector >= 0 && sector <= D64ImageExtractor.D64ImageGeometry.trackSectorCount(track);
            if (track <= 35) {
                if ((freeSectorBitmaps[track] & (1 << sector)) != 0) {
                    throw new ImageError("used sector " + D64ImageExtractor.D64ImageGeometry.sectorName(track, sector) + " is marked free");
                }
            }
        }

        private final void checkDiskSectorGoodness(int diskSector) throws ImageError {
            if (getSectorErrorByte(diskSector) != 0x00) {
                throw new ImageError("used sector " + D64ImageExtractor.D64ImageGeometry.diskSectorName(diskSector) + " is marked bad");
            }
        }

        final int getSectorErrorByte(int diskSector) {
            assert diskSector >= 0 && diskSector < geometry.getDiskSectorCount();
            if (errorDataCursor != null) {
                return errorDataCursor.getUnsignedByte(diskSector);
            } else {
                return 0;
            }
        }

        static final class DirEntry {
            final String name;
            final int startTrack, startSector;
            final int sectorCount;
        
            DirEntry(String name, int startTrack, int startSector,
                    int sectorCount) {
                this.name = name;
                this.startTrack = startTrack;
                this.startSector = startSector;
                this.sectorCount = sectorCount;
            }
        }

        static final class D64ImageGeometry {
            final int trackCount;
        
            D64ImageGeometry(int trackCount) {
                this.trackCount = trackCount;
            }
        
            private static final int trackStartDiskSector[] = new int[44];
        
            static {
                trackStartDiskSector[0] = -1; // there is no track 0
                int diskSector = 0;
                for (int track = 1; track <= 42; track++) {
                    trackStartDiskSector[track] = diskSector;
                    diskSector += D64ImageGeometry.trackSectorCount(track);
                }
                trackStartDiskSector[43] = diskSector;
                assert diskSector == 802;
            }
        
            static final int trackSectorCount(int track) {
                assert track >= 1 && track <= 42;
                if (track <= 17) {
                    return 21;
                } else if (track <= 24) {
                    return 19;
                } else if (track <= 30) {
                    return 18;
                } else {
                    return 17;
                }
            }
        
            static final int calcDiskSectorNumber(int track, int sector) {
                assert track >= 1 && track <= 42;
                assert sector >= 0 && sector < trackSectorCount(track);
                return trackStartDiskSector[track] + sector;
            }
        
            final int getDiskSectorCount() {
                return D64ImageGeometry.trackStartDiskSector[trackCount + 1];
            }
        
            static final void seekDiskSector(Cursor cursor, int diskSector) throws ImageError {
                cursor.seek(diskSector * 256);
            }
        
            static final int diskSectorTrack(int diskSector) {
                assert diskSector >= 0 && diskSector < trackStartDiskSector[trackStartDiskSector.length - 1];
                for (int track = 1;; track++) {
                    if (trackStartDiskSector[track + 1] > diskSector) {
                        return track;
                    }
                }
            }
            
            static final int diskSectorSector(int diskSector) {
                return diskSector - trackStartDiskSector[diskSectorTrack(diskSector)];
            }

            static final String diskSectorName(int diskSector) {
                assert diskSector >= 0 && diskSector < trackStartDiskSector[trackStartDiskSector.length - 1];
                int track = diskSectorTrack(diskSector);
                int sector = diskSector - trackStartDiskSector[track];
                assert calcDiskSectorNumber(track, sector) == diskSector;
                return sectorName(track, sector);
            }
        
            static final String sectorName(int track, int sector) {
                return String.format("T%02dS%02d", new Integer(track), new Integer(sector));
            }
        }
    }
}