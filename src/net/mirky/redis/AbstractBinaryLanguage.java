package net.mirky.redis;

// XXX: Currently, we have two similar mechanisms, the disassembler languages
// (see {@link Disassembler.Lang}) and the binary data structures (see {@link
// BinaryElementType}). Ideally, they should be interchangeable. This class, not
// yet fully abstracted, is their common ancestor.
abstract class AbstractBinaryLanguage {
    public abstract int getDefaultCountdown();
}
