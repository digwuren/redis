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

    public static final class LineLexer {
        private final String line;
        private int pos;
    
        public LineLexer(String line) {
            this.line = line;
            pos = 0;
        }
        
        public final void skipSpaces() {
            while (pos < line.length() && line.charAt(pos) == ' ') {
                pos++;
            }
        }
        
        public final void skipChar() {
            if (pos < line.length()) {
                pos++;
            }
        }
        
        public final boolean atUnsignedInteger() {
            if (pos >= line.length()) {
                return false;
            }
            char c = line.charAt(pos);
            return c >= '0' && c <= '9';
        }
        
        public final boolean atString() {
            return at('"');
        }
    
        public final String parseString() {
            assert atString();
            StringBuilder sb = new StringBuilder();
            for (int cur = pos + 1; cur < line.length(); cur++) {
                if (line.charAt(cur) == '"') {
                    pos = cur + 1;
                    return sb.toString();
                }
                if (line.charAt(cur) == '\\') {
                    cur++;
                    if (cur >= line.length()) {
                        break; // and complain about termination
                    }
                    char c = line.charAt(cur);
                    // XXX: currently, \ is a pure escape character; there are no specials
                    sb.append(c);
                } else {
                    sb.append(line.charAt(cur));
                }
            }
            throw new RuntimeException("string not terminated");
        }
        
        public final boolean at(char etalon) {
            return pos < line.length() && line.charAt(pos) == etalon;
        }
        
        public final boolean atEndOfLine() {
            return pos >= line.length();
        }
    
        public final String parseWord() {
            int begin = pos;
            while (pos < line.length() && isWordChar(line.charAt(pos))) {
                pos++;
            }
            return line.substring(begin, pos);
        }
    
        public final int parseUnsignedInteger() throws NumberFormatException {
            assert atUnsignedInteger();
            return ParseUtil.parseUnsignedInteger(parseWord());
        }

        public static final boolean isWordChar(char c) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
        }
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
                    Saguaro.LineLexer lexer = new LineLexer(line);
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
                    Saguaro.LineLexer lexer = new LineLexer(line);
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
