package net.mirky.redis;

public final class Named<T> {
    public final String name;
    public final T content;
    
    public Named(String name, T content) {
        this.name = name;
        this.content = content;
    }
}
