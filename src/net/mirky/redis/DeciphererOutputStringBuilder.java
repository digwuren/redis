package net.mirky.redis;

final class DeciphererOutputStringBuilder extends DeciphererOutput {
    public final StringBuilder sb;
    
    public DeciphererOutputStringBuilder(StringBuilder sb) {
        this.sb = sb;
    }

    public final void append(char c) {
        sb.append(c);
    }

    public final void append(String s) {
        sb.append(s);
    }

    public final void append(int i) {
        sb.append(i);
    }

    public final void switchBack() {
        // no effect in the output generation phase
    }

    public final void terminate() {
        // no effect in the output generation phase
    }

    public final void setCountdown(int newCountdown) {
        // no effect in the output generation phase
    }

    public final void noteAbsoluteEntryPoint(int address) {
        // no effect in the output generation phase
    }

    public final void lookupAPI(int address) {
        // no effect in the output generation phase
    }

    public final void switchTemporarily(ClassicLang newLang) {
        // no effect in the output generation phase
    }
}