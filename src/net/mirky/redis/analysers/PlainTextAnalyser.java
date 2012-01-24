package net.mirky.redis.analysers;

import java.io.PrintStream;

import net.mirky.redis.Analyser;
import net.mirky.redis.BichromaticStringBuilder;
import net.mirky.redis.ChromaticTextGenerator;
import net.mirky.redis.Decoding;
import net.mirky.redis.Format;
import net.mirky.redis.Format.UnknownOption;
import net.mirky.redis.Hex;
import net.mirky.redis.ReconstructionDataCollector;

@Format.Options("plain/decoding:decoding=ascii")
public final class PlainTextAnalyser extends Analyser.Leaf {
    @Override
    protected final ReconstructionDataCollector dis(Format format, byte[] data, PrintStream port) throws UnknownOption {
        Decoding decoding = format.getDecoding();
        ChromaticTextGenerator ctg = new ChromaticTextGenerator('<', '>', port);
        // FIXME: the delimiter should be configurable, not hardcoded as CR
        byte lineDelimiter = 0x0D;
        boolean inMiddleOfLine = false;
        for (int pos = 0; pos < data.length; pos++) {
            byte b = data[pos];
            if (b != lineDelimiter) {
                // FIXME: the eighth bit's meaning should be configurable
                char c = decoding.decode(b);
                if (c != 0) {
                    ctg.appendChar(c);
                } else {
                    ctg.appendHexByteToken(b);
                }
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
