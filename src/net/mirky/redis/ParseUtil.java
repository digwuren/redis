package net.mirky.redis;

public final class ParseUtil {
    private ParseUtil() {
        // not a real constructor
    }

    /**
     * Parses a given string as an unsigned integer; decimal if unprefixed,
     * hexadecimal if with 0x prefix.
     * 
     * @param s
     *            pretrimmed string to be parsed
     * @return resulting integer
     * @throws NumberFormatException
     *             if the input is not a valid integer or does not fit into
     *             Java's {@code int}
     */
    public static final int parseUnsignedInteger(String s) throws NumberFormatException {
        if (s.length() >= 3 && s.toLowerCase().startsWith("0x")) {
            return Integer.parseInt(s.substring(2), 16);
        } else {
            return Integer.parseInt(s);
        }
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
}
