package net.mirky.redis;

import java.io.PrintStream;

abstract class StructFieldType {
    public StructFieldType() {
        // nothing to be done here
    }

    /**
     * Show the content of a field of {@code this} type, extracted from the
     * specified {@code offset} via the given {@code cursor}, as a line output
     * to the given {@code port}. If the field contains textual values, these
     * are to be parsed using the given {@code decoding}.
     * 
     * @throws ImageError
     *             in case the field can not be extracted from data available
     *             via {@code cursor}. Note that this exception can only be
     *             thrown before anything is output, never in the middle of
     *             outputting a line.
     */
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

    enum SlicedIntegerType {
        BYTE, LEWYDE, BEWYDE;

        public final int extract(Cursor cursor, int offset) throws ImageError, RuntimeException {
            switch (this) {
                case BYTE:
                    return cursor.getUnsignedByte(offset);
                case LEWYDE:
                    return cursor.getUnsignedLewyde(offset);
                case BEWYDE:
                    return cursor.getUnsignedBewyde(offset);
                default:
                    throw new RuntimeException("bug detected");
            }
        }

        public final String hex(int i) {
            switch (this) {
                case BYTE:
                    return Hex.b(i);
                case LEWYDE:
                case BEWYDE:
                    return Hex.w(i);
                default:
                    throw new RuntimeException("bug detected");
            }
        }
    }
    
    static class SlicedIntegerField extends StructFieldType {
        private final SlicedIntegerType integerType;
        private final IntegerSlice[] slices;

        public SlicedIntegerField(SlicedIntegerType integerType, IntegerSlice[] slices) {
            this.integerType = integerType;
            this.slices = slices;
        }

        @Override
        final void show(Cursor cursor, int offset, PrintStream port, Decoding decoding) throws ImageError {
            int wholeField = integerType.extract(cursor, offset);
            port.print('[');
            port.print(integerType.hex(wholeField));
            port.print(']');
            for (IntegerSlice slice : slices) {
                port.print(slice.decode(wholeField));
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

    static final StructFieldType UNSIGNED_BEWYDE = new StructFieldType() {
        @Override
        final void show(Cursor cursor, int offset, PrintStream port, Decoding decoding) throws ImageError {
            int value = cursor.getUnsignedBewyde(offset);
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