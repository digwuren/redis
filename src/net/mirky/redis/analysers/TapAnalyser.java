package net.mirky.redis.analysers;

import net.mirky.redis.BinaryUtil;
import net.mirky.redis.Cursor;
import net.mirky.redis.Format;
import net.mirky.redis.Analyser;
import net.mirky.redis.ImageError;
import net.mirky.redis.ReconstructionDataCollector;

@Format.Options(".tap/decoding:decoding=zx-spectrum")
public final class TapAnalyser extends Analyser.Container {
    @Override
    protected final ReconstructionDataCollector extractFiles(Format format, byte[] fileData) throws ImageError {
        int crc32 = BinaryUtil.crc32(fileData);
        ReconstructionDataCollector rcn = new ReconstructionDataCollector(fileData.length);
        ZXSTapeBlockPairer pairer = new ZXSTapeBlockPairer(format, new Cursor.ByteArrayCursor(fileData, 0), rcn);
        while (!pairer.cursor.atEnd()) {
            int blockSize = pairer.cursor.getUnsignedLewyde(0);
            pairer.cursor.advance(2);
            pairer.processTapeBlock(blockSize, 2);
        }
        rcn.checksum(crc32);
        return rcn;
    }
}