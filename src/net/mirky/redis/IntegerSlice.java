package net.mirky.redis;

/**
 * An integer slice specifies how to extract and interpret a part of an integer
 * or other similar bit vector. The main integer is normally extracted from the
 * binary via a {@link BinaryElementType.SlicedInteger} instance.
 */
public abstract class IntegerSlice {
    protected final int rightShift;

    protected IntegerSlice(int shift) {
        this.rightShift = shift;
    }

    /**
     * Given the full integer, extract this slice and decode it for human
     * consumption.
     * 
     * @param field
     *            full field, not just this slice
     * @return decoded slice as a {@link String} instance. If the result is
     *         a non-empty string, it will start with a space.
     */
    public abstract String decode(int field);

    static final class Basic extends IntegerSlice {
        private final int width;
        private final String[] meanings;

        // Note that {@code meanings} can be shorter than {@code 1 << bitCount}, and it
        // can contain {@code null}:s.
        public Basic(int rightShift, int width, String[] meanings) {
            super(rightShift);
            this.width = width;
            this.meanings = meanings;
        }

        @Override
        public final String decode(int field) {
            int code = (field >> rightShift) & ((1 << width) - 1);
            String meaning = code < meanings.length ? meanings[code] : null;
            if (meaning != null) {
                return ' ' + meaning;
            } else {
                return " #" + code + " (invalid)";
            }
        }
    }

    static final class Flag extends IntegerSlice {
        private final String setMeaning;
        private final String clearMeaning;

        public Flag(int rightShift, String setMeaning, String clearMeaning) {
            super(rightShift);
            this.setMeaning = setMeaning;
            this.clearMeaning = clearMeaning;
        }

        @Override
        public final String decode(int field) {
            String meaning = ((field >> rightShift) & 1) != 0 ? setMeaning : clearMeaning;
            if (meaning != null) {
                return ' ' + meaning;
            } else {
                return "";
            }
        }
    }
}