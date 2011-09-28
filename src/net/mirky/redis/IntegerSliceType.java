package net.mirky.redis;

import java.util.HashMap;
import java.util.Map;

/**
 * An integer slice type specifies how to extract and interpret a part of an
 * integer or other similar bit vector. The integer is usually extracted from
 * the binary via a {@link StructFieldType.SlicedByteField} instance.
 */
public abstract class IntegerSliceType {
    protected final int shift;

    protected IntegerSliceType(int shift) {
        this.shift = shift;
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

    static final class Basic extends IntegerSliceType {
        private final int bitCount;
        private final Map<Integer, String> meanings;

        public Basic(int shift, int bitCount, String... firstMeanings) {
            super(shift);
            this.bitCount = bitCount;
            meanings = new HashMap<Integer, String>();
            for (int i = 0; i < firstMeanings.length; i++) {
                declare(i, firstMeanings[i]);
            }
        }

        public final void declare(int key, String meaning) {
            Integer boxedKey = new Integer(key);
            assert !meanings.containsKey(boxedKey);
            meanings.put(boxedKey, meaning);
        }

        @Override
        public final String decode(int field) {
            int code = (field >> shift) & ((1 << bitCount) - 1);
            String meaning = meanings.get(new Integer(code));
            if (meaning != null) {
                return ' ' + meaning;
            } else {
                return " #" + code + " (invalid)";
            }
        }
    }

    static final class Flag extends IntegerSliceType {
        private final String setMessage;
        private final String clearMessage;

        // Non-empty messages should start with a space.
        public Flag(int shift, String setMessage, String clearMessage) {
            super(shift);
            this.setMessage = setMessage;
            this.clearMessage = clearMessage;
        }

        @Override
        public final String decode(int field) {
            return ((field >> shift) & 1) != 0 ? setMessage : clearMessage;
        }
    }
}