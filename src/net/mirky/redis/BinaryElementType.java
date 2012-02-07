package net.mirky.redis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;

public abstract class BinaryElementType {
    /**
     * Show the content of an item of {@code this} structure, extracted from the
     * given {@code cursor}, as a sequence of full lines, output to the given
     * {@code port}. If the field contains textual values, these are to be
     * parsed using the given {@code decoding}.  Advance the cursor over the extracted item.
     * 
     * @param indentation
     *            indentation prefix inherited from the context
     * @param itemName
     *            contextual name of the item
     * 
     * @throws ImageError
     *             in case the field can not be extracted from data available
     *             via {@code cursor}. Note that this exception can only be
     *             thrown before anything is output, never in the middle of
     *             outputting a line.
     */
    public abstract void pass(Cursor cursor, String indentation, String itemName, Decoding decoding, PrintStream port)
            throws ImageError;

    static final class PaddedString extends BinaryElementType {
        private final int size;
        private final byte padding;

        public PaddedString(int size, byte padding) {
            this.size = size;
            this.padding = padding;
        }

        @Override
        public final void pass(Cursor cursor, String indentation, String itemName, Decoding decoding,
                PrintStream port) throws ImageError {
            int pos = cursor.tell();
            byte[] bytes = cursor.passPaddedBytes(size, padding);
            port.print(Hex.t(pos) + ": [...]   " + indentation + itemName + ": ");
            decoding.displayForeignStringAsLiteral(bytes, port);
            port.println();
        }
    }

    enum BasicInteger {
        BYTE, LEWYDE, BEWYDE;

        public final int pass(Cursor cursor) throws ImageError, RuntimeException {
            switch (this) {
                case BYTE:
                    return cursor.passUnsignedByte();
                case LEWYDE:
                    return cursor.passUnsignedLewyde();
                case BEWYDE:
                    return cursor.passUnsignedBewyde();
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
        private final Slice[] slices;

        public SlicedInteger(BasicInteger integerType, Slice[] slices) {
            this.integerType = integerType;
            this.slices = slices;
        }

        @Override
        public final void pass(Cursor cursor, String indentation, String itemName, Decoding decoding,
                PrintStream port) throws ImageError {
            int pos = cursor.tell();
            int wholeField = integerType.pass(cursor);
            port.print(Hex.t(pos) + ": [" + integerType.hex(wholeField) + "]" + integerType.hexPadding() + " " + indentation + itemName + ':');
            for (Slice slice : slices) {
                port.print(slice.decode(wholeField));
            }
            port.println();
        }

        /**
         * An integer slice specifies how to extract and interpret a part of an integer
         * or other similar bit vector. The main integer is normally extracted from the
         * binary via a {@link BinaryElementType.SlicedInteger} instance.
         */
        static abstract class Slice {
            protected final int rightShift;

            protected Slice(int shift) {
                this.rightShift = shift;
            }

            /**
             * Given the full integer, extract this slice and decode it for human
             * consumption.
             * 
             * @param field
             *            full field, not just this slice
             * @return decoded slice as a {@link String} instance. If the result is
             *         a non-empty string, it will start with a space.
             */
            public abstract String decode(int field);

            static final class Basic extends Slice {
                private final int width;
                private final String[] meanings;

                // Note that {@code meanings} can be shorter than {@code 1 << bitCount}, and it
                // can contain {@code null}:s.
                public Basic(int rightShift, int width, String[] meanings) {
                    super(rightShift);
                    this.width = width;
                    this.meanings = meanings;
                }

                @Override
                public final String decode(int field) {
                    int code = (field >> rightShift) & ((1 << width) - 1);
                    String meaning = code < meanings.length ? meanings[code] : null;
                    if (meaning != null) {
                        return ' ' + meaning;
                    } else {
                        return " #" + code + " (invalid)";
                    }
                }
            }

            static final class Flag extends Slice {
                private final String setMeaning;
                private final String clearMeaning;

                public Flag(int rightShift, String setMeaning, String clearMeaning) {
                    super(rightShift);
                    this.setMeaning = setMeaning;
                    this.clearMeaning = clearMeaning;
                }

                @Override
                public final String decode(int field) {
                    String meaning = ((field >> rightShift) & 1) != 0 ? setMeaning : clearMeaning;
                    if (meaning != null) {
                        return ' ' + meaning;
                    } else {
                        return "";
                    }
                }
            }
        }
    }

    static final class PlainUnsignedInteger extends BinaryElementType {
        private final BasicInteger type;

        PlainUnsignedInteger(BasicInteger type) {
            this.type = type;
        }

        @Override
        public final void pass(Cursor cursor, String indentation, String itemName, Decoding decoding,
                PrintStream port) throws ImageError {
            int pos = cursor.tell();
            int value = type.pass(cursor);
            port.println(Hex.t(pos) + ": [" + type.hex(value) + "]" + type.hexPadding() + " " + indentation + itemName + ": " + value);
        }
    }
    
    static final PlainUnsignedInteger UNSIGNED_LEWYDE = new PlainUnsignedInteger(BasicInteger.LEWYDE);
    static final PlainUnsignedInteger UNSIGNED_BEWYDE = new PlainUnsignedInteger(BasicInteger.BEWYDE);
    static final PlainUnsignedInteger UNSIGNED_BYTE = new PlainUnsignedInteger(BasicInteger.BYTE);

    public static final class Struct extends BinaryElementType {
        private final Struct.Step[] steps;

        public Struct(Struct.Step... fields) {
            this.steps = fields;
        }

        @Override
        public final void pass(Cursor cursor, String indentation, String itemName, Decoding decoding,
                PrintStream port) throws ImageError {
            port.println(Hex.t(cursor.tell()) + ":         " + indentation + (itemName != null ? itemName + ": " : "") + "struct");
            Context ctx = new Context(cursor, indentation + "  ", decoding, port);
            int posPastStruct = ctx.origin;
            for (Struct.Step step : steps) {
                step.step(ctx);
                if (cursor.tell() > posPastStruct) {
                    posPastStruct = cursor.tell();
                }
            }
            cursor.seek(posPastStruct);
        }

        public static final class Context {
            public final Cursor cursor;
            public final String indentation;
            public final Decoding decoding;
            public final PrintStream port;
            public final int origin;

            public Context(Cursor cursor, String indentation, Decoding decoding, PrintStream port) {
                this.cursor = cursor;
                this.indentation = indentation;
                this.decoding = decoding;
                this.port = port;
                origin = cursor.tell();
            }
        }

        public static abstract class Step {
            public abstract void step(Context ctx) throws ImageError;

            /**
             * For field pass steps, set the field type.  For seek steps, do nothing.
             */
            public abstract void setType(BinaryElementType type);

            public static final class LocalSeek extends Step {
                public final int offset;
            
                public LocalSeek(int offset) {
                    this.offset = offset;
                }
            
                @Override
                public final void step(Context ctx) throws ImageError {
                    ctx.cursor.seek(ctx.origin + offset);
                }

                @Override
                public final void setType(BinaryElementType type) {
                    // nothing to do, seek steps are not typed
                }
            }

            public static final class RelSeek extends Step {
                public final int offset;
            
                public RelSeek(int offset) {
                    this.offset = offset;
                }
            
                @Override
                public final void step(Context ctx) throws ImageError {
                    ctx.cursor.advance(offset);
                }

                @Override
                public final void setType(BinaryElementType type) {
                    // nothing to do, seek steps are not typed
                }
            }

            public static final class Pass extends Step {
                public final String name;
                public BinaryElementType type;
            
                public Pass(String name, BinaryElementType type) {
                    this.name = name;
                    this.type = type;
                }
            
                @Override
                public final void step(Context ctx) throws ImageError {
                    type.pass(ctx.cursor, ctx.indentation, name, ctx.decoding, ctx.port);
                }

                @Override
                public final void setType(BinaryElementType type) {
                    this.type = type;
                }
            }
        }
    }

    public static final ResourceManager<BinaryElementType> MANAGER = new ResourceManager<BinaryElementType>("struct") {
        @Override
        public final BinaryElementType load(String name, BufferedReader reader) {
            try {
                try {
                    return StructureDescriptionParser.parseStructureDescription(name, reader);
                } catch (ControlData.LineParseError e) {
                    throw new RuntimeException("parse error reading resource " + name, e);
                }
            } catch (IOException e) {
                throw new RuntimeException("I/O error reading resource " + name, e);
            }
        }
    };

    static {
        MANAGER.cache.put("unsigned-byte", UNSIGNED_BYTE);
        MANAGER.cache.put("unsigned-lewyde", UNSIGNED_LEWYDE);
        MANAGER.cache.put("unsigned-bewyde", UNSIGNED_BEWYDE);
    }
}
