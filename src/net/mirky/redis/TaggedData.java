package net.mirky.redis;

final class TaggedData {
    final byte[] data;
    final Format format;

    TaggedData(byte[] data, Format format) {
        this.data = data;
        this.format = format;
    }
}
