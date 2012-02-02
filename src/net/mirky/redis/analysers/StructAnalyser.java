package net.mirky.redis.analysers;

import java.io.PrintStream;

import net.mirky.redis.Analyser;
import net.mirky.redis.BinaryElementType;
import net.mirky.redis.Cursor;
import net.mirky.redis.Format;
import net.mirky.redis.ImageError;
import net.mirky.redis.Named;

@Format.Options("struct/decoding:decoding=ascii/struct!:struct=unsigned-byte")
public final class StructAnalyser extends Analyser.Leaf.PossiblyPartial {
    @Override
    protected final int disPartially(Format format, byte[] data, PrintStream port) throws RuntimeException {
        @SuppressWarnings("unchecked")
        BinaryElementType elementType = ((Format.Option.SimpleOption<Named<BinaryElementType>>) format.getOption("struct")).value.content;
        Cursor cursor = new Cursor(data, 0);
        try {
            elementType.pass(cursor, "", null, format.getDecoding(), port);
            return cursor.tell();
        } catch (ImageError e) {
            // FIXME: once we output via the {@link ChromaticLineBuilder}, this ought to be shown in red
            port.println("!!! unexpected end of image");
            return 0;
        }
    }
}
