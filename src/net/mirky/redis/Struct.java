package net.mirky.redis;

import java.io.PrintStream;

public abstract class Struct {
    public final String name;

    public Struct(String name) {
        this.name = name;
    }

    protected final void showBreadcrumbs(Cursor cursor, String path, PrintStream port) {
        port.println(Hex.t(cursor.tell()) + ": " + name + " @ " + path);
    }

    public abstract void show(Cursor cursor, String path, PrintStream port, Decoding decoding) throws ImageError;

    static final Struct.Basic BLANK = new Struct.Basic("blank");
    
    public static final class Conditional extends Struct {
        private final Rule[] rules;

        public Conditional(String name, Rule... rules) {
            super(name);
            if (rules.length == 0 || !(rules[rules.length - 1] instanceof Rule.Always)) {
                throw new RuntimeException("the last rule in a Struct.Conditional must be a Rule.Always");
            }
            this.rules = rules;
        }
        
        @Override
        public final void show(Cursor cursor, String path, PrintStream port, Decoding decoding) throws ImageError {
            for (Rule rule : rules) {
                if (rule.matches(cursor)) {
                    rule.struct.show(cursor, path, port, decoding);
                    return;
                }
            }
            // No rule matched.  This must not happen.
            throw new RuntimeException("bug detected");
        }

        public static abstract class Rule {
            public final Struct struct;

            public Rule(Struct struct) {
                this.struct = struct;
            }
            
            public abstract boolean matches(Cursor cursor);

            public static final class RegionBlank extends Rule {
                public final int offset;
                public final int size;
                
                public RegionBlank(int offset, int size, Struct struct) {
                    super(struct);
                    this.offset = offset;
                    this.size = size;
                }
                
                @Override
                public final boolean matches(Cursor cursor) {
                    return cursor.regionBlank(offset, size);
                }
            }
            
            public static final class ByteEquals extends Rule {
                public final int offset;
                public final byte etalon;
                
                public ByteEquals(int offset, byte etalon, Struct struct) {
                    super(struct);
                    this.offset = offset;
                    this.etalon = etalon;
                }
                
                @Override
                public final boolean matches(Cursor cursor) {
                    return ((byte) cursor.getUnsignedByte(offset)) == etalon;
                }
            }
            
            public static final class Always extends Rule {
                public Always(Struct struct) {
                    super(struct);
                }

                @Override
                public final boolean matches(Cursor cursor) {
                    return true;
                }
            }
        }
    }

    static final Struct.Basic D64DIRENTRY_REGULAR = new Struct.Basic("d64direntry.regular", 
            new Field(5, "filename", new StructFieldType.PaddedString(16, ((byte) 0xA0))),
            new Field(2, "file type", new StructFieldType.SlicedByteField(
                    new IntegerSliceType.Basic(0, 4, "DEL", "SEQ", "PRG", "USR", "REL"),
                    new IntegerSliceType.Flag(6, " (locked)", ""),
                    new IntegerSliceType.Flag(7, "", " (unclosed)")
            )),
            new Field(3, "data start", StructFieldType.D64_SECTOR_CHAIN_START),
            new Field(21, "side chain", StructFieldType.D64_SECTOR_CHAIN_START),
            new Field(30, "sector count", StructFieldType.UNSIGNED_LEWYDE)
    );

    public static final Struct D64DIRENTRY = new Struct.Conditional("d64direntry",
            new Conditional.Rule.RegionBlank(2, 30, BLANK),
            new Conditional.Rule.ByteEquals(2, (byte) 0, new Void("d64direntry.unused", 32)),
            new Conditional.Rule.Always(D64DIRENTRY_REGULAR)
    );

    static final class Basic extends Struct {
        private final Struct.Field[] fields;
    
        public Basic(String name, Struct.Field... fields) {
            super(name);
            this.fields = fields;
        }
    
        @Override
        public final void show(Cursor cursor, String path, PrintStream port, Decoding decoding) throws ImageError {
            showBreadcrumbs(cursor, path, port);
            for (Struct.Field field : fields) {
                field.type.show(cursor, field.offset, field.name, port, decoding);
            }
        }
    }

    static final class Void extends Struct {
        private final int size;
        
        public Void(String name, int size) {
            super(name);
            this.size = size;
        }
    
        @Override
        public final void show(Cursor cursor, String path, PrintStream port, Decoding decoding) throws ImageError {
            showBreadcrumbs(cursor, path, port);
            Hex.dump(cursor.getBytes(0, size), cursor.tell(), decoding, port);
        }
    }

    static final class Field {
        public final int offset;
        public final String name;
        public final StructFieldType type;
        
        public Field(int offset, String name, StructFieldType type) {
            this.offset = offset;
            this.name = name;
            this.type = type;
        }
    }
}