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

    public static final class LineParseError extends Exception {
        public final String line;

        public LineParseError(String msg, String line) {
            super(msg);
            this.line = line;
        }
    }

    public static final String[] loadStringArray(final String filename, int arraySize) throws NumberFormatException {
        String[] keywords = new String[arraySize];
        for (int i = 0; i < keywords.length; i++) {
            keywords[i] = null;
        }
        final BufferedReader reader = TextResource.getBufferedReader(filename);
        try {
            try {
                ParseUtil.IndentationSensitiveLexer lexer = new ParseUtil.IndentationSensitiveLexer(new ParseUtil.FileLineSource(reader, filename), '#');
                while (!lexer.atEndOfFile()) {
                    if (lexer.atIndent()) {
                        lexer.complain("unexpected indentation");
                    }
                    if (lexer.atDedent()) {
                        lexer.complain("unexpected dedentation");
                    }
                    if (!lexer.atUnsignedInteger()) {
                        lexer.complain("key expected");
                    }
                    int key = lexer.parseThisUnsignedInteger();
                    lexer.skipSpaces();
                    if (!lexer.at(':')) {
                        lexer.complain("missing colon after key");
                    }
                    lexer.skipChar();
                    lexer.skipSpaces();
                    if (!lexer.at('"')) {
                        lexer.complain("wrong value type");
                    }
                    String value = lexer.parseThisString();
                    lexer.expectLogicalEndOfLine();
                    if (key < 0 || key >= arraySize) {
                        throw new RuntimeException("key " + key + " out of bounds in " + filename);
                    }
                    if (keywords[key] != null) {
                        throw new RuntimeException("duplicate " + filename + " entry for " + key);
                    }
                    keywords[key] = value;
                    lexer.advanceVertically();
                }
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            throw new RuntimeException("I/O error reading resource " + filename, e);
        } catch (LineParseError e) {
            throw new RuntimeException("invalid " + filename + " line: " + e.line, e);
        }
        return keywords;
    }

    public static final Map<String, ArrayList<String>> loadStringMultimap(String filename) {
        Map<String, ArrayList<String>> map = new HashMap<String, ArrayList<String>>();
        BufferedReader reader = TextResource.getBufferedReader(filename);
        try {
            try {
                ParseUtil.IndentationSensitiveLexer lexer = new ParseUtil.IndentationSensitiveLexer(new ParseUtil.FileLineSource(reader, filename), '#');
                while (!lexer.atEndOfFile()) {
                    if (lexer.atIndent()) {
                        lexer.complain("unexpected indentation");
                    }
                    if (lexer.atDedent()) {
                        lexer.complain("unexpected dedentation");
                    }
                    if (!lexer.at('"')) {
                        lexer.complain("key expected");
                    }
                    String key = lexer.parseThisString();
                    lexer.skipSpaces();
                    if (!lexer.at(':')) {
                        lexer.complain("missing colon after key");
                    }
                    lexer.skipChar();
                    lexer.skipSpaces();
                    if (!lexer.at('"')) {
                        lexer.complain("missing value");
                    }
                    String value = lexer.parseThisString();
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
        } catch (ControlData.LineParseError e) {
            throw new RuntimeException("parse error reading resource " + filename, e);
        }
        return map;
    }
}
