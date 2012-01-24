package net.mirky.redis.analysers;

import java.io.PrintStream;

import net.mirky.redis.Analyser;
import net.mirky.redis.Decoding;
import net.mirky.redis.Format;
import net.mirky.redis.HighBitInterpretation;
import net.mirky.redis.ChromaticLineBuilder;
import net.mirky.redis.NewlineStyle;
import net.mirky.redis.ReconstructionDataCollector;

@Format.Options("plain/decoding:decoding=ascii/high-bit:high-bit=keep/nl:newline-style=lf")
public final class PlainTextAnalyser extends Analyser.Leaf {
    @Override
    protected final ReconstructionDataCollector dis(Format format, byte[] data, PrintStream port) throws Format.UnknownOption {
        Decoding decoding = format.getDecoding();
        HighBitInterpretation hbi = (HighBitInterpretation) ((Format.Option.SimpleOption) format.getOption("high-bit")).value;
        NewlineStyle nl = (NewlineStyle) ((Format.Option.SimpleOption) format.getOption("nl")).value;

        ChromaticLineBuilder clb = new ChromaticLineBuilder();
        boolean inMiddleOfLine = false;
        for (int pos = 0; pos < data.length;) {
            int newlineSize = nl.checkForNewline(data, pos);
            if (newlineSize == 0) {
                byte b = data[pos];
                clb.processInputByte(b, hbi, decoding);
                inMiddleOfLine = true;
                pos++;
            } else {
                clb.terpri(port);
                inMiddleOfLine = false;
                pos += newlineSize;
            }
        }
        if (inMiddleOfLine) {
            clb.changeMode(ChromaticLineBuilder.CONTROL);
            clb.append("noeol");
            clb.terpri(port);
        }
        return null;
    }
}
