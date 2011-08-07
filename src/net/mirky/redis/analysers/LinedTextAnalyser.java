package net.mirky.redis.analysers;

import java.io.PrintStream;

import net.mirky.redis.Analyser;
import net.mirky.redis.BichromaticStringBuilder;
import net.mirky.redis.Decoding;
import net.mirky.redis.Format;
import net.mirky.redis.Format.UnknownOption;
import net.mirky.redis.Hex;
import net.mirky.redis.ReconstructionDataCollector;

@Format.Options("lines/decoding:decoding=ascii/width!:positive-decimal=64")
public final class LinedTextAnalyser extends Analyser.Leaf {
    @Override
    protected final ReconstructionDataCollector dis(Format format, byte[] data, PrintStream port) throws UnknownOption {
        Decoding decoding = format.getDecoding();
        int width = format.getIntegerOption("width");
        int maxLineNumber = (data.length + width - 1) / width + 1;
        int lineNumberWidth = 0;
        while (maxLineNumber > 0) {
            lineNumberWidth++;
            maxLineNumber /= 10;
        }
        int pos = 0;
        int lineNumber = 1;
        BichromaticStringBuilder bsb = new BichromaticStringBuilder("34"); // the secondary colour is blue
        BichromaticStringBuilder.DelimitedMode del = new BichromaticStringBuilder.DelimitedMode(bsb, '<', '=', '>');
        while (pos < data.length) {
            bsb.clear();
            bsb.sb.append(lineNumber);
            while (bsb.sb.length() < lineNumberWidth) {
                bsb.sb.insert(0, '0');
            }
            bsb.sb.append(' ');
            for (int i = pos; i < pos + width && i < data.length; i++) {
                byte b = data[i];
                char c = decoding.decode(b);
                if (c != 0) {
                    del.delimitForPlain();
                    bsb.sb.append(c);
                } else {
                    del.delimitForColour();
                    bsb.sb.append(Hex.b(b));
                }
            }
            if (bsb.inColouredMode()) {
                del.delimitForPlain();
            }
            bsb.printLine(port);
            pos += width;
            lineNumber++;
        }
        return null;
    }
}
