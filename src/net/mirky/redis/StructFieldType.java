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
     * @return the offset just past the field, relative to the cursor's position
     * 
     * @throws ImageError
     *             in case the field can not be extracted from data available
     *             via {@code cursor}. Note that this exception can only be
     *             thrown before anything is output, never in the middle of
     *             outputting a line.
     */
    abstract int show(Cursor cursor, String indentation, String name, Decoding decoding, PrintStream port) throws ImageError;

    static final class PaddedString extends StructFieldType {
        private final int size;
        private final byte padding;

        public PaddedString(int size, byte padding) {
            this.size = size;
            this.padding = padding;
        }

        @Override
        final int show(Cursor cursor, String indentation, String name, Decoding decoding, PrintStream port) throws ImageError {
            byte[] bytes = cursor.getPaddedBytes(0, size, padding);
            port.print(Hex.t(cursor.tell()) + ": [...]   " + indentation + name + ": ");
            decoding.displayForeignStringAsLiteral(bytes, port);
            port.println();
            return size;
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
        final int show(Cursor cursor, String indentation, String name, Decoding decoding, PrintStream port) throws ImageError {
            int wholeField = integerType.extract(cursor, 0);
            port.print(Hex.t(cursor.tell()) + ": [" + integerType.hex(wholeField) + "] " + integerType.hexPadding() + indentation + name + ':');
            for (IntegerSlice slice : slices) {
                port.print(slice.decode(wholeField));
            }
            port.println();
            return integerType.size();
        }
    }

    static final class StructWrapperFieldType extends StructFieldType {
        private final Struct struct;
    
        StructWrapperFieldType(Struct struct) {
            this.struct = struct;
        }
    
        @Override
        final int show(Cursor cursor, String indentation, String name, Decoding decoding, PrintStream port) throws ImageError {
            return struct.show(cursor, indentation, name, decoding, port);
        }
    }

    static final StructFieldType UNSIGNED_LEWYDE = new StructFieldType() {
        @Override
        final int show(Cursor cursor, String indentation, String name, Decoding decoding, PrintStream port) throws ImageError {
            int value = cursor.getUnsignedLewyde(0);
            port.println(Hex.t(cursor.tell()) + ": [" + Hex.w(value) + "]  " + indentation + name + ": " + value);
            return 2;
        }
    };

    static final StructFieldType UNSIGNED_BEWYDE = new StructFieldType() {
        @Override
        final int show(Cursor cursor, String indentation, String name, Decoding decoding, PrintStream port) throws ImageError {
            int value = cursor.getUnsignedBewyde(0);
            port.println(Hex.t(cursor.tell()) + ": [" + Hex.w(value) + "]  " + indentation + name + ": " + value);
            return 2;
        }
    };

    static final StructFieldType UNSIGNED_BYTE = new StructFieldType() {
        @Override
        final int show(Cursor cursor, String indentation, String name, Decoding decoding, PrintStream port) {
            int value = cursor.getUnsignedByte(0);
            port.println(Hex.t(cursor.tell()) + ": [" + Hex.b(value) + "]    " + indentation + name + ": " +  value);
            return 1;
        }
    };
}