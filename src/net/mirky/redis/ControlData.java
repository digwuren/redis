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
                ParseUtil.IndentationSensitiveLexer lexer = new ParseUtil.IndentationSensitiveLexer(new ParseUtil.FileLineSource(reader), new ParseUtil.ErrorLocator(filename, 0), '#');
                while (!lexer.atEndOfFile()) {
                    lexer.noIndent();
                    int keyPos = lexer.hor.getPos();
                    int key = lexer.parseUnsignedInteger("key");
                    if (key < 0 || key >= arraySize) {
                        lexer.hor.errorAtPos(keyPos, "key out of bounds");
                    }
                    if (keywords[key] != null) {
                        lexer.hor.errorAtPos(keyPos, "duplicate key");
                    }
                    lexer.hor.skipSpaces();
                    lexer.hor.pass(':');
                    lexer.hor.skipSpaces();
                    String value = lexer.parseString("value");
                    lexer.expectLogicalEndOfLine();
                    keywords[key] = value;
                    lexer.advanceVertically();
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
                ParseUtil.IndentationSensitiveLexer lexer = new ParseUtil.IndentationSensitiveLexer(new ParseUtil.FileLineSource(reader), new ParseUtil.ErrorLocator(filename, 0), '#');
                while (!lexer.atEndOfFile()) {
                    lexer.noIndent();
                    String key = lexer.parseString("key");
                    lexer.hor.skipSpaces();
                    lexer.hor.pass(':');
                    lexer.hor.skipSpaces();
                    String value = lexer.parseString("value");
                    lexer.expectLogicalEndOfLine();
                    ArrayList<String> list = map.get(key);
                    if (list == null) {
                        list = new ArrayList<String>();
                        map.put(key, list);
                    }
                    list.add(value);
                    lexer.advanceVertically();
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
