package net.mirky.redis.analysers;

import java.io.PrintStream;

import net.mirky.redis.Format;
import net.mirky.redis.Analyser;
import net.mirky.redis.Hex;
import net.mirky.redis.ReconstructionDataCollector;

@Format.Options(".spf/decoding:decoding=zx-spectrum/first-char:unsigned-hex=0x20/height:positive-decimal=8/msb:scanline-direction=left")
public final class CelledFontAnalyser extends Analyser.Leaf {
    public enum ScanlineDirection {
        MSB_LEFT, MSB_RIGHT;

        final boolean getLeftBit(byte bits) {
            switch (this) {
                case MSB_LEFT:
                    return (bits & 0x80) != 0;
                case MSB_RIGHT:
                    return (bits & 0x01) != 0;
            }
            throw new RuntimeException("bug detected");
        }

        final byte shiftLeftwards(byte bits) {
            switch (this) {
                case MSB_LEFT:
                    return (byte) (bits << 1);
                case MSB_RIGHT:
                    return (byte) (bits >> 1);
            }
            throw new RuntimeException("bug detected");
        }
    }

    @Override
    protected final ReconstructionDataCollector dis(Format format, byte[] data, PrintStream port) {
        int firstCharcode = format.getIntegerOption("first-char");
        int charcellHeight = format.getIntegerOption("height");
        ScanlineDirection direction = (ScanlineDirection) ((Format.Option.SimpleOption) format.getOption("msb")).value;
        int baseOffset = 0;
        int leftmostCharcode = firstCharcode;
        while (true) {
            int glyphsOnThisRow;
            // some glyphs may be incomplete, though
            if (baseOffset >= data.length) {
                return null;
            } else if (data.length - baseOffset >= 8 * charcellHeight) {
                glyphsOnThisRow = 8;
            } else {
                glyphsOnThisRow = (data.length - baseOffset + charcellHeight - 1) / charcellHeight;
            }
            for (int row = 0; row < charcellHeight; row++) {
                for (int col = 0; col < glyphsOnThisRow; col++) {
                    int offset = baseOffset + (col * charcellHeight) + row;
                    if (offset >= data.length) {
                        break;
                    }
                    byte bits = data[offset];
                    for (int bitIndex = 0; bitIndex < 8; bitIndex++) {
                        port.print(direction.getLeftBit(bits) ? '#' : '.');
                        bits = direction.shiftLeftwards(bits);
                    }
                    port.print(' ');
                }
                port.println();
            }
            for (int col = 0; col < glyphsOnThisRow; col++) {
                port.print(formatCharcode(leftmostCharcode + col) + " ");
            }
            port.println();
            port.println();
            baseOffset += 8 * charcellHeight;
            leftmostCharcode += 8;
        }
    }

    /**
     * Format the charcode as a 8-character string. If the charcode fits
     * into a byte or wyde, a 0x prefix is added and the result is centered
     * with spaces; otherwise, it's just represented as a 8 hex digits.
     */
    private final String formatCharcode(int code) {
        if (code >= 0 && code <= 0xFF) {
            return ("  0x" + Hex.b(code) + "  ");
        } else if (code >= 0 && code <= 0xFFFF) {
            return (" 0x" + Hex.w(code) + " ");
        } else {
            return Hex.t(code);
        }
    }
}