package net.mirky.redis;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class BinaryUtil {
    private BinaryUtil() {
        // not a real constructor
    }
    
    static final byte[] loadFile(String filename) throws ImageError {
        FileInputStream stream = null;
        try {
            stream = new FileInputStream(filename);
        } catch (FileNotFoundException e) {
            throw new ImageError("file not found");
        }
        assert stream != null;
        long imageSize = 0;
        try {
            imageSize = stream.getChannel().size();
        } catch (IOException e) {
            throw new ImageError("file size query failed");
        }
        if (imageSize > Integer.MAX_VALUE) {
            throw new ImageError("image too big");
        }
        byte[] data = new byte[(int) imageSize];
        try {
            if (stream.read(data) != data.length || stream.read() != -1) {
                throw new ImageError("image size changed during processing");
            }
            stream.close();
        } catch (IOException e) {
            throw new ImageError("read failed");
        }
        return data;
    }

    public static final boolean fileSizeMatchesBlockCount(int filesize, int blockCount,
            int blockSize) {
        return filesize > (blockCount - 1) * blockSize && filesize <= blockCount * 254;
    }

    /**
     * Check whether a given byte array contains only zeroes.
     */
    public static final boolean byteArrayBlank(byte[] array) {
        return byteArraySliceBlank(array, 0, array.length);
    }

    /**
     * Check whether a byte array slice contains only zeroes.
     * 
     * @param array
     *            base array
     * @param start
     *            first offset of the slice to be scanned
     * @param length
     *            length of the slice to be scanned
     */
    public static final boolean byteArraySliceBlank(final byte[] array, final int start, final int length) {
        for (int i = start; i < start + length; i++) {
            if (array[i] != 0) {
                return false;
            }
        }
        return true;
    }

    public static final int crc32(byte[] data) {
        int r = ~0;
        for (byte b : data) {
            r ^= b & 0xFF;
            for (int i = 0; i < 8; i++) {
                boolean lowBit = (r & 1) != 0;
                r >>>= 1;
                if (lowBit) {
                    r ^= 0xEDB88320;
                }
            }
        }
        return ~r;
    }

    static final String md5(byte[] data) throws RuntimeException {
        try {
            return Hex.bs(MessageDigest.getInstance("MD5").digest(data));
        } catch (NoSuchAlgorithmException e1) {
            throw new RuntimeException("no MD5 support???");
        }
    }

    static final String sha1(byte[] data) throws RuntimeException {
        try {
            return Hex.bs(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e1) {
            throw new RuntimeException("no SHA-1 support???");
        }
    }
}
