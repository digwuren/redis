package net.mirky.redis;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.mirky.redis.ControlData.LineParseError;

abstract class StructureDescriptionParser {
    private StructureDescriptionParser() {
        // not a real constructor
    }

    static abstract class ParameterParser {
        /**
         * Parse parameters of a field type, if any, and return the complete
         * {@link StructFieldType}. Called with {@code lexer} positioned
         * immediately after the keyword, so it may need to start by ignoring
         * horizontal whitespace.
         * 
         * @throws ControlData.LineParseError
         * @throws IOException
         */
        abstract BinaryElementType parseParameters(ParseUtil.IndentationSensitiveLexer lexer) throws ControlData.LineParseError, IOException;
    }

    // for field types without parameters
    static final class SimpleFieldParameterParser extends StructureDescriptionParser.ParameterParser {
        private final BinaryElementType fieldType;
    
        public SimpleFieldParameterParser(BinaryElementType fieldType) {
            this.fieldType = fieldType;
        }
    
        @Override
        final BinaryElementType parseParameters(ParseUtil.IndentationSensitiveLexer lexer) throws ControlData.LineParseError, IOException {
            lexer.passNewline();
            return fieldType;
        }
    }

    static final class SlicedIntegerFieldParameterParser extends StructureDescriptionParser.ParameterParser {
        private final BinaryElementType.BasicInteger integerType;
    
        public SlicedIntegerFieldParameterParser(BinaryElementType.BasicInteger integerType) {
            this.integerType = integerType;
        }
    
        @Override
        final BinaryElementType.SlicedInteger parseParameters(ParseUtil.IndentationSensitiveLexer lexer) throws ControlData.LineParseError, IOException {
            lexer.skipSpaces();
            lexer.passNewline();
            lexer.passIndent();
            ArrayList<BinaryElementType.SlicedInteger.Slice> slices = new ArrayList<BinaryElementType.SlicedInteger.Slice>();
            while (!lexer.atDedent()) {
                lexer.noIndent();
                slices.add(StructureDescriptionParser.parseIntegerSlice(lexer));
            }
            lexer.skipThisDedent();
            return new BinaryElementType.SlicedInteger(integerType, slices.toArray(new BinaryElementType.SlicedInteger.Slice[0]));
        }
    }

    // Note that there are two forms of integer slices: 'basic' and 'flags'.
    // Basic slices hold an integer, flag slices hold a single bit.
    static final BinaryElementType.SlicedInteger.Slice parseIntegerSlice(ParseUtil.IndentationSensitiveLexer lexer) throws ControlData.LineParseError, IOException {
        lexer.pass('@');
        lexer.pass('.');
        int rightShift = lexer.parseUnsignedInteger("right shift");
        lexer.skipSpaces();
        BinaryElementType.SlicedInteger.Slice slice;
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
            slice = new BinaryElementType.SlicedInteger.Slice.Basic(rightShift, fieldWidth, meanings.toArray(new String[0]));
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
            slice = new BinaryElementType.SlicedInteger.Slice.Flag(rightShift, setMessage, clearMessage);
        }
        lexer.passNewline();
        return slice;
    }

    public static final BinaryElementType.Struct parseStructureDescription(String name, BufferedReader reader) throws LineParseError, IOException,
            RuntimeException {
        ParseUtil.IndentationSensitiveLexer lexer = new ParseUtil.IndentationSensitiveFileLexer(reader, name,
        '#');
        ArrayList<BinaryElementType.Struct.Field> fields = new ArrayList<BinaryElementType.Struct.Field>();
        while (!lexer.atEndOfFile()) {
            lexer.noIndent();
            lexer.pass('@');
            int fieldOffset = lexer.parseUnsignedInteger("offset");
            lexer.skipSpaces();
            String fieldName = lexer.parseDashedWord("field name");
            lexer.skipSpaces();
            lexer.pass(':');
            lexer.skipSpaces();
            if (!lexer.atWord()) {
                lexer.complain("expected field type");
            }
            String fieldTypeName = lexer.parseThisDashedWord();
            StructureDescriptionParser.ParameterParser parameterParser = getFieldTypeParameterParser(fieldTypeName);
            BinaryElementType fieldType;
            if (parameterParser == null) {
                try {
                    fieldType = BinaryElementType.MANAGER.get(fieldTypeName);
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
            fields.add(new BinaryElementType.Struct.Field(fieldOffset, fieldName, fieldType));
        }
        reader.close();
        return new BinaryElementType.Struct(name, fields.toArray(new BinaryElementType.Struct.Field[0]));
    }

    private static final Map<String, StructureDescriptionParser.ParameterParser> KNOWN_FIELD_TYPES = new HashMap<String, StructureDescriptionParser.ParameterParser>();

    static final StructureDescriptionParser.ParameterParser getFieldTypeParameterParser(String name) {
        return KNOWN_FIELD_TYPES.get(name);
    }

    static {
        KNOWN_FIELD_TYPES.put("padded-string", new StructureDescriptionParser.ParameterParser() {
            @Override
            final BinaryElementType parseParameters(ParseUtil.IndentationSensitiveLexer lexer) throws ControlData.LineParseError, IOException {
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

        KNOWN_FIELD_TYPES.put("sliced-byte", new StructureDescriptionParser.SlicedIntegerFieldParameterParser(BinaryElementType.BasicInteger.BYTE));
        KNOWN_FIELD_TYPES.put("sliced-lewyde", new StructureDescriptionParser.SlicedIntegerFieldParameterParser(BinaryElementType.BasicInteger.LEWYDE));
        KNOWN_FIELD_TYPES.put("sliced-bewyde", new StructureDescriptionParser.SlicedIntegerFieldParameterParser(BinaryElementType.BasicInteger.BEWYDE));
    }
}