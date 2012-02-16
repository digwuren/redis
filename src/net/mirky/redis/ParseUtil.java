package net.mirky.redis;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Vector;

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
        private String line;
        private int pos;
        private final ErrorLocator errorLocator;

        public LineLexer(String line, ErrorLocator errorLocator) {
            this.errorLocator = errorLocator;
            reset(line);
        }

        public final void reset(String newLine) {
            this.line = newLine;
            pos = 0;
        }
        
        /**
         * Skip over zero or more space characters.
         */
        public final void skipSpaces() {
            while (at(' ')) {
                pos++;
            }
        }

        /**
         * Extract the character at cursor and move the cursor over it. Error if
         * the cursor has reached the end of line.
         */
        public final char readChar() {
            assert pos < line.length();
            return line.charAt(pos++);
        }

        /**
         * Extract the character at cursor but leave the cursor unmoved. Error
         * if the cursor has reached the end of line.
         */
        public final char peekChar() {
            assert pos < line.length();
            return line.charAt(pos);
        }

        /**
         * Advance the cursor by one character. Error if the cursor has reached
         * the end of line.
         */
        public final void skipChar() {
            assert pos < line.length();
            pos++;
        }

        /**
         * Check whether the cursor is at a character that equals the given
         * character.
         */
        public final boolean at(char etalon) {
            return pos < line.length() && line.charAt(pos) == etalon;
        }

        /**
         * Check whether the cursor is at a sequence of characters that equals
         * the given string.
         */
        public final boolean at(String etalon) {
            return pos + etalon.length() < line.length() && line.substring(pos, pos + etalon.length()).equals(etalon);
        }

        /**
         * Check whether the cursor is at a character of the given charset.
         * 
         * @param charset
         *            a {@link String} enumerating matching characters
         */
        public final boolean atAnyOf(String charset) {
            return pos < line.length() && charset.indexOf(line.charAt(pos)) != -1;
        }

        /**
         * Check whether the cursor is at a decimal digit.
         */
        public final boolean atDigit() {
            return pos < line.length() && isDigit(line.charAt(pos));
        }

        /**
         * Check whether the cursor is at an ASCII standard alphanumeric character.
         */
        public final boolean atAlphanumeric() {
            return pos < line.length() && isAlphanumeric(line.charAt(pos));
        }

        /**
         * Check whether the cursor is at the end of line.
         */
        public final boolean atEndOfLine() {
            return pos >= line.length();
        }

        public final void pass(char c) {
            if (!at(c)) {
                error("expected '" + c + "'");
            }
            skipChar();
        }

        /**
         * If the cursor is at a character matching the given etalon, pass it
         * and return {@code true}; otherwise retain the cursor's position and
         * return {@code false}.
         */
        public final boolean passOpt(char c) {
            boolean result = at(c);
            if (result) {
                skipChar();
            }
            return result;
        }

        /**
         * Read and return the rest of the line. Leave cursor at the end of the
         * line.
         */
        public final String readRestOfLine() {
            String result = line.substring(pos);
            pos = line.length();
            return result;
        }

        /**
         * Read characters until a delimiter listed in {@code charset} is seen,
         * or the line ends, and return them as a {@link String}. Leave the
         * cursor at the delimiter or at the end of line. May return an empty
         * string, if the cursor is already at a listed delimiter
         */
        public final String readUntilDelimiter(String charset) {
            int endPos = pos;
            while (endPos < line.length() && charset.indexOf(line.charAt(endPos)) == -1) {
                endPos++;
            }
            String result = line.substring(pos, endPos);
            pos = endPos;
            return result;
        }

        /**
         * Parse the unsigned integer starting from the cursor. Programming
         * error if the cursor is not at an unsigned integer.
         * 
         * @throws LineParseError
         * @throws IOException
         */
        public final int parseThisUnsignedInteger() throws NumberFormatException {
            assert atDigit();
            return ParseUtil.parseUnsignedInteger(parseThisWord());
        }

        public final String parseThisWord() {
            assert atAlphanumeric();
            int begin = pos;
            while (atAlphanumeric()) {
                pos++;
            }
            return line.substring(begin, pos);
        }

        public final String parseThisDashedWord() {
            assert atAlphanumeric();
            int begin = pos;
            while (atAlphanumeric() || (pos != begin && at('-') && pos + 1 < line.length() && isAlphanumeric(line.charAt(pos + 1)))) {
                pos++;
            }
            return line.substring(begin, pos);
        }

        public final String peekThisDashedWord() {
            int begin = pos;
            String word = parseThisDashedWord();
            pos = begin;
            return word;
        }

        public final String parseThisString() {
            assert at('"');
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
                    // XXX: currently, \ is a pure escape character; there are
                    // no specials
                    sb.append(c);
                } else {
                    sb.append(line.charAt(cur));
                }
            }
            throw new RuntimeException("string not terminated");
        }

        public final void error(String message) {
            errorAtCol(pos + 1, message);
        }

        public final void errorAtCol(int colno, String message) {
            errorLocator.error(colno, message);
        }

        public final int getPos() {
            return pos;
        }

        public static final boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        public static final boolean isLetter(char c) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
        }

        public static final boolean isAlphanumeric(char c) {
            return isLetter(c) || isDigit(c);
        }
    }

    public static final class IndentationSensitiveLexer {
        private final LineSource lineSource;
        private final char commentChar;
        public final LineLexer hor;
        private boolean eof = false;
        private final Vector<Integer> indentationStack = new Vector<Integer>();
        private int dent; // +1 for indent, negative for dedent, the absolute
                          // value indicates the count of remaining dedents

        public IndentationSensitiveLexer(LineSource lineSource, ErrorLocator errorLocator, char commentChar) throws IOException {
            this.lineSource = lineSource;
            this.commentChar = commentChar;
            hor = new LineLexer(null, errorLocator);
            advanceVertically();
        }

        /**
         * Discard the current line and move to the next line that is not all
         * space and whose first non-space character is not {@code commentChar}.
         * Update {@link #indentationStack} and set {@link #dent}. Leave the
         * cursor at the new line's first non-space character.
         * 
         * @throws IOException
         *             if line acquisition fails due to an I/O error
         */
        public final void advanceVertically() throws IOException {
            do {
                String line = lineSource.getNextLine();
                if (line == null) {
                    hor.reset("");
                    eof = true;
                    dent = -indentationStack.size();
                    indentationStack.clear();
                    return;
                }
                hor.errorLocator.nextLine();
                hor.reset(line);
                hor.skipSpaces();
            } while (hor.atEndOfLine() || hor.at(commentChar));
            int threshold = indentationStack.isEmpty() ? 0 : indentationStack.get(indentationStack.size() - 1)
                    .intValue();
            if (hor.getPos() > threshold) {
                dent = 1;
                indentationStack.add(new Integer(hor.getPos()));
            } else {
                dent = 0;
                while (hor.getPos() < threshold) {
                    dent--;
                    indentationStack.remove(indentationStack.size() - 1);
                    threshold = indentationStack.isEmpty() ? 0 : indentationStack.get(indentationStack.size() - 1)
                            .intValue();
                }
                if (hor.getPos() != threshold) {
                    error("invalid dedent");
                }
            }
        }

        /**
         * Check whether we're at the end of file. Note that this means having
         * actually advanced vertically past the final interesting line in this
         * file *and* having zero dent. Standing at the end of the last line
         * does not count as standing at the end of the file.
         */
        public final boolean atEndOfFile() {
            return dent == 0 && eof;
        }

        /**
         * Check whether the current line contains a non-discarded indentation.
         */
        public final boolean atIndent() {
            return dent > 0;
        }

        /**
         * Check whether the current line contains a non-discarded dedentation.
         */
        public final boolean atDedent() {
            return dent < 0;
        }

        /**
         * Discard one indentation level. Error if there is no non-discarded
         * indentation on this line.
         */
        public final void discardIndent() {
            assert atIndent();
            dent--;
        }

        /**
         * Discard one dedentation level. Error if there is no non-discarded
         * dedentation on this line.
         */
        public final void discardDedent() {
            assert atDedent();
            dent++;
        }

        public final String parseDashedWord(String significance) {
            if (!hor.atAlphanumeric()) {
                error("expected " + significance + ", a word (dashes permitted)");
            }
            return hor.parseThisDashedWord();
        }

        public final boolean atCommentChar() {
            return hor.at(commentChar);
        }
        
        @Deprecated // in favour of {@link #error(String)}
        public final void complain(String message) {
            error(message);
        }
        
        public final void error(String message) {
            hor.errorAtCol(!eof ? hor.getPos() + 1 : 0, message);
        }

        public final void noIndent() {
            if (atIndent()) {
                complain("unexpected indent");
            }
        }

        /**
         * Parse an unsigned integer from the cursor onwards. If there isn't one
         * at the cursor, report an error and state the significance of the
         * missing unsined integer.
         */
        public final int parseUnsignedInteger(String significance) {
            if (!hor.atDigit()) {
                error("expected " + significance + ", an unsigned integer");
            }
            return hor.parseThisUnsignedInteger();
        }

        public final String parseString(String significance) {
            if (!hor.at('"')) {
                error("expected " + significance + ", a string");
            }
            return hor.parseThisString();
        }

        public final void passNewline() throws IOException {
            if (!(hor.atEndOfLine() || atCommentChar())) {
                complain("expected end of line");
            }
            advanceVertically();
        }

        public final void expectLogicalEndOfLine() {
            hor.skipSpaces();
            if (!(hor.atEndOfLine() || atCommentChar())) {
                complain("expected end of line");
            }
        }

        public final void passIndent() {
            if (!atIndent()) {
                complain("expected indent");
            }
            discardIndent();
        }

        public final void passDashedWord(String etalon) {
            if (!hor.atAlphanumeric() || !hor.peekThisDashedWord().equals(etalon)) {
                complain("expected '" + etalon + "'");
            }
            hor.parseThisDashedWord();
        }

        public final boolean passOptDashedWord(String etalon) {
            if (hor.atAlphanumeric() && hor.peekThisDashedWord().equals(etalon)) {
                hor.parseThisDashedWord();
                return true;
            } else {
                return false;
            }
        }
    }

    public static abstract class LineSource {
        public abstract String getNextLine() throws IOException;
    }

    public static final class FileLineSource extends LineSource {
        private final BufferedReader reader;
        private int lineno;

        public FileLineSource(BufferedReader reader) {
            this.reader = reader;
            lineno = 0;
        }

        @Override
        public final String getNextLine() throws IOException {
            lineno++;
            return reader.readLine();
        }
    }
    
    public static final class ErrorLocator {
        public final String filename;
        public int lineno;
        
        public ErrorLocator(String filename, int lineno) {
            this.filename = filename;
            this.lineno = lineno;
        }

        public final void nextLine() {
            lineno++;
        }

        /**
         * Report an error according to the tracked line and, if {@code colno}
         * is non-zero, the given column. Note that columns are numbered from 1
         * unlike positions.
         */
        public final void error(int colno, String message) {
            StringBuilder sb = new StringBuilder();
            sb.append(filename);
            sb.append(':');
            sb.append(lineno);
            if (colno != 0) {
                sb.append('.');
                sb.append(colno);
            }
            sb.append(": ");
            sb.append(message);
            throw new ControlDataError(sb.toString());
        }
    }
    
    public static final class ControlDataError extends RuntimeException {
        public ControlDataError(String message) {
            super(message);
        }
    }
}
