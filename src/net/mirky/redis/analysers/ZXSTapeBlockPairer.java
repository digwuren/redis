package net.mirky.redis.analysers;

import java.util.regex.Pattern;

import net.mirky.redis.Cursor;
import net.mirky.redis.Decoding;
import net.mirky.redis.Format;
import net.mirky.redis.Hex;
import net.mirky.redis.ImageError;
import net.mirky.redis.ReconstructionDataCollector;

/**
 * Each ZX Spectrum tape file consists of two blocks on tape: a header block
 * holding file metadata and a data block holding the content. This class takes
 * care of parsing header blocks, checking checksums, and associating data
 * blocks with their preceding header blocks. The actual format nuances of .tap
 * and .tzx formats will be handled by clients of this class.
 */
public final class ZXSTapeBlockPairer {
    private final Format format;
    private final ReconstructionDataCollector rcn;
    final Cursor cursor;
    HeaderData headerData;
    
    public ZXSTapeBlockPairer(Format format, Cursor.ByteArrayCursor cursor, ReconstructionDataCollector rcn) {
        this.format = format;
        this.cursor = cursor;
        this.rcn = rcn;
        headerData = null;
    }

    final boolean checkBlockChecksum(int blockSize) throws ImageError {
        if (!cursor.probe(blockSize)) {
            throw new ImageError("unexpected end of image");
        }
        byte checksum = 0;
        for (int i = 0; i < blockSize; i++) {
            checksum ^= cursor.getByte(i);
        }
        return checksum == 0;
    }

    // called to process a basic tape block.  Cursor stands before the block's flag byte;
    // blockSize has been parsed; processedUntranscribedBytes holds the number of bytes before
    // cursor that still need to be transcribed (as a patch).
    protected final void processTapeBlock(int blockSize, int processedUntranscribedBytes)
    throws ImageError {
        assert blockSize >= 0; // note that the tape formats support empty blocks
        // If the current block does not have a proper checksum, we discard the preceding header block and treat
        // the new block as an anonymous block.
        boolean checksumOk = checkBlockChecksum(blockSize);
        if (!checksumOk) {
            headerData = null;
        }
        // If the current block as header block size, header block flag, and proper checksum, we'll parse it
        // as a header block.
        if (blockSize == 19 && cursor.getUnsignedByte(0) == 0x00 && checksumOk) {
            if (cursor.getUnsignedByte(1) > 3) {
                throw new ImageError("unknown data block type " + cursor.getUnsignedByte(1));
            }
            headerData = new HeaderData(cursor.getUnsignedByte(1), cursor.getPaddedBytes(2, 10, (byte) 0x20), cursor.getUnsignedLewyde(12), cursor.getUnsignedLewyde(14), cursor.getUnsignedLewyde(16));
            cursor.extractPatch(-processedUntranscribedBytes, processedUntranscribedBytes + blockSize, rcn);
            cursor.advance(blockSize);
        } else {
            // Otherwise, it must be a data block.
            if (blockSize >= 2) {
                if (headerData == null || blockSize != headerData.fileSize + 2 || cursor.getUnsignedByte(0) != 0xFF) {
                    // If we don't have header data or the data block does not match it, generate
                    // a synthetic header data block of file type 4 matching zx4 of ZXLFN spec.
                    headerData = new HeaderData(4, new byte[]{'h', 'e', 'a', 'd', 'e', 'r', 'l', 'e', 's', 's', '-', 'd', 'a', 't', 'a'}, blockSize - 2, cursor.getUnsignedByte(0), cursor.getUnsignedByte(blockSize - 1));
                }
                cursor.extractPatch(- processedUntranscribedBytes, processedUntranscribedBytes + 1, rcn);
                cursor.extractContiguousFile(1, headerData.fileSize, rcn,
                        headerData.constructStorageFilename(format.getDecoding()));
                cursor.extractPatch(1 + headerData.fileSize, 1, rcn);
            } else {
                // The data block is too short to extract even as a ZXLFN zx4 file.  Just transcribe it as a patch.
                cursor.extractPatch(- processedUntranscribedBytes, processedUntranscribedBytes + blockSize, rcn);
            }
            headerData = null;
            // cursor.advance() assumes the delta to be strictly positive and may fail rather than
            // perform nop when passed zero
            if (blockSize > 0) {
                cursor.advance(blockSize);
            }
        }
    }

    public static final Pattern ZXLFN_REGEX = Pattern.compile("^([^,]*)(?:,(\\d+|\\#[\\da-fA-F]+))?(?:,(\\d+|\\#[\\da-fA-F]+))?\\.[zZ][xX]([03])$");

    /**
     * Parses a given string as a ZXLFN parameter (wyde-sized integer, decimal
     * if plain, hex if with '#' prefix).
     * 
     * @param param
     *            ZXLFN parameter to be parsed
     * @return parsed parameter if successful, -1 if parsing failed and a
     *         default value should be used
     */
    public static final int parseZxlfnParam(String param) {
        if (param == null || param.length() == 0) {
            return -1;
        }
        int value;
        try {
            if (param.charAt(0) == '#') {
                value = Integer.parseInt(param.substring(1), 16);
            } else {
                value = Integer.parseInt(param, 10);
            }
        } catch (NumberFormatException e) {
            return -1;
        }
        // The values are supposed to be unsigned wydes. If parsed successfully
        // but outside range, we'll reject it and return the default value. This
        // way, we can safely use out-of-range values as out-of-band codes.
        if (value >= 0 && value <= 0xFFFF) {
            return value;
        } else {
            return -1;
        }
    }

    protected static final class HeaderData {
        final int fileType;
        final byte[] filename;
        final int fileSize;
        final int param1;
        final int param2;

        HeaderData(int fileType, byte[] filename, int fileSize, int param1,
                int param2) {
            this.fileType = fileType;
            this.filename = filename;
            this.fileSize = fileSize;
            this.param1 = param1;
            this.param2 = param2;
        }

        final String constructStorageFilename(Decoding decoding) {
            StringBuilder sb = new StringBuilder();
            decoding.decodeFilename(filename, '#', sb);
            // If the original file is nameless, we'll use the first parameter in filename even if it
            // has the default value.  This way, the generated filename won't start with period.
            if (filename.length == 0 || param1 != defaultParam1() || param2 != defaultParam2()) {
                sb.append(',');
                if (fileType == 3) {
                    // in code file names, we'll display param1 in hex because it is a load address
                    sb.append('#');
                    sb.append(Hex.w(param1));
                } else {
                    sb.append(param1);
                }
                if (param2 != defaultParam2()) {
                    sb.append(',');
                    sb.append(param2);
                }
            }
            sb.append(".zx" + fileType);
            return sb.toString();
        }

        private final int defaultParam1() {
            switch (fileType) {
                case 0: return 0;
                case 3: return 0x8000;
                default: return -1;
            }
        }

        private final int defaultParam2() {
            switch (fileType) {
                case 0: return fileSize;
                case 3: return 0x8000;
                default: return -1;
            }
        }
    }
}
