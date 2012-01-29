package net.mirky.redis.analysers;

import java.io.PrintStream;

import net.mirky.redis.Analyser;
import net.mirky.redis.Cursor;
import net.mirky.redis.Format;
import net.mirky.redis.ImageError;
import net.mirky.redis.Struct;

@Format.Options("struct/decoding:decoding=ascii/struct!:struct=unsigned-byte")
public final class StructAnalyser extends Analyser.Leaf.PossiblyPartial {
    @Override
    protected final int disPartially(Format format, byte[] data, PrintStream port) throws RuntimeException {
        @SuppressWarnings("unchecked")
        Struct struct = ((Format.Option.SimpleOption<Struct>) format.getOption("struct")).value;
        Cursor cursor = new Cursor.ByteArrayCursor(data, 0);
        try {
            return struct.show(cursor, "/", port, format.getDecoding());
        } catch (ImageError e) {
            // FIXME: once we output via the {@link ChromaticLineBuilder}, this ought to be shown in red
            port.println("!!! unexpected end of image");
            return 0;
        }
    }
}
