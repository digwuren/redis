package net.mirky.redis.analysers;

import java.io.PrintStream;

import net.mirky.redis.Analyser;
import net.mirky.redis.Cursor;
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
        Cursor cursor = new Cursor.ByteArrayCursor(data, 0);
        try {
            int entryNumber = 0; // for the breadcrumb trail
            while (cursor.probe(step)) {
                if (entryNumber != 0) {
                    port.println();
                }
                struct.show(cursor, Integer.toString(entryNumber), port, format.getDecoding());
                cursor.advance(step);
                entryNumber++;
            }
        } catch (ImageError e) {
            // FIXME: once we output via the {@link ChromaticLineBuilder}, this ought to be shown in red
            port.println("!!! unexpected end of image");
        }
        return cursor.tell();
    }
}
