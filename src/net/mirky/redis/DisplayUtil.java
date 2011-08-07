package net.mirky.redis;

public final class DisplayUtil {
    private static final String CSI = (char) 0x1B + "[";
    
    private DisplayUtil() {
        // not a real class
    }
    
    public static final String brightRed(String s) {
        return CSI + "31;1m" + s + CSI + "0m";
    }
}
