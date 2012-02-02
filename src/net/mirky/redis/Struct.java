package net.mirky.redis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.mirky.redis.ParseUtil.IndentationSensitiveLexer;

public final class Struct extends BinaryElementType {
    private final String name;
    private final Struct.Field[] fields;

    public Struct(String name, Struct.Field... fields) {
        this.name = name;
        this.fields = fields;
    }

    @Override
    public final int show(Cursor cursor, String indentation, String itemName, Decoding decoding, PrintStream port) throws ImageError {
        port.println(Hex.t(cursor.tell()) + ":         " + indentation + (itemName != null ? itemName + ": " : "") + name);
        int offsetPastStruct = 0;
        for (Struct.Field field : fields) {
            int offsetPastField = field.offset + field.type.show(cursor.subcursor(field.offset), indentation + "  ", field.name, decoding, port);
            if (offsetPastField > offsetPastStruct) {
                offsetPastStruct = offsetPastField;
            }
        }
        return offsetPastStruct;
    }

    public static final class Field {
        public final int offset;
        public final String name;
        public final BinaryElementType type;

        public Field(int offset, String name, BinaryElementType fieldType) {
            this.offset = offset;
            this.name = name;
            this.type = fieldType;
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
        abstract BinaryElementType parseParameters(IndentationSensitiveLexer lexer) throws ControlData.LineParseError, IOException;
    }

    // for field types without parameters
    private static final class SimpleFieldParameterParser extends FieldParameterParser {
        private final BinaryElementType fieldType;

        public SimpleFieldParameterParser(BinaryElementType fieldType) {
            this.fieldType = fieldType;
        }

        @Override
        final BinaryElementType parseParameters(IndentationSensitiveLexer lexer) throws ControlData.LineParseError, IOException {
            lexer.passNewline();
            return fieldType;
        }
    }

    private static final class SlicedIntegerFieldParameterParser extends FieldParameterParser {
        private final BinaryElementType.SlicedIntegerType integerType;

        public SlicedIntegerFieldParameterParser(BinaryElementType.SlicedIntegerType integerType) {
            this.integerType = integerType;
        }

        @Override
        final BinaryElementType.SlicedIntegerField parseParameters(IndentationSensitiveLexer lexer) throws ControlData.LineParseError, IOException {
            lexer.skipSpaces();
            lexer.passNewline();
            lexer.passIndent();
            ArrayList<IntegerSlice> slices = new ArrayList<IntegerSlice>();
            while (!lexer.atDedent()) {
                lexer.noIndent();
                slices.add(parseIntegerSlice(lexer));
            }
            lexer.skipThisDedent();
            return new BinaryElementType.SlicedIntegerField(integerType, slices.toArray(new IntegerSlice[0]));
        }
    }

    private static final Map<String, FieldParameterParser> KNOWN_FIELD_TYPES = new HashMap<String, FieldParameterParser>();

    static final FieldParameterParser getFieldTypeParameterParser(String name) {
        return KNOWN_FIELD_TYPES.get(name);
    }

    static {
        KNOWN_FIELD_TYPES.put("unsigned-byte", new SimpleFieldParameterParser(BinaryElementType.UNSIGNED_BYTE));
        KNOWN_FIELD_TYPES.put("unsigned-lewyde", new SimpleFieldParameterParser(BinaryElementType.UNSIGNED_LEWYDE));
        KNOWN_FIELD_TYPES.put("unsigned-bewyde", new SimpleFieldParameterParser(BinaryElementType.UNSIGNED_BEWYDE));

        KNOWN_FIELD_TYPES.put("padded-string", new FieldParameterParser() {
            @Override
            final BinaryElementType parseParameters(IndentationSensitiveLexer lexer) throws ControlData.LineParseError, IOException {
                lexer.skipSpaces();
                int size = lexer.parseUnsignedInteger("string length");
                lexer.skipSpaces();
                int padding = lexer.parseUnsignedInteger("char code");
                if (padding >= 0x100) {
                    lexer.complain("value too high to be a char code");
                }
                lexer.passNewline();
                return new BinaryElementType.PaddedString(size, (byte) padding);
            }
        });

        KNOWN_FIELD_TYPES.put("sliced-byte", new SlicedIntegerFieldParameterParser(BinaryElementType.SlicedIntegerType.BYTE));
        KNOWN_FIELD_TYPES.put("sliced-lewyde", new SlicedIntegerFieldParameterParser(BinaryElementType.SlicedIntegerType.LEWYDE));
        KNOWN_FIELD_TYPES.put("sliced-bewyde", new SlicedIntegerFieldParameterParser(BinaryElementType.SlicedIntegerType.BEWYDE));
    }

    // Note that there are two forms of integer slices: 'basic' and 'flags'.
    // Basic slices hold an integer, flag slices hold a single bit.
    static final IntegerSlice parseIntegerSlice(IndentationSensitiveLexer lexer) throws ControlData.LineParseError, IOException {
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
                ArrayList<Struct.Field> fields = new ArrayList<Struct.Field>();
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
                    BinaryElementType fieldType;
                    if (parameterParser == null) {
                        try {
                            fieldType = Struct.MANAGER.get(fieldTypeName);
                        } catch (ResourceManager.ResolutionError e) {
                            lexer.complain("unknown field type");
                            // {@link
                            // ParseUtil.IndentationSensitiveFileLexer#complain(String)}
                            // returned?
                            throw new RuntimeException("bug detected");
                        }
                        lexer.passNewline();
                    } else {
                        fieldType = parameterParser.parseParameters(lexer);
                    }
                    fields.add(new Struct.Field(fieldOffset, fieldName, fieldType));
                }
                reader.close();
                return new Struct(name, fields.toArray(new Struct.Field[0]));
            } catch (IOException e) {
                throw new RuntimeException("I/O error reading resource " + name, e);
            } catch (ControlData.LineParseError e) {
                throw new RuntimeException("parse error reading resource " + name, e);
            }
        }
    };
}
