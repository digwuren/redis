package net.mirky.redis.analysers;

import java.io.PrintStream;

import net.mirky.redis.Analyser;
import net.mirky.redis.Decoding;
import net.mirky.redis.Format;
import net.mirky.redis.Format.UnknownOption;
import net.mirky.redis.Hex;
import net.mirky.redis.ReconstructionDataCollector;

@Format.Options(value = "blocks/bs:positive-decimal=1024/decoding:decoding=ascii/origin:unsigned-hex=0")
public class BlockedBinaryAnalyser extends Analyser.Leaf {
    @Override
    protected final ReconstructionDataCollector dis(Format format, byte[] data, PrintStream port) throws UnknownOption, RuntimeException {
        Decoding decoding = format.getDecoding();
        int blockSize = format.getIntegerOption("bs");
        // Note that we're ignoring the origin here.
        int blockCounter = 0;
        for (int blockStart = 0; blockStart < data.length; blockStart += blockSize) {
            int blockEnd = blockStart + blockSize;
            if (blockEnd > data.length) {
                blockEnd = data.length;
            }
            byte[] block = new byte[blockEnd - blockStart];
            System.arraycopy(data, blockStart, block, 0, block.length);
            if (blockStart != 0) {
                port.println(); // visual separator for blocks
            }
            port.println("block " + blockCounter);
            Hex.dump(block, 0, decoding, port);
            blockCounter++;
        }
        return null;
    }
}