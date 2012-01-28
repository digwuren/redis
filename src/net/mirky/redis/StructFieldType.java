package net.mirky.redis;

import java.io.PrintStream;

abstract class StructFieldType {
    public StructFieldType() {
        // nothing to be done here
    }

    abstract void show(Cursor cursor, int offset, PrintStream port, Decoding decoding) throws ImageError;

    static final class PaddedString extends StructFieldType {
        private final int size;
        private final byte padding;

        public PaddedString(int size, byte padding) {
            this.size = size;
            this.padding = padding;
        }

        @Override
        final void show(Cursor cursor, int offset, PrintStream port, Decoding decoding) throws ImageError {
            decoding.displayForeignString(cursor.getPaddedBytes(offset, size, padding), port);
        }
    }

    static class SlicedByteField extends StructFieldType {
        private final IntegerSlice[] slices;

        public SlicedByteField(IntegerSlice[] slices) {
            this.slices = slices;
        }

        @Override
        final void show(Cursor cursor, int offset, PrintStream port, Decoding decoding) {
            int fileTypeByte = cursor.getUnsignedByte(offset);
            port.print("[" + Hex.b(fileTypeByte) + "]");
            for (IntegerSlice slice : slices) {
                port.print(slice.decode(fileTypeByte));
            }
        }
    }

    static final StructFieldType D64_SECTOR_CHAIN_START = new StructFieldType() {
        @Override
        final void show(Cursor cursor, int offset, PrintStream port, Decoding decoding) {
            int track = cursor.getUnsignedByte(offset);
            int sector = cursor.getUnsignedByte(offset + 1);
            port.print('[' + Hex.b(track) + ' ' + Hex.b(sector) + "] ");
            if (track == 0 && sector == 0) {
                port.print("n/a");
            } else {
                port.print("track " + track + ", sector " + sector);
            }
        }
    };

    static final StructFieldType UNSIGNED_LEWYDE = new StructFieldType() {
        @Override
        final void show(Cursor cursor, int offset, PrintStream port, Decoding decoding) throws ImageError {
            int value = cursor.getUnsignedLewyde(offset);
            port.print("[" + Hex.w(value) + "] " + value);
        }
    };

    static final StructFieldType UNSIGNED_BYTE = new StructFieldType() {
        @Override
        final void show(Cursor cursor, int offset, PrintStream port, Decoding decoding) {
            int value = cursor.getUnsignedByte(offset);
            port.print("[" + Hex.b(value) + "] " + value);
        }
    };
}