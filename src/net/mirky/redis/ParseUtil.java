package net.mirky.redis;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Vector;

import net.mirky.redis.ControlData.LineParseError;

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

        public LineLexer(String line) {
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

        public final String parseRestOfLine() {
            String result = line.substring(pos);
            pos = line.length();
            return result;
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
        private LineLexer lineLexer = null;
        private boolean eof = false;
        private final Vector<Integer> indentationStack = new Vector<Integer>();
        private int dent; // +1 for indent, negative for dedent, the absolute
                          // value indicates the count of remaining dedents

        public IndentationSensitiveLexer(LineSource lineSource, char commentChar) {
            this.lineSource = lineSource;
            this.commentChar = commentChar;
        }

        public final void advanceVertically() throws ControlData.LineParseError, IOException {
            do {
                String line = lineSource.getNextLine();
                if (line == null) {
                    lineLexer = null;
                    eof = true;
                    dent = -indentationStack.size();
                    indentationStack.clear();
                    return;
                }
                lineLexer = new LineLexer(line);
                lineLexer.skipSpaces();
            } while (lineLexer.atEndOfLine() || lineLexer.at(commentChar));
            int threshold = indentationStack.isEmpty() ? 0 : indentationStack.get(indentationStack.size() - 1)
                    .intValue();
            if (lineLexer.getPos() > threshold) {
                dent = 1;
                indentationStack.add(new Integer(lineLexer.getPos()));
            } else {
                dent = 0;
                while (lineLexer.getPos() < threshold) {
                    dent--;
                    indentationStack.remove(indentationStack.size() - 1);
                    threshold = indentationStack.isEmpty() ? 0 : indentationStack.get(indentationStack.size() - 1)
                            .intValue();
                }
                if (lineLexer.getPos() != threshold) {
                    complain("invalid dedent");
                }
            }
        }

        /**
         * Check whether we're at the end of file. Note that this means having
         * actually advanced vertically past the final interesting line in this
         * file *and* having zero dent. Standing at the end of the last line
         * does not count as standing at the end of the file.
         * 
         * @throws IOException
         * @throws LineParseError
         */
        public final boolean atEndOfFile() throws ControlData.LineParseError, IOException {
            ensureCurrentLine();
            return dent == 0 && eof;
        }

        /**
         * Check whether we're at the end of a physical line.
         * 
         * @throws IOException
         * @throws LineParseError
         */
        public final boolean atEndOfLine() throws ControlData.LineParseError, IOException {
            ensureCurrentLine();
            return dent == 0 && (eof || lineLexer.atEndOfLine());
        }

        public final boolean atIndent() {
            return dent > 0;
        }

        public final boolean atDedent() {
            return dent < 0;
        }

        public final boolean at(char c) throws ControlData.LineParseError, IOException {
            ensureCurrentLine();
            return dent == 0 && !eof && lineLexer.at(c);
        }

        public final boolean at(String s) throws ControlData.LineParseError, IOException {
            ensureCurrentLine();
            return dent == 0 && !eof && lineLexer.at(s);
        }

        public final boolean atAnyOf(String charset) throws ControlData.LineParseError, IOException {
            ensureCurrentLine();
            return dent == 0 && !eof && lineLexer.atAnyOf(charset);
        }
        
        // If any of the delimiters in {@code charset} is not found, pass until end of line.
        public final String passUntilDelimiter(String charset) throws LineParseError, IOException {
            ensureCurrentLine();
            return lineLexer.readUntilDelimiter(charset);
        }

        public final void skipThisIndent() {
            assert atIndent();
            dent--;
        }

        public final void skipThisDedent() {
            assert atDedent();
            dent++;
        }

        public final char getChar() throws ControlData.LineParseError, IOException {
            ensureCurrentLine();
            assert dent == 0 && !eof;
            return lineLexer.readChar();
        }
        
        public final void skipChar() throws ControlData.LineParseError, IOException {
            ensureCurrentLine();
            assert dent == 0 && !eof;
            lineLexer.skipChar();
        }

        public final boolean atUnsignedInteger() throws ControlData.LineParseError, IOException {
            ensureCurrentLine();
            return dent == 0 && !eof && lineLexer.atDigit();
        }

        /**
         * Parse the unsigned integer starting from the cursor. Programming
         * error if the cursor is not at an unsigned integer.
         * 
         * @throws LineParseError
         * @throws IOException
         */
        public final int parseThisUnsignedInteger() throws ControlData.LineParseError, IOException {
            ensureCurrentLine();
            assert dent == 0 && !eof;
            return lineLexer.parseThisUnsignedInteger();
        }
        
        /**
         * Skip horizontal whitespace. If there's any unprocessing indent or
         * dedent, it will be cleared.
         * 
         * @throws IOException
         * @throws LineParseError
         */
        public final void skipSpaces() throws ControlData.LineParseError, IOException {
            ensureCurrentLine();
            dent = 0;
            if (!eof) {
                lineLexer.skipSpaces();
            }
        }

        public final String parseThisString() throws ControlData.LineParseError, IOException {
            ensureCurrentLine();
            assert dent == 0 && !eof;
            return lineLexer.parseThisString();
        }

        public final boolean atWord() throws ControlData.LineParseError, IOException {
            ensureCurrentLine();
            return dent == 0 && !eof && lineLexer.atAlphanumeric();
        }
        
        public final String parseThisDashedWord() throws ControlData.LineParseError, IOException {
            ensureCurrentLine();
            assert dent == 0 && !eof;
            return lineLexer.parseThisDashedWord();
        }

        public final String peekThisDashedWord() throws ControlData.LineParseError, IOException {
            ensureCurrentLine();
            assert dent == 0 && !eof;
            return lineLexer.peekThisDashedWord();
        }

        public final String parseDashedWord(String significance) throws ControlData.LineParseError, IOException {
            if (!atWord()) {
                complain("expected " + significance + ", a word (dashes permitted)");
            }
            return parseThisDashedWord();
        }

        public final boolean atCommentChar() throws ControlData.LineParseError, IOException {
            ensureCurrentLine();
            return at(commentChar);
        }
        
        private final void ensureCurrentLine() throws ControlData.LineParseError, IOException {
            if (lineLexer == null && !eof) {
                advanceVertically();
            }
        }

        public final void complain(String message) throws ControlData.LineParseError {
            if (!eof) {
                throw new ControlData.LineParseError(lineSource.getLineLoc() + ':' + (lineLexer.getPos() + 1) + ": " + message, lineLexer.line);
            } else {
                throw new ControlData.LineParseError(lineSource.getLineLoc() + ": " + message, "");
            }
        }

        public final void noIndent() throws ControlData.LineParseError {
            if (atIndent()) {
                complain("unexpected indent");
            }
        }

        public final void pass(char c) throws ControlData.LineParseError, IOException {
            if (!at(c)) {
                complain("expected '" + c + "'");
            }
            skipChar();
        }

        public final boolean passOpt(char c) throws ControlData.LineParseError, IOException {
            boolean result = at(c);
            if (result) {
                skipChar();
            }
            return result;
        }

        /**
         * Parse an unsigned integer from the cursor onwards. If there isn't one
         * at the cursor, report an error and state the significance of the
         * missing unsined integer.
         * 
         * @throws LineParseError
         * @throws IOException
         */
        public final int parseUnsignedInteger(String significance) throws ControlData.LineParseError,
                IOException {
            if (!atUnsignedInteger()) {
                complain("expected " + significance + ", an unsigned integer");
            }
            return parseThisUnsignedInteger();
        }

        public final String parseString(String significance) throws ControlData.LineParseError,
                IOException {
            if (!at('"')) {
                complain("expected " + significance + ", a string");
            }
            return parseThisString();
        }

        public final void passNewline() throws ControlData.LineParseError, IOException {
            if (!(atEndOfLine() || atCommentChar())) {
                complain("expected end of line");
            }
            advanceVertically();
        }

        public final void passIndent() throws ControlData.LineParseError {
            if (!atIndent()) {
                complain("expected indent");
            }
            skipThisIndent();
        }

        public final String parseRestOfLine() throws ControlData.LineParseError, IOException {
            ensureCurrentLine();
            return lineLexer.parseRestOfLine();
        }

        public final void passDashedWord(String etalon) throws ControlData.LineParseError, IOException {
            if (!atWord() || !peekThisDashedWord().equals(etalon)) {
                complain("expected '" + etalon + "'");
            }
            parseThisDashedWord();
        }

        public final boolean passOptDashedWord(String etalon) throws ControlData.LineParseError, IOException {
            if (atWord() && peekThisDashedWord().equals(etalon)) {
                parseThisDashedWord();
                return true;
            } else {
                return false;
            }
        }

        public final char peekChar() throws LineParseError, IOException {
            assert !atEndOfLine();
            return lineLexer.peekChar();
        }

        public final void expectLogicalEndOfLine() throws ControlData.LineParseError,
                IOException {
            skipSpaces();
            if (!(atEndOfLine() || atCommentChar())) {
                complain("expected end of line");
            }
        }
    }

    public static abstract class LineSource {
        public abstract String getNextLine() throws IOException;

        /**
         * Return a string identifying the line-precision location of the line
         * last read. As per GNUCS, lines are numbered from 1.
         */
        public abstract String getLineLoc();
    }

    public static final class FileLineSource extends LineSource {
        private final BufferedReader reader;
        private final String filename;
        private int lineno;

        public FileLineSource(BufferedReader reader, String filename) {
            this.reader = reader;
            this.filename = filename;
            lineno = 0;
        }

        @Override
        public final String getNextLine() throws IOException {
            lineno++;
            return reader.readLine();
        }

        @Override
        public final String getLineLoc() {
            return filename + ':' + lineno;
        }
    }
}
