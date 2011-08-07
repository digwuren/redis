package net.mirky.redis.analysers;

import java.io.PrintStream;

import net.mirky.redis.Cursor;
import net.mirky.redis.Decoding;
import net.mirky.redis.Format;
import net.mirky.redis.Analyser;
import net.mirky.redis.Hex;
import net.mirky.redis.ImageError;

@Format.Options("d64-directory/decoding:decoding=petscii")
public final class D64DirectoryAnalyser extends Analyser.Leaf.PossiblyPartial {
    @Override
    protected final int disPartially(Format format, byte[] data, PrintStream port) throws RuntimeException {
        try {
            Cursor cursor = new Cursor.ByteArrayCursor(data, 0);
            int entryNumber = 0;
            while (cursor.probe(0x20)) {
                port.println();
                displayEntry(cursor, entryNumber, format, port);
                cursor.advance(0x20);
                entryNumber++;
            }
            return cursor.tell();
        } catch (ImageError e) {
            // not supposed to happen, we probed first
            throw new RuntimeException("bug detected", e);
        }
    }

    private final void displayEntry(Cursor cursor, int entryNumber, Format format, PrintStream port) throws ImageError {
        if (cursor.regionBlank(2, 30)) {
    		port.println("Entry #" + entryNumber + " is blank.");
    	} else {
    		int fileTypeByte = cursor.getUnsignedByte(2);
    		Decoding decoding = format.getDecoding();
            if (fileTypeByte == 0) {
    			port.println("Entry #" + entryNumber + " is unused.");
    			Hex.dump(cursor.getBytes(2, 30), cursor.tell() + 2, decoding, port);
    		}  else {
    			port.print("Entry #" + entryNumber + ": ");
    			decoding.displayForeignString(cursor.getPaddedBytes(5, 16, (byte) 0xA0), port);
    			port.println();
    			switch (fileTypeByte & 0xF) {
    				case 0:
    					port.print("DEL");
    					break;
    				case 1:
    					port.print("SEQ");
    					break;
    				case 2:
    					port.print("PRG");
    					break;
    				case 3:
    					port.print("USR");
    					break;
    				case 4:
    					port.print("REL");
    					break;
    				default:
    					port.print("#" + (fileTypeByte & 0xF) + " (invalid)");
    			}
    			if ((fileTypeByte & 0x40) != 0) {
    				port.print(" (locked)");
    			}
    			if ((fileTypeByte & 0x80) == 0) {
    				port.print(" (unclosed)");
    			}
    			port.print(", starts at track " + cursor.getUnsignedByte(3));
    			port.print(" sector " + cursor.getUnsignedByte(4));
    			int sideStartTrack = cursor.getUnsignedByte(0x15);
    			int sideStartSector = cursor.getUnsignedByte(0x16);
    			if (sideStartTrack == 0 && sideStartSector == 0) {
    				port.print(", no side chain");
    			} else {
    				port.print(", side chain starts at track " + sideStartTrack + " sector " + sideStartSector);
    			}
    			port.println(", " + cursor.getUnsignedLewyde(30) + " sectors");
    		}
    	}
    }
}