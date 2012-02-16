package net.mirky.redis;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class ControlData {
    private ControlData() {
        // not a real constructor
    }

    public static final String[] loadStringArray(final String filename, int arraySize) throws NumberFormatException {
        String[] keywords = new String[arraySize];
        for (int i = 0; i < keywords.length; i++) {
            keywords[i] = null;
        }
        final BufferedReader reader = TextResource.getBufferedReader(filename);
        try {
            try {
                ParseUtil.IndentableLexer lexer = new ParseUtil.IndentableLexer(new ParseUtil.LineSource.File(reader), new ParseUtil.ErrorLocator(filename, 0), '#');
                while (!lexer.atEndOfFile()) {
                    lexer.noIndent();
                    int keyPos = lexer.getPos();
                    int key = lexer.readUnsignedInteger("key");
                    if (key < 0 || key >= arraySize) {
                        lexer.errorAtPos(keyPos, "key out of bounds");
                    }
                    if (keywords[key] != null) {
                        lexer.errorAtPos(keyPos, "duplicate key");
                    }
                    lexer.skipSpaces();
                    lexer.pass(':');
                    lexer.skipSpaces();
                    String value = lexer.readStringLiteral("value");
                    keywords[key] = value;
                    lexer.passLogicalNewline();
                }
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            throw new RuntimeException("I/O error reading resource " + filename, e);
        }
        return keywords;
    }

    public static final Map<String, ArrayList<String>> loadStringMultimap(String filename) {
        Map<String, ArrayList<String>> map = new HashMap<String, ArrayList<String>>();
        BufferedReader reader = TextResource.getBufferedReader(filename);
        try {
            try {
                ParseUtil.IndentableLexer lexer = new ParseUtil.IndentableLexer(new ParseUtil.LineSource.File(reader), new ParseUtil.ErrorLocator(filename, 0), '#');
                while (!lexer.atEndOfFile()) {
                    lexer.noIndent();
                    String key = lexer.readStringLiteral("key");
                    lexer.skipSpaces();
                    lexer.pass(':');
                    lexer.skipSpaces();
                    String value = lexer.readStringLiteral("value");
                    ArrayList<String> list = map.get(key);
                    if (list == null) {
                        list = new ArrayList<String>();
                        map.put(key, list);
                    }
                    list.add(value);
                    lexer.passLogicalNewline();
                }
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            throw new RuntimeException("I/O error reading resource " + filename, e);
        }
        return map;
    }
}
