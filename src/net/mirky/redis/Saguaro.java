package net.mirky.redis;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class Saguaro {
    private Saguaro() {
        // not a real constructor
    }

    public static final class LineParseError extends Exception {
        public final String line;

        public LineParseError(String msg, String line) {
            super(msg);
            this.line = line;
        }
    }

    // XXX: we're currently fully ignoring any possible indentation
    // XXX: and we're not ignoring empty lines and comments, either
    public static final String[] loadStringArray(String filename, int arraySize) throws NumberFormatException {
        String[] keywords = new String[arraySize];
        for (int i = 0; i < keywords.length; i++) {
            keywords[i] = null;
        }
        BufferedReader reader = TextResource.getBufferedReader(filename);
        try {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    ParseUtil.LineLexer lexer = new ParseUtil.LineLexer(line);
                    lexer.skipSpaces();
                    if (!lexer.atUnsignedInteger()) {
                        throw new LineParseError("wrong key type", line);
                    }
                    int key = lexer.parseUnsignedInteger();
                    lexer.skipSpaces();
                    if (!lexer.at(':')) {
                        throw new LineParseError("missing colon after key", line);
                    }
                    lexer.skipChar();
                    lexer.skipSpaces();
                    if (!lexer.atString()) {
                        throw new LineParseError("wrong value type", line);
                    }
                    String value = lexer.parseString();
                    lexer.skipSpaces();
                    if (!lexer.atEndOfLine()) {
                        throw new LineParseError("missing end of line", line);
                    }
                    if (key < 0 || key >= arraySize) {
                        throw new RuntimeException("key " + key + " out of bounds in " + filename);
                    }
                    if (keywords[key] != null) {
                        throw new RuntimeException("duplicate " + filename + " entry for " + key);
                    }
                    keywords[key] = value;
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

    // XXX: we're currently fully ignoring any possible indentation
    // XXX: and we're not ignoring empty lines and comments, either
    public static final Map<String, ArrayList<String>> loadStringMultimap(String filename) throws NumberFormatException {
        Map<String, ArrayList<String>> map = new HashMap<String, ArrayList<String>>();
        BufferedReader reader = TextResource.getBufferedReader(filename);
        String line;
        try {
            try {
                while ((line = reader.readLine()) != null) {
                    ParseUtil.LineLexer lexer = new ParseUtil.LineLexer(line);
                    lexer.skipSpaces();
                    if (!lexer.atString()) {
                        throw new RuntimeException("invalid " + filename + " line: " + line);
                    }
                    String key = lexer.parseString();
                    lexer.skipSpaces();
                    if (!lexer.at(':')) {
                        throw new RuntimeException("invalid " + filename + " line: " + line);
                    }
                    lexer.skipChar();
                    lexer.skipSpaces();
                    if (!lexer.atString()) {
                        throw new RuntimeException("invalid " + filename + " line: " + line);
                    }
                    String value = lexer.parseString();
                    lexer.skipSpaces();
                    if (!lexer.atEndOfLine()) {
                        throw new RuntimeException("invalid " + filename + " line: " + line);
                    }
                    ArrayList<String> list = map.get(key);
                    if (list == null) {
                        list = new ArrayList<String>();
                        map.put(key, list);
                    }
                    list.add(value);
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
