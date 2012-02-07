package net.mirky.redis;

public final class Cursor {
    private final byte[] data;
    private int pos;

    public Cursor(byte[] data, int pos) {
        assert pos >= 0 && pos <= data.length;
        this.data = data;
        this.pos = pos;
    }

    public final void seek(int newPos) throws ImageError {
        assert newPos >= 0;
        if (newPos > data.length) {
            throw new ImageError("cursor outside image");
        }
        pos = newPos;
    }

    // Note that negative advances are permitted.
    public final void advance(int delta) throws ImageError {
        seek(pos + delta);
    }

    public final int tell() {
        return pos;
    }

    // Checks whether there are at least a specified number of bytes available after the cursor.
    public final boolean probe(int amount) {
        assert amount > 0;
        return amount <= data.length && pos <= data.length - amount;
    }

    public final boolean atEnd() {
        return pos == data.length;
    }

    public final byte getByte(int offset) throws ImageError {
        int addr = pos + offset;
        if (addr < 0 || addr > data.length - 1) {
            throw new ImageError("cursor outside image");
        }
        return data[addr];
    }
    
    public final int getUnsignedByte(int offset) throws ImageError {
        return getByte(offset) & 0xFF;
    }
    
    public final int passUnsignedByte() throws ImageError {
        int value = getUnsignedByte(0);
        advance(1);
        return value;
    }

    public final int getUnsignedLewyde(int offset) throws ImageError {
        int addr = pos + offset;
        assert addr >= 0;
        if (addr > data.length - 2) {
            throw new ImageError("cursor outside image");
        }
        return (data[addr] & 0xFF) + (data[addr + 1] & 0xFF) * 256;
    }

    public final int passUnsignedLewyde() throws ImageError {
        int value = getUnsignedLewyde(0);
        advance(2);
        return value;
    }

    public final int getUnsignedBewyde(int offset) throws ImageError {
        int addr = pos + offset;
        assert addr >= 0;
        if (addr > data.length - 2) {
            throw new ImageError("cursor outside image");
        }
        return (data[addr + 1] & 0xFF) + (data[addr] & 0xFF) * 256;
    }

    public final int passUnsignedBewyde() throws ImageError {
        int value = getUnsignedBewyde(0);
        advance(2);
        return value;
    }

    public final int getUnsignedLetribyte(int offset) throws ImageError {
        int addr = pos + offset;
        assert addr >= 0;
        if (addr > data.length - 3) {
            throw new ImageError("cursor outside image");
        }
        return (data[addr] & 0xFF) + (data[addr + 1] & 0xFF) * 0x100 + (data[addr + 2] & 0xFF) * 0x10000;
    }

    public final byte[] getPaddedBytes(int offset, int maxLength, byte padding) throws ImageError {
        assert maxLength > 0;
        int end = offset + maxLength;
        while (end > offset && getByte(end - 1) == padding) {
            end--;
        }
        return getBytes(offset, end - offset);
    }

    public final byte[] passPaddedBytes(int size, byte padding) throws ImageError {
        byte[] bytes = getPaddedBytes(0, size, padding);
        advance(size);
        return bytes;
    }

    public final byte[] getBytes(int offset, int count) throws ImageError {
        byte[] buffer = new byte[count];
        getBytes(offset, count, buffer, 0);
        return buffer;
    }

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

    public final void extractContiguousFile(int offset, int size,
            ReconstructionDataCollector rcn, String filename) throws ImageError {
        rcn.contiguousFile(filename, getBytes(offset, size), null, (pos + offset));
    }

    public final void extractContiguousIfNotBlank(int offset, int size,
            ReconstructionDataCollector rcn, String filename) throws ImageError {
        byte[] fileData = getBytes(offset, size);
        if (!BinaryUtil.byteArrayBlank(fileData)) {
            rcn.contiguousFile(filename, fileData, null, (pos + offset));
        }
    }

    public final void extractPatch(int offset, int size, ReconstructionDataCollector rcn) throws ImageError {
        try {
            rcn.patch(pos + offset, getBytes(offset, size));
        } catch (ReconstructionFailure.DataInconsistent e) {
            throw new RuntimeException("bug detected", e);
        }
    }

    public final boolean hasMagic(int offset, byte... etalon) throws ImageError {
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

    public final void walkAndExtractPatch(int size, ReconstructionDataCollector rcn) throws ImageError {
        assert size > 0;
        if (!probe(size)) {
            throw new ImageError("truncated image");
        }
        extractPatch(0, size, rcn);
        advance(size);
    }

    public final Cursor subcursor(int offset) {
        return new Cursor(data, pos + offset);
    }

    @Override
    public final String toString() {
        return "@" + pos + ":" + data.length;
    }
}
