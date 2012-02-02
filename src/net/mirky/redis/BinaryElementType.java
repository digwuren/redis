package net.mirky.redis;

import java.io.PrintStream;

public abstract class BinaryElementType {
    /**
     * Show the content of an item of {@code this} structure, extracted from the
     * given {@code cursor}, as a sequence of full lines, output to the given
     * {@code port}. If the field contains textual values, these are to be
     * parsed using the given {@code decoding}.
     * 
     * @param indentation
     *            indentation prefix inherited from the context
     * @param itemName
     *            contextual name of the item
     * @return the offset just past the field, relative to the cursor's position
     * 
     * @throws ImageError
     *             in case the field can not be extracted from data available
     *             via {@code cursor}. Note that this exception can only be
     *             thrown before anything is output, never in the middle of
     *             outputting a line.
     */
    public abstract int show(Cursor cursor, String indentation, String itemName, Decoding decoding, PrintStream port)
            throws ImageError;


    static final class PaddedString extends BinaryElementType {
        private final int size;
        private final byte padding;

        public PaddedString(int size, byte padding) {
            this.size = size;
            this.padding = padding;
        }

        @Override
        public final int show(Cursor cursor, String indentation, String name, Decoding decoding, PrintStream port) throws ImageError {
            byte[] bytes = cursor.getPaddedBytes(0, size, padding);
            port.print(Hex.t(cursor.tell()) + ": [...]   " + indentation + name + ": ");
            decoding.displayForeignStringAsLiteral(bytes, port);
            port.println();
            return size;
        }
    }

    enum BasicInteger {
        BYTE, LEWYDE, BEWYDE;

        public final int extract(Cursor cursor) throws ImageError, RuntimeException {
            switch (this) {
                case BYTE:
                    return cursor.getUnsignedByte(0);
                case LEWYDE:
                    return cursor.getUnsignedLewyde(0);
                case BEWYDE:
                    return cursor.getUnsignedBewyde(0);
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
    
    static class SlicedInteger extends BinaryElementType {
        private final BasicInteger integerType;
        private final IntegerSlice[] slices;

        public SlicedInteger(BasicInteger integerType, IntegerSlice[] slices) {
            this.integerType = integerType;
            this.slices = slices;
        }

        @Override
        public final int show(Cursor cursor, String indentation, String name, Decoding decoding, PrintStream port) throws ImageError {
            int wholeField = integerType.extract(cursor);
            port.print(Hex.t(cursor.tell()) + ": [" + integerType.hex(wholeField) + "]" + integerType.hexPadding() + " " + indentation + name + ':');
            for (IntegerSlice slice : slices) {
                port.print(slice.decode(wholeField));
            }
            port.println();
            return integerType.size();
        }
    }

    static final class PlainUnsignedInteger extends BinaryElementType {
        private final BasicInteger type;

        PlainUnsignedInteger(BasicInteger type) {
            this.type = type;
        }

        @Override
        public final int show(Cursor cursor, String indentation, String name, Decoding decoding, PrintStream port) throws ImageError {
            int value = type.extract(cursor);
            port.println(Hex.t(cursor.tell()) + ": [" + type.hex(value) + "]" + type.hexPadding() + " " + indentation + name + ": " + value);
            return type.size();
        }
    }
    
    static final PlainUnsignedInteger UNSIGNED_LEWYDE = new PlainUnsignedInteger(BasicInteger.LEWYDE);
    static final PlainUnsignedInteger UNSIGNED_BEWYDE = new PlainUnsignedInteger(BasicInteger.BEWYDE);
    static final PlainUnsignedInteger UNSIGNED_BYTE = new PlainUnsignedInteger(BasicInteger.BYTE);
}
