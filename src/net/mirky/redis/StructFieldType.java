package net.mirky.redis;

import java.io.PrintStream;

abstract class StructFieldType {
    public StructFieldType() {
        // nothing to be done here
    }

    /**
     * Show the content of a field of {@code this} type, extracted from the
     * specified {@code offset} via the given {@code cursor}, as a full line,
     * output to the given {@code port}. If the field contains textual values,
     * these are to be parsed using the given {@code decoding}.
     * @param indentation
     *            indentation prefix inherited from the context
     * @param name
     *            name of the field
     * 
     * @return the offset just past the field, relative to the cursor's position
     * 
     * @throws ImageError
     *             in case the field can not be extracted from data available
     *             via {@code cursor}. Note that this exception can only be
     *             thrown before anything is output, never in the middle of
     *             outputting a line.
     */
    abstract int show(Cursor cursor, int offset, String indentation, String name, Decoding decoding, PrintStream port) throws ImageError;

    static final class PaddedString extends StructFieldType {
        private final int size;
        private final byte padding;

        public PaddedString(int size, byte padding) {
            this.size = size;
            this.padding = padding;
        }

        @Override
        final int show(Cursor cursor, int offset, String indentation, String name, Decoding decoding, PrintStream port) throws ImageError {
            byte[] bytes = cursor.getPaddedBytes(offset, size, padding);
            port.print(Hex.t(cursor.tell() + offset) + ": [...]   " + indentation + name + ": ");
            decoding.displayForeignStringAsLiteral(bytes, port);
            port.println();
            return offset + size;
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
        
        public final int size() {
            switch (this) {
                case BYTE:
                    return 1;
                case LEWYDE:
                case BEWYDE:
                    return 2;
                default:
                    throw new RuntimeException("bug detected");
            }
        }

        public final String hexPadding() {
            switch (this) {
                case BYTE:
                    return "   ";
                case LEWYDE:
                case BEWYDE:
                    return " ";
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
        final int show(Cursor cursor, int offset, String indentation, String name, Decoding decoding, PrintStream port) throws ImageError {
            int wholeField = integerType.extract(cursor, offset);
            port.print(Hex.t(cursor.tell() + offset) + ": [" + integerType.hex(wholeField) + "] " + integerType.hexPadding() + indentation + name + ':');
            for (IntegerSlice slice : slices) {
                port.print(slice.decode(wholeField));
            }
            port.println();
            return offset + integerType.size();
        }
    }

    static final StructFieldType D64_SECTOR_CHAIN_START = new StructFieldType() {
        @Override
        final int show(Cursor cursor, int offset, String indentation, String name, Decoding decoding, PrintStream port) throws ImageError {
            Cursor subcursor = cursor.subcursor(offset);
            port.println(Hex.t(subcursor.tell()) + ":         " + indentation + name + ':');
            int offsetPastStruct = 0; // relative to subcursor
            Struct.Field[] fields = new Struct.Field[] {
                    new Struct.Field(0, "track", UNSIGNED_BYTE),
                    new Struct.Field(1, "sector", UNSIGNED_BYTE),
            };
            for (Struct.Field field : fields) {
                int offsetPastField = field.show(subcursor, indentation + "  ", decoding, port);
                if (offsetPastField > offsetPastStruct) {
                    offsetPastStruct = offsetPastField;
                }
            }
            return offset + offsetPastStruct;
        }
    };

    static final StructFieldType UNSIGNED_LEWYDE = new StructFieldType() {
        @Override
        final int show(Cursor cursor, int offset, String indentation, String name, Decoding decoding, PrintStream port) throws ImageError {
            int value = cursor.getUnsignedLewyde(offset);
            port.println(Hex.t(cursor.tell() + offset) + ": [" + Hex.w(value) + "]  " + indentation + name + ": " + value);
            return offset + 2;
        }
    };

    static final StructFieldType UNSIGNED_BEWYDE = new StructFieldType() {
        @Override
        final int show(Cursor cursor, int offset, String indentation, String name, Decoding decoding, PrintStream port) throws ImageError {
            int value = cursor.getUnsignedBewyde(offset);
            port.println(Hex.t(cursor.tell() + offset) + ": [" + Hex.w(value) + "]  " + indentation + name + ": " + value);
            return offset + 2;
        }
    };

    static final StructFieldType UNSIGNED_BYTE = new StructFieldType() {
        @Override
        final int show(Cursor cursor, int offset, String indentation, String name, Decoding decoding, PrintStream port) {
            int value = cursor.getUnsignedByte(offset);
            port.println(Hex.t(cursor.tell() + offset) + ": [" + Hex.b(value) + "]    " + indentation + name + ": " +  value);
            return offset + 1;
        }
    };
}