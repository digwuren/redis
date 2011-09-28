package net.mirky.redis;

import java.io.PrintStream;

abstract class StructFieldType {
    public StructFieldType() {
        // nothing to be done here
    }

    abstract void show(Cursor cursor, int offset, String name, PrintStream port, Decoding decoding) throws ImageError;

    protected static final void displayFieldPrefix(Cursor cursor, int offset, String fieldName, PrintStream port) {
        port.print(Hex.t(cursor.tell() + offset) + ": " + fieldName + ": ");
    }

    static final class PaddedString extends StructFieldType {
        private final int size;
        private final byte padding;

        public PaddedString(int size, byte padding) {
            this.size = size;
            this.padding = padding;
        }

        @Override
        final void show(Cursor cursor, int offset, String name, PrintStream port, Decoding decoding) throws ImageError {
            displayFieldPrefix(cursor, offset, name, port);
            decoding.displayForeignString(cursor.getPaddedBytes(offset, size, padding), port);
            port.println();
        }
    }

    static class SlicedByteField extends StructFieldType {
        private final IntegerSliceType[] slices;

        public SlicedByteField(IntegerSliceType... slices) {
            this.slices = slices;
        }

        @Override
        final void show(Cursor cursor, int offset, String name, PrintStream port, Decoding decoding) {
            displayFieldPrefix(cursor, offset, name, port);
            int fileTypeByte = cursor.getUnsignedByte(offset);
            port.print("[" + Hex.b(fileTypeByte) + "]");
            for (IntegerSliceType slice : slices) {
                port.print(slice.decode(fileTypeByte));
            }
            port.println();
        }
    }

    static final StructFieldType D64_SECTOR_CHAIN_START = new StructFieldType() {
        @Override
        final void show(Cursor cursor, int offset, String name, PrintStream port, Decoding decoding) {
            displayFieldPrefix(cursor, offset, name, port);
            int track = cursor.getUnsignedByte(offset);
            int sector = cursor.getUnsignedByte(offset + 1);
            port.print('[' + Hex.b(track) + ' ' + Hex.b(sector) + "] ");
            if (track == 0 && sector == 0) {
                port.print("n/a");
            } else {
                port.print("track " + track + ", sector " + sector);
            }
            port.println();
        }
    };

    static final StructFieldType UNSIGNED_LEWYDE = new StructFieldType() {
        @Override
        final void show(Cursor cursor, int offset, String name, PrintStream port, Decoding decoding) throws ImageError {
            displayFieldPrefix(cursor, offset, name, port);
            int value = cursor.getUnsignedLewyde(offset);
            port.println("[" + Hex.w(value) + "] " + value);
        }
    };
}