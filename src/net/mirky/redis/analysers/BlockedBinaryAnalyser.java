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
        // Note that the lowest level is given first.
        AccountingLevel[] accountingLevels = new AccountingLevel[]{
                new AccountingLevel("block", format.getIntegerOption("bs"), 0)
        };
        int[] accounters = new int[accountingLevels.length];
        for (int i = 0; i < accounters.length; i++) {
            accounters[i] = accountingLevels[i].first;
        }
        for (int blockStart = 0; blockStart < data.length; blockStart += accountingLevels[0].size) {
            int blockEnd = blockStart + accountingLevels[0].size;
            if (blockEnd > data.length) {
                blockEnd = data.length;
            }
            byte[] block = new byte[blockEnd - blockStart];
            System.arraycopy(data, blockStart, block, 0, block.length);
            if (blockStart != 0) {
                port.println(); // visual separator for blocks
            }
            for (int i = accounters.length - 1; i >= 0; i--) {
                port.print(accountingLevels[i].name + " " + accounters[i]);
                if (i != 0) {
                    port.print(", ");
                }
            }
            port.println();
            Hex.dump(block, 0, decoding, port);
            int carry = 0;
            while (true) {
                accounters[carry]++;
                if (!(carry + 1 < accountingLevels.length && accounters[carry] - accountingLevels[carry].first >= accountingLevels[carry + 1].size)) {
                    break;
                }
                accounters[carry] = accountingLevels[carry].first;
                carry++;
            }
        }
        return null;
    }
    
    private static final class AccountingLevel {
        public final String name;
        public final int size;
        public final int first;
        
        public AccountingLevel(String name, int size, int first) {
            this.name = name;
            this.size = size;
            this.first = first;
        }
    }
}