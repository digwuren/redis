package net.mirky.redis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.mirky.redis.ControlData.LineParseError;
import net.mirky.redis.ParseUtil.IndentationSensitiveLexer;
import net.mirky.redis.ResourceManager.ResolutionError;
import net.mirky.redis.StructFieldType.SlicedIntegerType;

public abstract class Struct {
    public final String name;

    public Struct(String name) {
        this.name = name;
    }

    public abstract int show(Cursor cursor, PrintStream port, Decoding decoding) throws ImageError;

    private static final Struct.Basic BLANK = new Struct.Basic("blank");

    public static final class Union extends Struct {
        private final Rule[] rules;

        public Union(String name, Rule... rules) {
            super(name);
            if (rules.length == 0 || !(rules[rules.length - 1] instanceof Rule.Always)) {
                throw new RuntimeException("the last rule in a Struct.Conditional must be a Rule.Always");
            }
            this.rules = rules;
        }

        @Override
        public final int show(Cursor cursor, PrintStream port, Decoding decoding) throws ImageError {
            for (Rule rule : rules) {
                if (rule.matches(cursor)) {
                    return rule.struct.show(cursor, port, decoding);
                }
            }
            // No rule matched. This must not happen.
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

    public static final class Basic extends Struct {
        private final Struct.Field[] fields;

        public Basic(String name, Struct.Field... fields) {
            super(name);
            this.fields = fields;
        }

        @Override
        public final int show(Cursor cursor, PrintStream port, Decoding decoding) throws ImageError {
            port.println(Hex.t(cursor.tell()) + ": " + name);
            int offsetPastStruct = 0;
            for (Struct.Field field : fields) {
                int offsetPastField = field.show(cursor, port, decoding);
                if (offsetPastField > offsetPastStruct) {
                    offsetPastStruct = offsetPastField;
                }
            }
            return offsetPastStruct;
        }
    }

    public static final class Void extends Struct {
        private final int size;

        public Void(String name, int size) {
            super(name);
            this.size = size;
        }

        @Override
        public final int show(Cursor cursor, PrintStream port, Decoding decoding) throws ImageError {
            port.println(Hex.t(cursor.tell()) + ": " + name);
            Hex.dump(cursor.getBytes(0, size), cursor.tell(), decoding, port);
            return size;
        }
    }

    public static final class Field {
        public final int offset;
        public final String name;
        public final StructFieldType type;

        public Field(int offset, String name, StructFieldType type) {
            this.offset = offset;
            this.name = name;
            this.type = type;
        }

        /**
         * Show content of {@code this} field of the structure as a text line
         * via the given {@port}.
         * 
         * @return the offset just past the field, relative to the cursor's
         *         position
         * @throws ImageError
         *             in case the field can not be extracted from data
         *             available via {@code cursor}. Note that this exception
         *             can only be thrown before anything is output or
         *             immediately after completing a full line, never in the
         *             middle of outputting a line.
         */
        // FIXME: reorder the parameters so {@code port} goes last
        public final int show(Cursor cursor, PrintStream port, Decoding decoding) throws ImageError {
            int offsetPastField = type.show(cursor, offset, "", name, port, decoding);
            port.println();
            return offsetPastField;
        }
    }

    static abstract class FieldParameterParser {
        /**
         * Parse parameters of a field type, if any, and return the complete
         * {@link StructFieldType}. Called with {@code lexer} positioned
         * immediately after the keyword, so it may need to start by ignoring
         * horizontal whitespace.
         * 
         * @throws ControlData.LineParseError
         * @throws IOException
         */
        abstract StructFieldType parseParameters(IndentationSensitiveLexer lexer) throws ControlData.LineParseError, IOException;
    }

    // for field types without parameters
    private static final class SimpleFieldParameterParser extends FieldParameterParser {
        private final StructFieldType fieldType;

        public SimpleFieldParameterParser(StructFieldType fieldType) {
            this.fieldType = fieldType;
        }

        @Override
        final StructFieldType parseParameters(IndentationSensitiveLexer lexer) throws ControlData.LineParseError, IOException {
            lexer.passNewline();
            return fieldType;
        }
    }

    private static final class SlicedIntegerFieldParameterParser extends FieldParameterParser {
        private final StructFieldType.SlicedIntegerType integerType;
    
        public SlicedIntegerFieldParameterParser(SlicedIntegerType integerType) {
            this.integerType = integerType;
        }
    
        @Override
        final StructFieldType parseParameters(IndentationSensitiveLexer lexer) throws ControlData.LineParseError, IOException {
            lexer.skipSpaces();
            lexer.passNewline();
            lexer.passIndent();
            ArrayList<IntegerSlice> slices = new ArrayList<IntegerSlice>();
            while (!lexer.atDedent()) {
                lexer.noIndent();
                slices.add(parseIntegerSlice(lexer));
            }
            lexer.skipThisDedent();
            return new StructFieldType.SlicedIntegerField(integerType, slices.toArray(new IntegerSlice[0]));
        }
    }

    private static final Map<String, FieldParameterParser> KNOWN_FIELD_TYPES = new HashMap<String, FieldParameterParser>();
    
    static final FieldParameterParser getFieldTypeParameterParser(String name) {
        return KNOWN_FIELD_TYPES.get(name);
    }
    
    static {
        KNOWN_FIELD_TYPES.put("unsigned-byte", new SimpleFieldParameterParser(StructFieldType.UNSIGNED_BYTE));
        KNOWN_FIELD_TYPES.put("unsigned-lewyde", new SimpleFieldParameterParser(StructFieldType.UNSIGNED_LEWYDE));
        KNOWN_FIELD_TYPES.put("unsigned-bewyde", new SimpleFieldParameterParser(StructFieldType.UNSIGNED_BEWYDE));

        KNOWN_FIELD_TYPES.put("d64-sector-chain-start", new SimpleFieldParameterParser(StructFieldType.D64_SECTOR_CHAIN_START));

        KNOWN_FIELD_TYPES.put("padded-string", new FieldParameterParser() {
            @Override
            final StructFieldType parseParameters(IndentationSensitiveLexer lexer) throws ControlData.LineParseError, IOException {
                lexer.skipSpaces();
                int size = lexer.parseUnsignedInteger("string length");
                lexer.skipSpaces();
                int padding = lexer.parseUnsignedInteger("char code");
                if (padding >= 0x100) {
                    lexer.complain("value too high to be a char code");
                }
                lexer.passNewline();
                return new StructFieldType.PaddedString(size, (byte) padding);
            }
        });

        KNOWN_FIELD_TYPES.put("sliced-byte", new SlicedIntegerFieldParameterParser(StructFieldType.SlicedIntegerType.BYTE));
        KNOWN_FIELD_TYPES.put("sliced-lewyde", new SlicedIntegerFieldParameterParser(StructFieldType.SlicedIntegerType.LEWYDE));
        KNOWN_FIELD_TYPES.put("sliced-bewyde", new SlicedIntegerFieldParameterParser(StructFieldType.SlicedIntegerType.BEWYDE));
    }

    // Note that there are two forms of integer slices: 'basic' and 'flags'.
    // Basic slices hold an integer, flag slices hold a single bit.
    static final IntegerSlice parseIntegerSlice(IndentationSensitiveLexer lexer) throws LineParseError, IOException {
        lexer.pass('@');
        lexer.pass('.');
        int rightShift = lexer.parseUnsignedInteger("right shift");
        lexer.skipSpaces();
        IntegerSlice slice;
        if (lexer.atUnsignedInteger()) {
            // it's a basic slice; the field width (in bits) comes next
            int fieldWidth = lexer.parseUnsignedInteger("field width");
            if (fieldWidth == 0) {
                lexer.complain("zero-bit field?");
            }
            List<String> meanings = new ArrayList<String>();
            while (true) {
                lexer.skipSpaces();
                if (!lexer.at('"')) {
                    break;
                }
                meanings.add(lexer.parseThisString());
            }
            slice = new IntegerSlice.Basic(rightShift, fieldWidth, meanings.toArray(new String[0]));
        } else {
            // it's a flag slice; the field width is implicitly one
            String setMessage;
            if (lexer.at('"')) {
                setMessage = lexer.parseThisString();
            } else {
                setMessage = null;
            }
            lexer.skipSpaces();
            String clearMessage;
            if (lexer.at('/')) {
                lexer.skipChar();
                lexer.skipSpaces();
                clearMessage = lexer.parseString("cleared flag meaning");
            } else {
                clearMessage = null;
            }
            if (setMessage == null && clearMessage == null) {
                lexer.complain("expected bit meaning");
            }
            slice = new IntegerSlice.Flag(rightShift, setMessage, clearMessage);
        }
        lexer.passNewline();
        return slice;
    }

    public static final ResourceManager<Struct> MANAGER = new ResourceManager<Struct>("struct") {
        @Override
        public final Struct load(String name, BufferedReader reader) {
            IndentationSensitiveLexer lexer = new ParseUtil.IndentationSensitiveFileLexer(reader, name,
                    '#');
            try {
                ArrayList<Field> fields = new ArrayList<Field>();
                while (!lexer.atEndOfFile()) {
                    lexer.noIndent();
                    lexer.pass('@');
                    int fieldOffset = lexer.parseUnsignedInteger("offset");
                    lexer.skipSpaces();
                    String fieldName = lexer.parseString("field name");
                    lexer.skipSpaces();
                    lexer.pass(':');
                    lexer.skipSpaces();
                    if (!lexer.atWord()) {
                        lexer.complain("expected field type");
                    }
                    String fieldTypeName = lexer.parseThisDashedWord();
                    FieldParameterParser parameterParser = getFieldTypeParameterParser(fieldTypeName);
                    if (parameterParser == null) {
                        lexer.complain("unknown field type");
                        // {@link
                        // ParseUtil.IndentationSensitiveFileLexer#complain(String)}
                        // returned?
                        throw new RuntimeException("bug detected");
                    }
                    StructFieldType fieldType = parameterParser.parseParameters(lexer);
                    fields.add(new Field(fieldOffset, fieldName, fieldType));
                }
                reader.close();
                return new Struct.Basic(name, fields.toArray(new Field[0]));
            } catch (IOException e) {
                throw new RuntimeException("I/O error reading resource " + name, e);
            } catch (ControlData.LineParseError e) {
                throw new RuntimeException("parse error reading resource " + name, e);
            }
        }
    };

    public static final Struct D64_DIRENT_UNION;

    static {
        try {
            D64_DIRENT_UNION = new Struct.Union("d64-dirent-union",
                    new Union.Rule.RegionBlank(2, 30, BLANK),
                    new Union.Rule.ByteEquals(2, (byte) 0, new Void("d64-dirent-blank", 32)),
                    new Union.Rule.Always(Struct.MANAGER.get("d64-dirent")));
        } catch (ResolutionError e) {
            throw new RuntimeException("Bug detected", e);
        }
        MANAGER.cache.put("d64-dirent-union", D64_DIRENT_UNION);
    }
}
