package net.mirky.redis.analysers;

import java.io.PrintStream;

import net.mirky.redis.Analyser;
import net.mirky.redis.ChromaticTextGenerator;
import net.mirky.redis.Decoding;
import net.mirky.redis.Format;
import net.mirky.redis.Format.UnknownOption;
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
        ChromaticTextGenerator ctg = new ChromaticTextGenerator('<', '>', port);
        while (pos < data.length) {
            ctg.appendLeftPadded(Integer.toString(lineNumber), '0', lineNumberWidth);
            ctg.appendChar(' ');
            for (int i = pos; i < pos + width && i < data.length; i++) {
                byte b = data[i];
                char c = decoding.decode(b);
                if (c != 0) {
                    ctg.appendChar(c);
                } else {
                    ctg.appendHexByteToken(b);
                }
            }
            ctg.terpri();
            pos += width;
            lineNumber++;
        }
        return null;
    }
}
