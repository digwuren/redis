package net.mirky.redis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

import net.mirky.redis.Disassembler.Bytecode;
import net.mirky.redis.Disassembler.DeciphererInput;
import net.mirky.redis.Disassembler.IncompleteInstruction;
import net.mirky.redis.Disassembler.WavingContext;
import net.mirky.redis.ResourceManager.ResolutionError;
import net.mirky.redis.analysers.ZXSBasicProgramAnalyser;

/**
 * A {@link ClassicLang} roughly represents a particular bytecode/machine code
 * language such as {@code i8080} or {@code m68000}. Most of the actual
 * languages are stored in *.lang resource files, which are translated into
 * an internal bytecode when the {@link ClassicLang} instance is constructed. (See
 * {@link ClassicLang.Tabular} for details.)
 * 
 * {@link ClassicLang}s are equity-comparable by their identity, and hashable and
 * ordering-comparable by their name. This allows them to be stored in both
 * hashed and tree:d sets. Note that {@link ClassicLang} names are unique because
 * of the caching done by {@link ClassicLang#get(String)}.
 */
public abstract class ClassicLang extends AbstractBinaryLanguage implements Comparable<ClassicLang> {
    final String name;
    private final int defaultCountdown;

    private ClassicLang(String name, int defaultCountdown) {
        this.name = name;
        this.defaultCountdown = defaultCountdown;
    }

    @Override
    public final int hashCode() {
        return name.hashCode();
    }

    @Override
    public final boolean equals(Object that) {
        return this == that;
    }

    public final int compareTo(ClassicLang that) {
        return this.name.compareTo(that.name);
    }

    /**
     * Checks triviality status of the language. Switches to a trivial
     * language and back are not explicitly marked in disassembler's output.
     * This is handy for raw value languages.
     * 
     * @return whether the language is trivial
     */
    abstract boolean isTrivial();

    abstract int decipher(int opcode, DeciphererInput in, WavingContext out) throws UnknownOpcode,
    IncompleteInstruction;

    abstract int decipher(int opcode, DeciphererInput in, DeciphererOutputStringBuilder out) throws UnknownOpcode,
            IncompleteInstruction;

    void dumpLang(String langName, PrintStream port) {
        port.println(langName + " is a builtin language");
    }

    @Override
    public final int getDefaultCountdown() {
        return defaultCountdown;
    }

    @SuppressWarnings("synthetic-access")
    static final ClassicLang NONE = new ClassicLang("none", 0) {
        @Override
        final int decipher(int opcode, DeciphererInput in, WavingContext out) {
            // should never be called -- the disassembler should check
            // against NONE
            throw new RuntimeException("bug detected");
        }

        @Override
        final int decipher(int opcode, DeciphererInput in, DeciphererOutputStringBuilder out) {
            // should never be called -- the disassembler should check
            // against NONE
            throw new RuntimeException("bug detected");
        }

        @Override
        final boolean isTrivial() {
            return true;
        }
    };

    @SuppressWarnings("synthetic-access")
    static final ClassicLang CONDENSED_ZXSNUM = new ClassicLang("condensed-zxsnum", 1) {
        @Override
        final int decipher(int firstCondensedByte, DeciphererInput in, WavingContext out) {
            int significandByteCount = (firstCondensedByte >> 6) + 1;
            byte condensedExponent = (byte) (firstCondensedByte & 0x3F);
            int significandOffset = condensedExponent == 0 ? 2 : 1;
            return significandOffset + significandByteCount;
        }

        @Override
        final int decipher(int firstCondensedByte, DeciphererInput in, DeciphererOutputStringBuilder out)
                throws IncompleteInstruction {
            int significandByteCount = (firstCondensedByte >> 6) + 1;
            byte condensedExponent = (byte) (firstCondensedByte & 0x3F);
            byte[] bytes = new byte[]{0, 0, 0, 0, 0};
            int significandOffset;
            if (condensedExponent == 0) {
                bytes[0] = (byte) in.getUnsignedByte(1);
                significandOffset = 2;
            } else {
                bytes[0] = condensedExponent;
                significandOffset = 1;
            }
            bytes[0] += 0x50;
            for (int i = 0; i < significandByteCount; i++) {
                bytes[i + 1] = (byte) in.getUnsignedByte(significandOffset + i);
            }
            out.append("byte ");
            for (int i = 0; i < significandOffset + significandByteCount; i++) {
                if (i != 0) {
                    out.append(", ");
                }
                out.append("0x");
                out.append(Hex.b(in.getUnsignedByte(i)));
            }
            ZXSBasicProgramAnalyser.ZXSpectrumNumber number = new ZXSBasicProgramAnalyser.ZXSpectrumNumber(bytes);
            out.append(" // ");
            number.prepareForDisassemblyDisplay(out.sb);
            return significandOffset + significandByteCount;
        }

        @Override
        final boolean isTrivial() {
            return true;
        }
    };

    static final class Tabular extends ClassicLang {
        private final boolean trivial;
        private final Tabular.Linkage linkage;
        private final byte[] bytecode;
        private final int[] dispatchTable;
        /*
         * Note that the dispatch suboffset declaration is only used for
         * dumping the parsed language table. The actual dispatch key is
         * either implicitly fetched from suboffset zero, or explicitly
         * declared by the parent language -- possibly without any reference
         * to a fixed suboffset at all.
         */
        private final int dispatchSuboffset;

        @SuppressWarnings("synthetic-access")
        private Tabular(String name, int defaultCountdown, boolean trivial, byte[] bytecode, int[] dispatchTable, Tabular.Linkage linkage, LangParser parser) {
            super(name, defaultCountdown);
            assert dispatchTable.length == 256;
            assert linkage.minitables.length <= Bytecode.MAX_MINITABLE_COUNT;
            this.trivial = trivial;
            this.linkage = linkage;
            this.dispatchSuboffset = parser.dispatchSuboffset;
            this.bytecode = bytecode;
            this.dispatchTable = dispatchTable;
        }

        static final ClassicLang.Tabular loadTabular(String name, BufferedReader reader) throws IOException {
            LangParser parser = new LangParser();
            parser.parse(name, reader);
            return new Tabular(name, parser.defaultCountdown, parser.trivial, parser.bytecode, parser.dispatchTable, parser.linkage, parser);
        }

        @Override
        final int decipher(int opcode, DeciphererInput in, WavingContext out) throws UnknownOpcode,
                IncompleteInstruction {
            if (dispatchTable[opcode] == -1) {
                throw new ClassicLang.UnknownOpcode(this);
            }
            return WavingPhaseDecipherer.decipher(bytecode, dispatchTable[opcode], linkage, in, out);
        }

        @Override
        final int decipher(int opcode, DeciphererInput in, DeciphererOutputStringBuilder out) throws UnknownOpcode,
                IncompleteInstruction {
            if (dispatchTable[opcode] == -1) {
                throw new ClassicLang.UnknownOpcode(this);
            }
            return OutputPhaseDecipherer.decipher(bytecode, dispatchTable[opcode], linkage, in, out);
        }

        @Override
        final boolean isTrivial() {
            return trivial;
        }

        @Override
        final void dumpLang(String langName, PrintStream port) {
            port.println("# " + langName + " is a tabular language");
            port.println("Dispatch-suboffset: " + dispatchSuboffset);
            port.println("Default-countdown: " + getDefaultCountdown());
            if (trivial) {
                port.println("Trivial: true");
            }
            for (int i = 0; i < linkage.minitables.length; i++) {
                String[] minitable = linkage.minitables[i];
                if (minitable != null) {
                    port.print("minitable minitable-" + i + ":");
                    for (int j = 0; j < minitable.length; j++) {
                        port.print(' ');
                        port.print(minitable[j]);
                    }
                    port.println();
                }
            }
            linkage.dumpReferredLanguages(port);
            for (int i = 0; i < 256; i++) {
                if ((i & 0x0F) == 0) {
                    port.println();
                }
                port.print("0x" + Hex.b(i) + ' ');
                if (dispatchTable[i] != -1) {
                    port.print("(@0x" + Hex.w(dispatchTable[i]) + ") ");
                    dumpDecipherer(i, dispatchTable[i], port);
                    port.println();
                } else {
                    port.println('-');
                }
            }
            port.println();
            try {
                Hex.dump(bytecode, 0, Decoding.MANAGER.get("ascii"), port);
            } catch (ResolutionError e) {
                throw new RuntimeException("The ASCII decoding is missing?");
            }
        }

        private final void dumpDecipherer(int opcode, int startPosition, PrintStream port) {
            boolean broketed = false;
            DECIPHERER_LOOP : for (int i = startPosition; bytecode[i] != Bytecode.COMPLETE; i++) {
                byte b = bytecode[i];
                if (b == Disassembler.Bytecode.GET_BYTE_0 + dispatchSuboffset) {
                    int currentValue = opcode;
                    for (int k = i + 1; k < bytecode.length; k++) {
                        if (bytecode[k] == Disassembler.Bytecode.SHR_3) {
                            currentValue >>>= 3;
                        } else if (bytecode[k] == Disassembler.Bytecode.SHR_4) {
                            currentValue >>>= 4;
                        } else if (bytecode[k] == Disassembler.Bytecode.SHR_5) {
                            currentValue >>>= 5;
                        } else if (bytecode[k] == Disassembler.Bytecode.SHR_6) {
                            currentValue >>>= 6;
                        } else if (bytecode[k] >= Disassembler.Bytecode.MINITABLE_LOOKUP_0
                                && bytecode[k] < Disassembler.Bytecode.MINITABLE_LOOKUP_0
                                        + Disassembler.Bytecode.MAX_MINITABLE_COUNT) {
                            String[] minitable = linkage.minitables[bytecode[k]
                                    - Disassembler.Bytecode.MINITABLE_LOOKUP_0];
                            if (minitable == null) {
                                break;
                            }
                            if (broketed) {
                                port.print('>');
                                broketed = false;
                            }
                            port.print(minitable[currentValue & (minitable.length - 1)]);
                            i = k;
                            continue DECIPHERER_LOOP;
                        } else {
                            break;
                        }
                    }
                }
                if (b >= 0x20 && b <= 0x7E) {
                    if (broketed) {
                        port.print('>');
                        broketed = false;
                    }
                    port.print((char) b);
                } else {
                    if (!broketed) {
                        port.print('<');
                        broketed = true;
                    } else {
                        port.print(", ");
                    }
                    port.print("0x" + Hex.b(b));
                }
            }
            if (broketed) {
                port.print('>');
            }
        }

        static final class BytecodeCollector {
            private final ArrayList<Byte> steps;

            BytecodeCollector() {
                steps = new ArrayList<Byte>();
            }

            final void add(byte code) {
                steps.add(new Byte(code));
            }

            final byte[] finish() {
                byte[] bytecode = new byte[steps.size()];
                for (int i = 0; i < bytecode.length; i++) {
                    bytecode[i] = steps.get(i).byteValue();
                }
                return bytecode;
            }

            final int currentPosition() {
                return steps.size();
            }
        }

        static final class Linkage {
            final String[][] minitables;
            final String[] referredLanguages;
            
            Linkage() {
                minitables = new String[Disassembler.Bytecode.MAX_MINITABLE_COUNT][];
                referredLanguages = new String[Disassembler.Bytecode.MAX_REFERRED_LANGUAGE_COUNT];
            }

            public final void dumpReferredLanguages(PrintStream port) {
                for (int i = 0; i < referredLanguages.length; i++) {
                    if (referredLanguages[i] != null) {
                        port.println("# referred lang " + i + " is " + referredLanguages[i]);
                    }
                }
            }

            public final ClassicLang getReferredLanguage(int index) {
                try {
                    return ClassicLang.MANAGER.get(referredLanguages[index]);
                } catch (ResolutionError e) {
                    throw new RuntimeException("referred language unknown", e);
                }
            }
        }
    }

    public static final ResourceManager<ClassicLang> MANAGER = new ResourceManager<ClassicLang>("lang") {
        @Override
        public final ClassicLang load(String name, BufferedReader reader) throws IOException {
            return Tabular.loadTabular(name, reader);
        }
    };
    
    static {
        MANAGER.registerSpecial("none", ClassicLang.NONE);
        MANAGER.registerSpecial("condensed-zxsnum", ClassicLang.CONDENSED_ZXSNUM);
    }
    

    /**
     * Thrown when a bytecode table lookup fails. {@link #run()} catches it,
     * terminates disassembly of the current sequence, and makes sure that
     * all fetched bytes of the current instruction are listed as raw bytes
     * for the user to be able to see what could not be parsed.
     */
    static final class UnknownOpcode extends Exception {
        final ClassicLang lang;

        UnknownOpcode(ClassicLang lang) {
            this.lang = lang;
        }
    }
}