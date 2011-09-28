package net.mirky.redis;

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
            int begin = pos + 1;
            for (int cur = begin; cur < line.length(); cur++) {
                if (line.charAt(cur) == '"') {
                    pos = cur + 1;
                    return line.substring(begin, cur);
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
            String word = parseWord().toLowerCase();
            if (word.length() >= 3 && word.startsWith("0x")) {
                return Integer.parseInt(word.substring(2), 16);
            } else {
                return Integer.parseInt(word);
            }
        }
    
        public static final boolean isWordChar(char c) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
        }
    }

    // XXX: we're currently fully ignoring any possible indentation
    // XXX: and we're not ignoring empty lines and comments, either
    public static final String[] loadStringArray(String filename, int arraySize) throws NumberFormatException {
        String[] keywords = new String[arraySize];
        for (int i = 0; i < keywords.length; i++) {
            keywords[i] = null;
        }
        for (String line : new TextResource(filename)) {
            Saguaro.LineLexer lexer = new LineLexer(line);
            lexer.skipSpaces();
            if (!lexer.atUnsignedInteger()) {
                throw new RuntimeException("invalid " + filename + " line: " + line);
            }
            int key = lexer.parseUnsignedInteger();
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
            if (key < 0 || key >= arraySize) {
                throw new RuntimeException("key " + key + " out of bounds in " + filename);
            }
            if (keywords[key] != null) {
                throw new RuntimeException("duplicate " + filename + " entry for " + key);
            }
            keywords[key] = value;
        }
        return keywords;
    }
}
