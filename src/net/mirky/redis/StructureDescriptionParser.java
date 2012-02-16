package net.mirky.redis;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.mirky.redis.BinaryElementType.Struct.Step;

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
        abstract BinaryElementType parseParameters(ParseUtil.IndentableLexer lexer) throws IOException;
    }

    // for field types without parameters
    static final class SimpleFieldParameterParser extends StructureDescriptionParser.ParameterParser {
        private final BinaryElementType fieldType;
    
        public SimpleFieldParameterParser(BinaryElementType fieldType) {
            this.fieldType = fieldType;
        }
    
        @Override
        final BinaryElementType parseParameters(ParseUtil.IndentableLexer lexer) throws IOException {
            lexer.passLogicalNewline();
            return fieldType;
        }
    }

    static final class SlicedIntegerFieldParameterParser extends StructureDescriptionParser.ParameterParser {
        private final BinaryElementType.BasicInteger integerType;
    
        public SlicedIntegerFieldParameterParser(BinaryElementType.BasicInteger integerType) {
            this.integerType = integerType;
        }
    
        @Override
        final BinaryElementType.SlicedInteger parseParameters(ParseUtil.IndentableLexer lexer) throws IOException {
            lexer.passLogicalNewline();
            lexer.passIndent();
            ArrayList<BinaryElementType.SlicedInteger.Slice> slices = new ArrayList<BinaryElementType.SlicedInteger.Slice>();
            while (!lexer.atDedent()) {
                lexer.noIndent();
                slices.add(StructureDescriptionParser.parseIntegerSlice(lexer));
            }
            lexer.discardDedent();
            return new BinaryElementType.SlicedInteger(integerType, slices.toArray(new BinaryElementType.SlicedInteger.Slice[0]));
        }
    }

    // Note that there are two forms of integer slices: 'basic' and 'flags'.
    // Basic slices hold an integer, flag slices hold a single bit.
    static final BinaryElementType.SlicedInteger.Slice parseIntegerSlice(ParseUtil.IndentableLexer lexer) throws IOException {
        lexer.pass('@');
        lexer.pass('.');
        int rightShift = lexer.readUnsignedInteger("right shift");
        lexer.skipSpaces();
        BinaryElementType.SlicedInteger.Slice slice;
        if (lexer.atDigit()) {
            // it's a basic slice; the field width (in bits) comes next
            int posBeforeSliceWidth = lexer.getPos();
            int sliceWidth = lexer.readUnsignedInteger("slice width");
            if (sliceWidth == 0) {
                lexer.errorAtPos(posBeforeSliceWidth, "zero-bit slice?");
            }
            List<String> meanings = new ArrayList<String>();
            while (true) {
                lexer.skipSpaces();
                if (!lexer.at('"')) {
                    break;
                }
                meanings.add(lexer.readStringLiteral(null));
            }
            slice = new BinaryElementType.SlicedInteger.Slice.Basic(rightShift, sliceWidth, meanings.toArray(new String[0]));
        } else {
            // it's a flag slice; the field width is implicitly one
            String setFlagName = lexer.readOptStringLiteral(null);
            lexer.skipSpaces();
            String clearFlagName;
            if (lexer.at('/')) {
                lexer.skipChar();
                lexer.skipSpaces();
                clearFlagName = lexer.readStringLiteral("cleared flag meaning");
            } else {
                clearFlagName = null;
            }
            if (setFlagName == null && clearFlagName == null) {
                lexer.error("expected bit meaning");
            }
            slice = new BinaryElementType.SlicedInteger.Slice.Flag(rightShift, setFlagName, clearFlagName);
        }
        lexer.passLogicalNewline();
        return slice;
    }

    public static final BinaryElementType parseStructureDescription(String name, BufferedReader reader) throws IOException {
        ParseUtil.IndentableLexer lexer = new ParseUtil.IndentableLexer(new ParseUtil.LineSource.File(reader), new ParseUtil.ErrorLocator(name, 0), '#');
        try {
            BinaryElementType type = parseType(lexer);
            lexer.requireEndOfFile();
            return type;
        } finally {
            reader.close();
        }
    }

    public static final BinaryElementType parseType(ParseUtil.IndentableLexer lexer) throws IOException {
        int posBefore = lexer.getPos();
        String keyword = lexer.readDashedWord("type");
        StructureDescriptionParser.ParameterParser parameterParser = getFieldTypeParameterParser(keyword);
        BinaryElementType type;
        if (parameterParser == null) {
            try {
                type = BinaryElementType.MANAGER.get(keyword);
            } catch (ResourceManager.ResolutionError e) {
                lexer.errorAtPos(posBefore, "unknown type");
                throw new RuntimeException("bug detected");
            }
            lexer.passLogicalNewline();
        } else {
            type = parameterParser.parseParameters(lexer);
        }
        return type;
    }

    public static final Step parseThisSeek(ParseUtil.IndentableLexer lexer) {
        assert lexer.at('@');
        lexer.skipChar();
        int sign;
        if (lexer.at('+')) {
            lexer.skipChar();
            sign = +1;
        } else if (lexer.at('-')) {
            lexer.skipChar();
            sign = -1;
        } else {
            sign = 0;
        }
        int offset = lexer.readUnsignedInteger("offset");
        return sign == 0 ? new Step.LocalSeek(offset) : new Step.RelSeek(sign * offset);
    }

    private static final Map<String, StructureDescriptionParser.ParameterParser> KNOWN_FIELD_TYPES = new HashMap<String, StructureDescriptionParser.ParameterParser>();

    static final StructureDescriptionParser.ParameterParser getFieldTypeParameterParser(String name) {
        return KNOWN_FIELD_TYPES.get(name);
    }

    static {
        KNOWN_FIELD_TYPES.put("sliced-byte", new SlicedIntegerFieldParameterParser(BinaryElementType.BasicInteger.BYTE));
        KNOWN_FIELD_TYPES.put("sliced-lewyde", new SlicedIntegerFieldParameterParser(BinaryElementType.BasicInteger.LEWYDE));
        KNOWN_FIELD_TYPES.put("sliced-bewyde", new SlicedIntegerFieldParameterParser(BinaryElementType.BasicInteger.BEWYDE));

        KNOWN_FIELD_TYPES.put("padded-string", new ParameterParser() {
            @Override
            final BinaryElementType parseParameters(ParseUtil.IndentableLexer lexer) throws IOException {
                lexer.skipSpaces();
                int size = lexer.readUnsignedInteger("string length");
                lexer.skipSpaces();
                int beforePadding = lexer.getPos();
                int padding = lexer.readUnsignedInteger("char code");
                if (padding >= 0x100) {
                    lexer.errorAtPos(beforePadding, "value too high to be a char code");
                }
                lexer.passLogicalNewline();
                return new BinaryElementType.PaddedString(size, (byte) padding);
            }
        });
        
        KNOWN_FIELD_TYPES.put("struct", new ParameterParser() {
            @Override
            final BinaryElementType parseParameters(ParseUtil.IndentableLexer lexer) throws IOException {
                ArrayList<BinaryElementType.Struct.Step> steps = new ArrayList<BinaryElementType.Struct.Step>();
                lexer.passLogicalNewline();
                lexer.passIndent();
                
                while (!lexer.atDedent()) {
                    lexer.noIndent();
                    if (lexer.at('@')) {
                        Step seek = parseThisSeek(lexer);
                        steps.add(seek);
                        lexer.skipSpaces();
                    }
                    if (!(lexer.atEndOfLine() || lexer.atCommentChar())) {
                        ArrayList<BinaryElementType.Struct.Step> lineSteps = new ArrayList<BinaryElementType.Struct.Step>();
                        while (true) {
                            String fieldName = lexer.readDashedWord("field name");
                            lineSteps.add(new Step.Pass(fieldName, null));
                            lexer.skipSpaces();
                            if (!lexer.at(',')) {
                                break;
                            }
                            lexer.skipChar();
                            lexer.skipSpaces();
                            if (lexer.at('@')) {
                                Step seek = parseThisSeek(lexer);
                                lineSteps.add(seek);
                                lexer.skipSpaces();
                            }
                        }
                        lexer.pass(':');
                        lexer.skipSpaces();
                        BinaryElementType fieldType = parseType(lexer);
                        for (Step step : lineSteps) {
                            step.setType(fieldType);
                        }
                        steps.addAll(lineSteps);
                    } else {
                        lexer.passLogicalNewline();
                    }
                }
                lexer.discardDedent();
                return new BinaryElementType.Struct(steps.toArray(new BinaryElementType.Struct.Step[0]));
            }
        });
    }
}