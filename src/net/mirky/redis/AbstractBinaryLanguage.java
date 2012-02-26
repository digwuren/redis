package net.mirky.redis;

// XXX: Currently, we have two similar mechanisms, the disassembler languages
// (see {@link Disassembler.Lang}) and the binary data structures (see {@link
// BinaryElementType}). Ideally, they should be interchangeable. This class, not
// yet fully abstracted, is their common ancestor.
abstract class AbstractBinaryLanguage {
    /**
     * Check triviality status of the language. Switches to a trivial
     * language and back are not explicitly marked in disassembler's output.
     * This is handy for data-style languages.
     * 
     * @return whether the language is trivial
     */
    abstract boolean isTrivial();

    public abstract int getDefaultCountdown();
}
