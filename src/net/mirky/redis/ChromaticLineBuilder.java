package net.mirky.redis;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

public final class ChromaticLineBuilder {
    public static final int PLAIN = 0;
    public static final int CONTROL = 1; // broketed light blue, for invisible/unprintable
    public static final int BRIGHT = 2; // non-delim yellow, for /high-bit=bright
    public static final int ZXSB_TOKEN = 3; // backticked yellow for ZXS BASIC tokens
    public static final int EXTRACTED = 4; // dark blue, for extracted parts of a hexdump
    
    private final StringBuilder sb;
    private int currentMode; // 0 being plain
    private final Mode[] modes;

    // Both ANSI colours ("34" for blue, "33;1" for yellow) and xterm's
    // 256-colour extensions (such as "38;5;208" for orange #D78700) are
    // supported.
    public ChromaticLineBuilder() {
        sb = new StringBuilder();
        currentMode = 0;
        modes = new Mode[] {
                new Mode("0", '\0', '\0', '\0'),
                new Mode("1;34", '<', ' ', '>'),
                new Mode("1;33", '\0', '\0', '\0'),
                new Mode("1;33", '`', ' ', '`'),
                // Because mode switches can be performed directly, we'll have
                // to explicitly reset the brightness in non-bright foregrounds.
                // \e[0m is the easiest way to do this.
                new Mode("0;33", '\0', '\0', '\0'),
        };
    }

    public final int getCurrentMode() {
        return currentMode;
    }

    public final void changeMode(int newMode) {
        assert newMode >= 0 && newMode < modes.length;
        if (newMode == currentMode) {
            appendDelim(modes[currentMode].mid);
        } else {
            appendDelim(modes[currentMode].tail);
            currentMode = newMode;
            sb.append("\u001B[");
            sb.append(modes[currentMode].colorSeqBody);
            sb.append('m');
            appendDelim(modes[currentMode].lead);
        }
    }

    public final void append(String s) {
        sb.append(s);
    }

    public final void append(char c) {
        sb.append(c);
    }
    
    public final void appendLeftPadded(String s, char padding, int minWidth) {
        for (int i = minWidth - s.length(); i > 0; i--) {
            append(padding);
        }
        append(s);
    }

    public final void processInputByte(byte b, HighBitInterpretation hbi, Decoding decoding) {
        boolean bright = false;
        byte code = 0;
        switch (hbi) {
            case KEEP:
                code = b;
                break;
            case DISCARD:
                code = (byte) (b & 0x7F);
                break;
            case BRIGHT:
                bright = (b & 0x80) != 0;
                code = (byte) (b & 0x7F);
                break;
        }
        char c = decoding.decode(code);
        if (c != 0) {
            changeMode(bright ? BRIGHT : PLAIN);
            append(c);
        } else {
            // Note that if the byte does not seem to have a graphical
            // representation, we'll display the original value, rather than the
            // one with high bit stripped.
            changeMode(CONTROL);
            append(Hex.b(b));
        }
    }
    
    // Skips NUL delims so NUL can be used to indicate no delimiter.
    private final void appendDelim(char c) {
        if (c != '\0') {
            append(c);
        }
    }
    
    public final void clear() {
        sb.setLength(0);
        currentMode = PLAIN;
    }

    /**
     * End the current mode if non-plain and print the whole accumulated string
     * out to {@code port}. Then, reset the {@link ChromaticLineBuilder}.
     */
    public final void terpri(PrintStream port) {
        changeMode(PLAIN);
        try {
            port.write(sb.toString().getBytes("utf-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("utf-8 not supported???", e);
        } catch (IOException e) {
            throw new RuntimeException("I/O error", e);
        }
        port.println();
        clear();
    }

    private static final class Mode {
        public final String colorSeqBody;
        public final char lead;
        public final char mid;
        public final char tail;

        public Mode(String colorSeqBody, char lead, char mid, char tail) {
            this.colorSeqBody = colorSeqBody;
            this.lead = lead;
            this.mid = mid;
            this.tail = tail;
        }
    }
}