package net.mirky.redis;

import net.mirky.redis.ClassicLang.Bytecode.DeciphererOutput;

final class DeciphererOutputStringBuilder extends DeciphererOutput {
    public final StringBuilder sb;
    
    public DeciphererOutputStringBuilder(StringBuilder sb) {
        this.sb = sb;
    }

    @Override
    public final void append(char c) {
        sb.append(c);
    }

    @Override
    public final void append(String s) {
        sb.append(s);
    }

    @Override
    public final void append(int i) {
        sb.append(i);
    }

    @Override
    public final void switchBack() {
        // no effect in the output generation phase
    }

    @Override
    public final void terminate() {
        // no effect in the output generation phase
    }

    @Override
    public final void setCountdown(int newCountdown) {
        // no effect in the output generation phase
    }

    @Override
    public final void noteAbsoluteEntryPoint(int address) {
        // no effect in the output generation phase
    }

    @Override
    public final void noteAbsoluteEntryPoint(int currentValue, ClassicLang lang) {
        // no effect in the output generation phase
    }

    @Override
    public final void lookupAPI(int address) {
        // no effect in the output generation phase
    }

    @Override
    public final void switchTemporarily(ClassicLang newLang) {
        // no effect in the output generation phase
    }
}