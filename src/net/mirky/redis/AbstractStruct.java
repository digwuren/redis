package net.mirky.redis;

import java.io.PrintStream;

public abstract class AbstractStruct {
    /**
     * Show the content of an item of {@code this} structure, extracted from the
     * given {@code cursor}, as a sequence of full lines, output to the given
     * {@code port}. If the field contains textual values, these are to be
     * parsed using the given {@code decoding}.
     * 
     * @param indentation
     *            indentation prefix inherited from the context
     * @param itemName
     *            contextual name of the item
     * @return the offset just past the field, relative to the cursor's position
     * 
     * @throws ImageError
     *             in case the field can not be extracted from data available
     *             via {@code cursor}. Note that this exception can only be
     *             thrown before anything is output, never in the middle of
     *             outputting a line.
     */
    public abstract int show(Cursor cursor, String indentation, String itemName, Decoding decoding, PrintStream port)
            throws ImageError;
}
