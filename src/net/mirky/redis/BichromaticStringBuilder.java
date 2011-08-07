package net.mirky.redis;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

public final class BichromaticStringBuilder {
    public final StringBuilder sb;
    private boolean colouring;
    private final String colour;

    // Both ANSI colours ("34" for blue, "33;1" for yellow) and xterm's
    // 256-colour extensions
    // ("38;5;208" for orange #D78700) are supported.
    public BichromaticStringBuilder(String colour) {
        sb = new StringBuilder();
        colouring = false;
        this.colour = colour;
    }

    public final void clear() {
        sb.setLength(0);
        colouring = false;
    }

    /**
     * Switch to colouring mode. If already in colouring mode, do nothing.
     */
    public final void beginColour() {
        if (!colouring) {
            sb.append((char) 0x1B);
            sb.append('[');
            sb.append(colour);
            sb.append('m');
            colouring = true;
        }
    }

    /**
     * Switch to plain mode. If already in plain mode, do nothing.
     */
    public final void endColour() {
        if (colouring) {
            sb.append((char) 0x1B);
            sb.append("[0m");
            colouring = false;
        }
    }

    public final void printLine(PrintStream port) {
        try {
            port.write(sb.toString().getBytes("utf-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("utf-8 not supported???", e);
        } catch (IOException e) {
            throw new RuntimeException("I/O error", e);
        }
        port.println();
    }

    public final boolean inColouredMode() {
        return colouring;
    }

    public static final class DelimitedMode {
        private final BichromaticStringBuilder bsb;
        private final char startDelimiter;
        private final char middleDelimiter;
        private final char endDelimiter;

        public DelimitedMode(BichromaticStringBuilder bsb, char startDelimiter, char middleDelimiter, char endDelimiter) {
            this.bsb = bsb;
            this.startDelimiter = startDelimiter;
            this.middleDelimiter = middleDelimiter;
            this.endDelimiter = endDelimiter;
        }

        public final void delimitForColour() {
            if (!bsb.inColouredMode()) {
                bsb.beginColour();
                bsb.sb.append(startDelimiter);
            } else {
                bsb.sb.append(middleDelimiter);
            }
        }

        public final void delimitForPlain() {
            if (bsb.inColouredMode()) {
                bsb.sb.append(endDelimiter);
                bsb.endColour();
            }
        }
    }
}