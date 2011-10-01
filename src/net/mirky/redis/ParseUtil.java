package net.mirky.redis;

public final class ParseUtil {
    private ParseUtil() {
        // not a real constructor
    }

    public static final int parseUnsignedInteger(String s) throws NumberFormatException {
        if (s.length() >= 3 && s.toLowerCase().startsWith("0x")) {
            return Integer.parseInt(s.substring(2), 16);
        } else {
            return Integer.parseInt(s);
        }
    }
}
