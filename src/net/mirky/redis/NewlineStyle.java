package net.mirky.redis;

public enum NewlineStyle {
    LF, CR;

    public final int checkForNewline(byte[] data, int pos) {
        // Note that pos pointing just past end of data is permitted.
        assert pos >= 0 && pos <= data.length;
        switch (this) {
            case LF:
                return pos < data.length && data[pos] == 10 ? 1 : 0;
            case CR:
                return pos < data.length && data[pos] == 13 ? 1 : 0;
            default:
                throw new RuntimeException("bug detected");
        }
    }
}
