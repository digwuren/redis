package net.mirky.redis.analysers;

import java.io.PrintStream;

import net.mirky.redis.Analyser;
import net.mirky.redis.ControlData;
import net.mirky.redis.Cursor;
import net.mirky.redis.Format;
import net.mirky.redis.Hex;
import net.mirky.redis.ImageError;
import net.mirky.redis.ChromaticLineBuilder;

@Format.Options("zxs-basic-code/decoding:decoding=zx-spectrum/autostart:unsigned-decimal=0")
public final class ZXSBasicProgramCodeAnalyser extends Analyser.Leaf.PossiblyPartial {
    @Override
    protected final int disPartially(Format format, byte[] data, PrintStream port) throws Format.UnknownOption, RuntimeException {
        Cursor cursor = new Cursor.ByteArrayCursor(data, 0);
        int lastLineNumber = 0; // so we can check for out-of-order lines
        int autostart = format.getIntegerOption("autostart");
        boolean autostartLineSeen = false;
        try {
            while (true) {
                if (cursor.tell() == data.length) {
                    break;
                }
                /*
                 * We'll use backticks to delimit raw text from decoded text. ZX
                 * Spectrum's charset does not contains backticks, so there's no
                 * confusion that might raise if we used, say, ASCII brokets.
                 */
                ChromaticLineBuilder clb = new ChromaticLineBuilder();
                int lineNumber = cursor.getUnsignedBewyde(0); // sic, line number is big endian
                int lineSize = cursor.getUnsignedLewyde(2);
                if (!cursor.probe(4 + lineSize)) {
                    port.println("! line's declared size exceeds bytes available in input");
                    break;
                }
                if (lineNumber == 0 || lineNumber > 9999) {
                    port.println("! invalid line number");
                }
                if (lineNumber <= lastLineNumber) {
                    port.println("! line number sequence should strictly grow but doesn't here");
                }
                lastLineNumber = lineNumber;
                if (lineNumber == autostart) {
                    port.println("* autostart here");
                    autostartLineSeen = true;
                }
                clb.append(Integer.toString(lineNumber));
                clb.append(' ');
                int i = 4;
                int digitSequenceStart = -1;
                boolean lineProperlyTerminated = false;
                while (i < 4 + lineSize) {
                    int c = cursor.getUnsignedByte(i);
                    if (c == 0x0D && i == 4 + lineSize - 1) {
                        lineProperlyTerminated = true;
                        break;
                    } else if (c >= 0x12 && c <= 0x14 && (cursor.getUnsignedByte(i + 1) == 0x00 || cursor.getUnsignedByte(i + 1) == 0x01)) {
                        boolean newState = cursor.getUnsignedByte(i + 1) != 0;
                        clb.changeMode(ChromaticLineBuilder.CONTROL);
                        if (!newState) {
                            clb.append('/');
                        }
                        switch (c) {
                            case 0x12:
                                clb.append("flash");
                                break;
                            case 0x13:
                                clb.append("bright");
                                break;
                            case 0x14:
                                clb.append("inverse");
                                break;
                        }
                        i += 2;
                    } else if (c == 0x0E) {
                        // ZX Spectrum's BASIC stores a human-readable number followed by a preparsed
                        // machine-readable number.  Usually, they match.  In some copy protection
                        // schemes, the human-readable number can be deliberately misleading, so
                        // we want to compare the two numbers against each other and point out any
                        // discrepancies.
                        ZXSBasicProgramAnalyser.ZXSpectrumNumber binary = new ZXSBasicProgramAnalyser.ZXSpectrumNumber(cursor.getBytes(i + 1, 5));
                        boolean skip = false; // we'll skip the binary part if it matches the text part
                        if (digitSequenceStart != -1) {
                            // 32-bit calculations, we can detect overflow easily
                            // because ZX Spectrum's integer calculations were 16-bit
                            int number = 0;
                            boolean overflow = false;
                            for (int j = digitSequenceStart; j < i; j++) {
                                int d = cursor.getUnsignedByte(j);
                                assert d >= 0x30 && d <= 0x39;
                                number *= 10;
                                number += d - 0x30;
                                overflow |= number > 0xFFFF;
                            }
                            // FIXME: check the binary and decimal number's match for floats, too
                            if (!overflow && binary.is(number)) {
                                skip = true;
                            }
                        }
                        if (!skip) {
                            // If we're not skipping, it's because there is no matching text number before the 0x0E.  Why?
                            clb.changeMode(ChromaticLineBuilder.ZXSB_TOKEN);
                            if (digitSequenceStart != -1) {
                                // If because there was a different text number:
                                clb.append("actually ");
                            } else {
                                // If because there was no text number:
                                clb.append("number ");
                            }
                            clb.append(binary.prepareForDisplay());
                        }
                        digitSequenceStart = -1;
                        i += 6;
                    } else {
                        String keyword = ZXSBasicProgramCodeAnalyser.KEYWORDS[c];
                        if (keyword != null) {
                            clb.changeMode(ChromaticLineBuilder.ZXSB_TOKEN);
                            clb.append(keyword);
                            digitSequenceStart = -1;
                        } else {
                            if (c >= '0' && c <= '9') {
                                if (digitSequenceStart == -1) {
                                    digitSequenceStart = i;
                                }
                            } else {
                                digitSequenceStart = -1;
                            }
                            char decodedChar = format.getDecoding().decode((byte) c);
                            if (decodedChar != 0) {
                                clb.changeMode(ChromaticLineBuilder.PLAIN);
                                clb.append(decodedChar);
                            } else {
                                clb.changeMode(ChromaticLineBuilder.CONTROL);
                                clb.append(Hex.b(c));
                            }
                        }
                        i++;
                    }
                }
                if (!lineProperlyTerminated) {
                    clb.changeMode(ChromaticLineBuilder.CONTROL);
                    clb.append("noeol");
                }
                clb.terpri(port);
                cursor.advance(4 + lineSize);
            }
            if (autostart != 32768 && !autostartLineSeen) {
                if (cursor.tell() == data.length) {
                    port.println("! autostart declared but does not match any program line");
                } else {
                    port.println("! autostart declared but does not match any program line (so far)");
                }
            }
            return cursor.tell();
        } catch (ImageError e) {
            return cursor.tell();
        }
    }

    private static final String[] KEYWORDS = ControlData.loadStringArray("zxsbaskw.tab", 256);
}