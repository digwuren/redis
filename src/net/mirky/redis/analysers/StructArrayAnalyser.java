package net.mirky.redis.analysers;

import java.io.PrintStream;

import net.mirky.redis.Analyser;
import net.mirky.redis.Cursor;
import net.mirky.redis.Decoding;
import net.mirky.redis.Format;
import net.mirky.redis.ImageError;
import net.mirky.redis.Struct;

@Format.Options("array/decoding:decoding=ascii/struct!:struct=unsigned-byte/step!:unsigned-hex=1")
public final class StructArrayAnalyser extends Analyser.Leaf.PossiblyPartial {
    @Override
    protected final int disPartially(Format format, byte[] data, PrintStream port) throws RuntimeException {
        @SuppressWarnings("unchecked")
        Struct struct = ((Format.Option.SimpleOption<Struct>) format.getOption("struct")).value;
        int step = format.getIntegerOption("step");
        try {
            Cursor cursor = new Cursor.ByteArrayCursor(data, 0);
            walkArray(cursor, struct, step, format.getDecoding(), port);
            return cursor.tell();
        } catch (ImageError e) {
            // not supposed to happen, we probed first
            throw new RuntimeException("bug detected", e);
        }
    }

    private final void walkArray(Cursor cursor, Struct struct, int step, Decoding decoding, PrintStream port)
            throws ImageError {
        int entryNumber = 0;
        while (cursor.probe(step)) {
            if (entryNumber != 0) {
                port.println();
            }
            struct.show(cursor, Integer.toString(entryNumber), port, decoding);
            cursor.advance(step);
            entryNumber++;
        }
    }
}
