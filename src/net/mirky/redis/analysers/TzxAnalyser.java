package net.mirky.redis.analysers;

import net.mirky.redis.BinaryUtil;
import net.mirky.redis.Cursor;
import net.mirky.redis.Format;
import net.mirky.redis.Analyser;
import net.mirky.redis.Hex;
import net.mirky.redis.ImageError;
import net.mirky.redis.ReconstructionDataCollector;

@Format.Options(".tzx/decoding:decoding=zx-spectrum")
public final class TzxAnalyser extends Analyser.Container {
    @Override
    protected final ReconstructionDataCollector extractFiles(Format format, byte[] fileData) throws ImageError {
        int crc32 = BinaryUtil.crc32(fileData);
        ReconstructionDataCollector rcn = new ReconstructionDataCollector(fileData.length);
        ZXSTapeBlockPairer pairer = new ZXSTapeBlockPairer(format, new Cursor(fileData, 0), rcn);
        if (!pairer.cursor.hasMagic(0, (byte) 0x5A, (byte) 0x58, (byte) 0x54, (byte) 0x61, (byte) 0x70, (byte) 0x65, (byte) 0x21, (byte) 0x1A)) {
            throw new ImageError("tzx magic not found");
        }
        int majorVersion = pairer.cursor.getUnsignedByte(8);
        if (majorVersion != 1) {
            throw new ImageError("unknown TZX format version");
        }
        pairer.cursor.extractPatch(0, 10, rcn);
        pairer.cursor.advance(10);
        while (!pairer.cursor.atEnd()) {
            int blockType = pairer.cursor.getUnsignedByte(0);
            switch (blockType) {
                case 0x10: // Standard speed data block
                    pairer.cursor.advance(5);
                    pairer.processTapeBlock(pairer.cursor.getUnsignedLewyde(-2), 5);
                    break;
                case 0x11: // Turbo speed data block
                    pairer.cursor.advance(0x13);
                    pairer.processTapeBlock(pairer.cursor.getUnsignedLetribyte(-3), 0x13);
                    break;
                case 0x12: // Pure tone
                    pairer.cursor.extractPatch(0, 5, rcn);
                    pairer.cursor.advance(5);
                    break;
                case 0x13: // Sequence of pulses of various lengths
                    pairer.cursor.walkAndExtractPatch(2 + pairer.cursor.getUnsignedByte(1) * 2, rcn);
                    break;
                case 0x14: // Pure data block
                    pairer.cursor.advance(0x0B);
                    pairer.processTapeBlock(pairer.cursor.getUnsignedLetribyte(-3), 0x0B);
                    break;
                case 0x20: // Silence / Stop tape
                    pairer.cursor.walkAndExtractPatch(3, rcn);
                    break;
                case 0x21: // Group start
                    pairer.cursor.walkAndExtractPatch(2 + pairer.cursor.getUnsignedByte(1), rcn);
                    break;
                case 0x22: // Group end
                    pairer.cursor.walkAndExtractPatch(1, rcn);
                    break;
                case 0x30: // Text description
                    pairer.cursor.walkAndExtractPatch(2 + pairer.cursor.getUnsignedByte(1), rcn);
                    break;
                case 0x32: // Archive info
                    pairer.cursor.walkAndExtractPatch(3 + pairer.cursor.getUnsignedLewyde(1), rcn);
                    break;
                // XXX: The following TZX block types are not currently supported.
                case 0x15: // Direct Recording
                    throw new ImageError("unsupported TZX block type 0x15, Direct Recording");
                case 0x18: // CSW Recording
                    throw new ImageError("unsupported TZX block type 0x18, CSW Recording");
                case 0x19: // Generalised Data Block
                    throw new ImageError("unsupported TZX block type 0x19, Generalised Data Block");
                case 0x23: // Jump to block
                    throw new ImageError("unsupported TZX block type 0x23, Jump to block");
                case 0x24: // Loop start
                    throw new ImageError("unsupported TZX block type 0x24, Loop start");
                case 0x25: // Loop end
                    throw new ImageError("unsupported TZX block type 0x25, Loop end");
                case 0x26: // Call sequence
                    throw new ImageError("unsupported TZX block type 0x26, Call sequence");
                case 0x27: // Return from sequence
                    throw new ImageError("unsupported TZX block type 0x27, Return from sequence");
                case 0x28: // Select block
                    throw new ImageError("unsupported TZX block type 0x28, Select block");
                case 0x2A: // Stop the tape if in 48K mode
                    throw new ImageError("unsupported TZX block type 0x2A, Stop the tape if in 48K mode");
                case 0x2B: // Set signal level
                    throw new ImageError("unsupported TZX block type 0x2B, Set signal level");
                case 0x31: // Message block
                    throw new ImageError("unsupported TZX block type 0x31, Message block");
                case 0x33: // Hardware type
                    throw new ImageError("unsupported TZX block type 0x33, Hardware type");
                case 0x35: // Custom info block
                    throw new ImageError("unsupported TZX block type 0x35, Custom info block");
                case 0x5A: // "Glue" block
                    throw new ImageError("unsupported TZX block type 0x5A, \"Glue\" block");
                // The following TZX block types are deprecated and are not currently supported.
                case 0x16: // C64 ROM type data block
                    throw new ImageError("unsupported TZX block type 0x16, C64 ROM type data block (deprecated)");
                case 0x17: // C64 turbo tape data block
                    throw new ImageError("unsupported TZX block type 0x17, C64 turbo tape data block (deprecated)");
                case 0x34: // Emulation info
                    throw new ImageError("unsupported TZX block type 0x34, Emulation info (deprecated)");
                case 0x40: // Snapshot block
                    throw new ImageError("unsupported TZX block type 0x40, Snapshot block (deprecated)");
                default:
                    throw new ImageError("unknown TZX block type 0x" + Hex.b(blockType));
            }
        }
        rcn.checksum(crc32);
        return rcn;
    }
}