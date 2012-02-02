package net.mirky.redis.analysers;

import java.io.PrintStream;

import net.mirky.redis.Analyser;
import net.mirky.redis.Cursor;
import net.mirky.redis.Format;
import net.mirky.redis.ImageError;
import net.mirky.redis.Named;
import net.mirky.redis.Struct;

@Format.Options("array/decoding:decoding=ascii/struct!:struct=unsigned-byte")
public final class StructArrayAnalyser extends Analyser.Leaf.PossiblyPartial {
    @Override
    protected final int disPartially(Format format, byte[] data, PrintStream port) throws RuntimeException {
        @SuppressWarnings("unchecked")
        Struct struct = ((Format.Option.SimpleOption<Named<Struct>>) format.getOption("struct")).value.content;
        Cursor cursor = new Cursor.ByteArrayCursor(data, 0);
        try {
            boolean firstp = true;
            while (!cursor.atEnd()) {
                if (!firstp) {
                    port.println();
                }
                int structSize = struct.show(cursor, "", null, format.getDecoding(), port);
                if (structSize == 0) {
                    // FIXME: once we output via the {@link ChromaticLineBuilder}, this ought to be shown in red
                    port.println("!!! null struct");
                    break;
                }
                cursor.advance(structSize);
                firstp = false;
            }
        } catch (ImageError e) {
            // FIXME: once we output via the {@link ChromaticLineBuilder}, this ought to be shown in red
            port.println("!!! unexpected end of image");
        }
        return cursor.tell();
    }
}
