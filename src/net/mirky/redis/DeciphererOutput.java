package net.mirky.redis;

public abstract class DeciphererOutput {
    public abstract void append(char c);
    public abstract void append(String s);
    public abstract void append(int i);
    public abstract void switchBack();
    public abstract void terminate();
    public abstract void setCountdown(int newCountdown);
    public abstract void noteAbsoluteEntryPoint(int address);
    public abstract void noteAbsoluteEntryPoint(int currentValue, ClassicLang lang);
    public abstract void lookupAPI(int address);
    public abstract void switchTemporarily(ClassicLang newLang);
}
