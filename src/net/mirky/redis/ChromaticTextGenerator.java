package net.mirky.redis;

import java.io.PrintStream;

public final class ChromaticTextGenerator {
    private final BichromaticStringBuilder bsb;
    private final BichromaticStringBuilder.DelimitedMode del;
    private final PrintStream port;

    public ChromaticTextGenerator(char tokenListStart, char tokenListEnd, PrintStream port) {
        bsb = new BichromaticStringBuilder("34;1"); // light blue
        del = new BichromaticStringBuilder.DelimitedMode(bsb, tokenListStart, ' ', tokenListEnd);
        this.port = port;
    }

    public final void appendLeftPadded(String s, char padding, int minWidth) {
        for (int i = minWidth - s.length(); i > 0; i--) {
            bsb.sb.append(padding);
        }
        bsb.sb.append(s);
    }

    public final void appendInteger(int i) {
        del.delimitForPlain();
        bsb.sb.append(i);
    }

    public final void appendChar(char c) {
        del.delimitForPlain();
        bsb.sb.append(c);
    }

    public final void appendToken(String s) {
        del.delimitForColour();
        bsb.sb.append(s);
    }

    public final void appendHexByteToken(byte b) {
        appendToken(Hex.b(b));
    }

    public final void terpri() {
        del.delimitForPlain();
        bsb.printLine(port);
        bsb.clear();
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
            del.delimitForPlain();
            // FIXME: instead of turning bright on and off again, we should
            // treat it as just another mode
            if (bright) {
                bsb.sb.append("\u001B[33;1m"); // yellow
            }
            bsb.sb.append(c);
            if (bright) {
                bsb.sb.append("\u001B[0m"); // plain
            }
        } else {
            // Note that if the byte does not seem to have a graphical
            // representation, we'll display the original value, rather than the
            // one with high bit stripped.
            appendHexByteToken(b);
        }
    }
}