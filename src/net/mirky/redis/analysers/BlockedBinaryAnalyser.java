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
        // Note that we're ignoring the origin here.
        Decoding decoding = format.getDecoding();
        // Note that the lowest level is given zeroth.
        Format.GeometryLevel[] geometry = new Format.GeometryLevel[]{
                new Format.GeometryLevel("block", format.getIntegerOption("bs"), 0)
        };
        int[] address = new int[geometry.length];
        for (int i = 0; i < address.length; i++) {
            address[i] = geometry[i].first;
        }
        for (int blockStart = 0; blockStart < data.length; blockStart += geometry[0].size) {
            int blockEnd = blockStart + geometry[0].size;
            if (blockEnd > data.length) {
                blockEnd = data.length;
            }
            byte[] block = new byte[blockEnd - blockStart];
            System.arraycopy(data, blockStart, block, 0, block.length);
            if (blockStart != 0) {
                port.println(); // visual separator for blocks
            }
            for (int i = address.length - 1; i >= 0; i--) {
                port.print(geometry[i].name + " " + address[i]);
                if (i != 0) {
                    port.print(", ");
                }
            }
            port.println();
            Hex.dump(block, 0, decoding, port);
            int carry = 0;
            while (true) {
                address[carry]++;
                if (!(carry + 1 < geometry.length && address[carry] - geometry[carry].first >= geometry[carry + 1].size)) {
                    break;
                }
                address[carry] = geometry[carry].first;
                carry++;
            }
        }
        return null;
    }
}