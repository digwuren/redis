package net.mirky.redis.analysers;

import java.io.PrintStream;

import net.mirky.redis.Analyser;
import net.mirky.redis.ChromaticTextGenerator;
import net.mirky.redis.Decoding;
import net.mirky.redis.Format;
import net.mirky.redis.HighBitInterpretation;
import net.mirky.redis.ReconstructionDataCollector;

@Format.Options("plain/decoding:decoding=ascii/high-bit:high-bit=keep")
public final class PlainTextAnalyser extends Analyser.Leaf {
    @Override
    protected final ReconstructionDataCollector dis(Format format, byte[] data, PrintStream port) throws Format.UnknownOption {
        Decoding decoding = format.getDecoding();
        HighBitInterpretation hbi = (HighBitInterpretation) ((Format.Option.SimpleOption) format.getOption("high-bit")).value;
        ChromaticTextGenerator ctg = new ChromaticTextGenerator('<', '>', port);
        // FIXME: the delimiter should be configurable, not hardcoded as CR
        byte lineDelimiter = 0x0D;
        boolean inMiddleOfLine = false;
        for (int pos = 0; pos < data.length; pos++) {
            byte b = data[pos];
            if (b != lineDelimiter) {
                ctg.processInputByte(b, hbi, decoding);
                inMiddleOfLine = true;
            } else {
                ctg.terpri();
                inMiddleOfLine = false;
            }
        }
        if (inMiddleOfLine) {
            ctg.appendToken("noeol");
            ctg.terpri();
        }
        return null;
    }
}
