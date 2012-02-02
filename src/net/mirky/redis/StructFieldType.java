package net.mirky.redis;

import java.io.PrintStream;

abstract class StructFieldType extends AbstractStruct {
    public StructFieldType() {
        // nothing to be done here
    }

    static final class PaddedString extends StructFieldType {
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

    enum SlicedIntegerType {
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
    
    static class SlicedIntegerField extends StructFieldType {
        private final SlicedIntegerType integerType;
        private final IntegerSlice[] slices;

        public SlicedIntegerField(SlicedIntegerType integerType, IntegerSlice[] slices) {
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

    static final class StructWrapperFieldType extends StructFieldType {
        private final Struct struct;
    
        StructWrapperFieldType(Struct struct) {
            this.struct = struct;
        }
    
        @Override
        public final int show(Cursor cursor, String indentation, String name, Decoding decoding, PrintStream port) throws ImageError {
            return struct.show(cursor, indentation, name, decoding, port);
        }
    }

    static final class PlainUnsignedInteger extends StructFieldType {
        private final SlicedIntegerType type;

        PlainUnsignedInteger(SlicedIntegerType type) {
            this.type = type;
        }

        @Override
        public final int show(Cursor cursor, String indentation, String name, Decoding decoding, PrintStream port) throws ImageError {
            int value = type.extract(cursor);
            port.println(Hex.t(cursor.tell()) + ": [" + type.hex(value) + "]" + type.hexPadding() + " " + indentation + name + ": " + value);
            return type.size();
        }
    }
    
    static final StructFieldType UNSIGNED_LEWYDE = new PlainUnsignedInteger(SlicedIntegerType.LEWYDE);
    static final StructFieldType UNSIGNED_BEWYDE = new PlainUnsignedInteger(SlicedIntegerType.BEWYDE);
    static final StructFieldType UNSIGNED_BYTE = new PlainUnsignedInteger(SlicedIntegerType.BYTE);
}