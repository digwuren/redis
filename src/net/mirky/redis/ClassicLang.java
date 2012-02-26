package net.mirky.redis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import net.mirky.redis.ClassicLang.Bytecode.DeciphererOutput;
import net.mirky.redis.Disassembler.DeciphererInput;
import net.mirky.redis.Disassembler.IncompleteInstruction;
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

    abstract int decipher(int opcode, DeciphererInput in, DeciphererOutput out) throws UnknownOpcode,
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
        final int decipher(int opcode, DeciphererInput in, DeciphererOutput out) {
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
        final int decipher(int firstCondensedByte, DeciphererInput in, DeciphererOutput out)
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
            number.prepareForDisassemblyDisplay(out);
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
        final int decipher(int opcode, DeciphererInput in, DeciphererOutput out) throws UnknownOpcode,
                IncompleteInstruction {
            if (dispatchTable[opcode] == -1) {
                throw new ClassicLang.UnknownOpcode(this);
            }
            return Bytecode.decipher(bytecode, dispatchTable[opcode], linkage, in, out);
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
                if (b == Bytecode.GET_BYTE_0 + dispatchSuboffset) {
                    int currentValue = opcode;
                    for (int k = i + 1; k < bytecode.length; k++) {
                        if (bytecode[k] == Bytecode.SHR_3) {
                            currentValue >>>= 3;
                        } else if (bytecode[k] == Bytecode.SHR_4) {
                            currentValue >>>= 4;
                        } else if (bytecode[k] == Bytecode.SHR_5) {
                            currentValue >>>= 5;
                        } else if (bytecode[k] == Bytecode.SHR_6) {
                            currentValue >>>= 6;
                        } else if (bytecode[k] >= Bytecode.MINITABLE_LOOKUP_0
                                && bytecode[k] < Bytecode.MINITABLE_LOOKUP_0
                                        + Bytecode.MAX_MINITABLE_COUNT) {
                            String[] minitable = linkage.minitables[bytecode[k]
                                    - Bytecode.MINITABLE_LOOKUP_0];
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
                minitables = new String[Bytecode.MAX_MINITABLE_COUNT][];
                referredLanguages = new String[Bytecode.MAX_REFERRED_LANGUAGE_COUNT];
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
    
    /**
     * Bytecode values for the internal bytecode. Note that the bytecode is
     * currently not considered a medium for data storage or external
     * communication; it *will* change significantly and is not suitable for
     * external data storage yet.
     */
    public static final class Bytecode {
        private Bytecode() {
            // not a real constructor
        }

        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.FIELD)
        private static @interface DeciphererStep {
            String name();

            // If -1, any non-zero preceding size of {@code currentValue} is
            // considered acceptable.
            // If >= 0, this operator can only be used when {@code currentValue}
            // has such a size.
            int sizeRequirement();

            // If -1, this operator does not change the size of {@code
            // currentValue}. If >= 0,
            // new size of {@code currentValue} after execution of this
            // operator.
            int sizeAfter() default -1;
        }

        // End processing of the current instruction.
        static final byte COMPLETE = 0x00;

        // Output the current integer value as a decimal number.
        @DeciphererStep(name = "decimal", sizeRequirement = -1, sizeAfter = 0)
        static final byte DECIMAL = 0x01;

        // Add the current instruction's address and one to the current integer
        // value.
        @DeciphererStep(name = "signedrel 1", sizeRequirement = 1, sizeAfter = 2)
        static final byte BYTE_SIGNEDREL_1 = 0x02;

        // Add the current instruction's address and two to the current integer
        // value.
        @DeciphererStep(name = "signedrel 2", sizeRequirement = 1, sizeAfter = 2)
        static final byte BYTE_SIGNEDREL_2 = 0x03;

        // Mark the address pointed by current integer value an entry point in
        // the current language.
        @DeciphererStep(name = "entry", sizeRequirement = -1)
        static final byte ENTRY_POINT_REFERENCE = 0x04;

        // Ditto, plus check if the address has a special meaning in the current
        // API.
        // Call to such a special subroutine may be considered terminal (that
        // is, non-returning)
        // by the disassembler, or it may cause the disassembler to switch
        // language.
        @DeciphererStep(name = "subrentry", sizeRequirement = -1)
        static final byte SUBROUTINE_ENTRY_POINT_REFERENCE = 0x05;

        // Output the current integer value as an unsigned hex byte.
        @DeciphererStep(name = "<byte> unsigned", sizeRequirement = 1, sizeAfter = 0)
        static final byte UNSIGNED_BYTE = 0x08;

        // Output the current integer value as an unsigned hex wyde.
        @DeciphererStep(name = "<wyde> unsigned", sizeRequirement = 2, sizeAfter = 0)
        static final byte UNSIGNED_WYDE = 0x09;

        // Output the current integer value as a signed hex byte.
        @DeciphererStep(name = "<byte> signed", sizeRequirement = 1, sizeAfter = 0)
        static final byte SIGNED_BYTE = 0x0A;

        // Output the current integer value as a signed hex wyde.
        @DeciphererStep(name = "<wyde> signed", sizeRequirement = 2, sizeAfter = 0)
        static final byte SIGNED_WYDE = 0x0B;

        // AND the current integer value with 0x38.
        @DeciphererStep(name = "and 0x38", sizeRequirement = -1)
        static final byte AND_0x38 = 0x0C;

        // AND the current integer value with 3.
        @DeciphererStep(name = "and 3", sizeRequirement = -1)
        static final byte AND_3 = 0x0D;

        // AND the current integer value with 7.
        @DeciphererStep(name = "and 7", sizeRequirement = -1)
        static final byte AND_7 = 0x0E;

        // Unsigned-shift the current integer value right by 3 bits.
        @DeciphererStep(name = "shr 3", sizeRequirement = -1)
        static final byte SHR_3 = 0x10;

        // Unsigned-shift the current integer value right by 4 bits.
        @DeciphererStep(name = "shr 4", sizeRequirement = -1)
        static final byte SHR_4 = 0x11;

        // Unsigned-shift the current integer value right by 5 bits.
        @DeciphererStep(name = "shr 5", sizeRequirement = -1)
        static final byte SHR_5 = 0x12;

        // Unsigned-shift the current integer value right by 6 bits.
        @DeciphererStep(name = "shr 6", sizeRequirement = -1)
        static final byte SHR_6 = 0x13;

        // Look the current integer value up in the minitable with the given
        // number,
        // and output the resulting string.
        static final byte MINITABLE_LOOKUP_0 = 0x18;
        static final int MAX_MINITABLE_COUNT = 8;

        // Fetch a byte or little-endian wyde, starting from the given offset
        // wrt the current instruction's
        // start, as the new current integer value.
        // Also updates the current instruction's length, if applicable.
        @DeciphererStep(name = "byte 0", sizeRequirement = 0, sizeAfter = 1)
        static final byte GET_BYTE_0 = (byte) 0x80;

        @DeciphererStep(name = "byte 1", sizeRequirement = 0, sizeAfter = 1)
        static final byte GET_BYTE_1 = (byte) 0x81;

        @DeciphererStep(name = "byte 2", sizeRequirement = 0, sizeAfter = 1)
        static final byte GET_BYTE_2 = (byte) 0x82;

        @DeciphererStep(name = "byte 3", sizeRequirement = 0, sizeAfter = 1)
        static final byte GET_BYTE_3 = (byte) 0x83;

        @DeciphererStep(name = "lewyde 0", sizeRequirement = 0, sizeAfter = 2)
        static final byte GET_LEWYDE_0 = (byte) 0x84;

        @DeciphererStep(name = "lewyde 1", sizeRequirement = 0, sizeAfter = 2)
        static final byte GET_LEWYDE_1 = (byte) 0x85;

        @DeciphererStep(name = "lewyde 2", sizeRequirement = 0, sizeAfter = 2)
        static final byte GET_LEWYDE_2 = (byte) 0x86;

        @DeciphererStep(name = "lewyde 3", sizeRequirement = 0, sizeAfter = 2)
        static final byte GET_LEWYDE_3 = (byte) 0x87;

        static final int MAX_SUBOFFSET = 3;

        // Re-dispatch according to a sublanguage:
        static final byte DISPATCH_0 = (byte) 0xA0;
        static final int MAX_REFERRED_LANGUAGE_COUNT = 8;
        
        // Declare entry point in a referred language:
        static final int ENTRY_POINT_0 = (byte) 0xB0;
        
        // Switches and temporary switches:
        static final byte TEMPSWITCH_0 = (byte) 0xA8;

        @DeciphererStep(name = "set-countdown 6", sizeRequirement = 0, sizeAfter = 0)
        static final byte SET_COUNTDOWN_6 = (byte) 0x89;
        
        @DeciphererStep(name = "set-countdown 8", sizeRequirement = 0, sizeAfter = 0)
        static final byte SET_COUNTDOWN_8 = (byte) 0x8A;
        
        @DeciphererStep(name = "set-countdown 12", sizeRequirement = 0, sizeAfter = 0)
        static final byte SET_COUNTDOWN_12 = (byte) 0x8B;
        
        @DeciphererStep(name = "switchback", sizeRequirement = 0, sizeAfter = 0)
        static final byte SWITCH_BACK = (byte) 0x92; // switch back to the last
                                                     // language

        @DeciphererStep(name = "terminate", sizeRequirement = 0, sizeAfter = 0)
        static final byte TERMINATE = (byte) 0x93; // stop after this
                                                   // instruction is complete.
        // (Note that TERMINATE does not imply COMPLETE.)

        // Reserved placeholder code
        static final byte INVALID = (byte) 0xFF;
        
        private static final Map<String, StepDeclaration> simpleSteps = new HashMap<String, StepDeclaration>();

        static {
            try {
                for (Field field : Bytecode.class.getDeclaredFields()) {
                    DeciphererStep ann = field.getAnnotation(DeciphererStep.class);
                    if (ann != null) {
                        assert !simpleSteps.containsKey(ann.name());
                        simpleSteps.put(ann.name(), new StepDeclaration(field.getByte(null), ann.sizeRequirement(),
                                ann.sizeAfter()));
                    }
                }
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("bug detected", e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("bug detected", e);
            }
        }

        static final StepDeclaration resolveSimpleStep(String step) {
            return simpleSteps.get(step);
        }

        static class StepDeclaration {
            final byte code;
            final int sizeRequirement;
            final int sizeAfter;

            StepDeclaration(int code, int sizeRequirement, int sizeAfter) {
                this.code = (byte) code;
                this.sizeRequirement = sizeRequirement;
                this.sizeAfter = sizeAfter;
            }

            final boolean typeMatches(int size) {
                if (sizeRequirement == -1) {
                    return size != 0;
                } else {
                    return size == sizeRequirement;
                }
            }
        }

        /**
         * Run the given disassembler bytecode.
         * 
         * @return the instruction's size
         * 
         * @throws Disassembler.IncompleteInstruction
         *             if the end of the binary object in the {@link Disassembler}
         *             is encountered before the instruction ends
         * @throws ClassicLang.UnknownOpcode
         *             if {@link Lang.Tabular} dispatch fails (which is not
         *             necessarily dispatch by the first byte in this instruction;
         *             some languages have instructions with multiple dispatches)
         */
        static final int decipher(byte[] code, int startPosition, ClassicLang.Tabular.Linkage linkage, DeciphererInput in, DeciphererOutput out) throws Disassembler.IncompleteInstruction, ClassicLang.UnknownOpcode {
            Disassembler.Maximiser currentInstructionSize = new Disassembler.Maximiser(0);
            int currentValue = 0;
            for (int i = startPosition;; i++) {
                byte step = code[i];
                if (step >= 0x20 && step <= 0x7E) {
                    out.append((char) step);
                } else if (step >= MINITABLE_LOOKUP_0
                        && step < MINITABLE_LOOKUP_0 + MAX_MINITABLE_COUNT) {
                    String[] minitable = linkage.minitables[step - MINITABLE_LOOKUP_0];
                    // note that we're checking this at the minitable construction
                    // time
                    assert minitable.length > 0 && (minitable.length & (minitable.length - 1)) == 0;
                    // mask off excess bits, then fetch a string from the minitable
                    out.append(minitable[currentValue & (minitable.length - 1)]);
                } else if (step >= DISPATCH_0
                        && step < DISPATCH_0 + MAX_REFERRED_LANGUAGE_COUNT) {
                    int suboffset = step - DISPATCH_0;
                    ClassicLang newLang = linkage.getReferredLanguage(suboffset);
                    int subsize = newLang.decipher(currentValue, in, out);
                    currentInstructionSize.feed(suboffset + subsize);
                } else if (step >= TEMPSWITCH_0
                        && step < TEMPSWITCH_0 + MAX_REFERRED_LANGUAGE_COUNT) {
                    ClassicLang newLang = linkage.getReferredLanguage(step - TEMPSWITCH_0);
                    out.switchTemporarily(newLang);
                } else if (step >= ENTRY_POINT_0
                        && step < ENTRY_POINT_0 + MAX_REFERRED_LANGUAGE_COUNT) {
                    ClassicLang lang = linkage.getReferredLanguage(step - ENTRY_POINT_0);
                    out.noteAbsoluteEntryPoint(currentValue, lang);
                } else if (step >= GET_BYTE_0 && step <= GET_BYTE_0 + MAX_SUBOFFSET) {
                    int suboffset = step - GET_BYTE_0;
                    currentValue = in.getUnsignedByte(suboffset);
                    currentInstructionSize.feed(suboffset + 1);
                } else if (step >= GET_LEWYDE_0 && step <= GET_LEWYDE_0 + MAX_SUBOFFSET) {
                    int suboffset = step - GET_LEWYDE_0;
                    currentValue = in.getUnsignedLewyde(suboffset);
                    currentInstructionSize.feed(suboffset + 2);
                } else {
                    switch (step) {
                        case SHR_3:
                            currentValue >>>= 3;
                            break;
        
                        case SHR_4:
                            currentValue >>>= 4;
                            break;
        
                        case SHR_5:
                            currentValue >>>= 5;
                            break;
        
                        case SHR_6:
                            currentValue >>>= 6;
                            break;
        
                        case ENTRY_POINT_REFERENCE:
                            out.noteAbsoluteEntryPoint(currentValue);
                            break;
        
                        case SUBROUTINE_ENTRY_POINT_REFERENCE:
                            out.noteAbsoluteEntryPoint(currentValue);
                            out.lookupAPI(currentValue);
                            break;
        
                        case UNSIGNED_BYTE:
                            out.append("0x");
                            out.append(Hex.b(currentValue));
                            break;
        
                        case UNSIGNED_WYDE:
                            out.append("0x");
                            out.append(Hex.w(currentValue));
                            break;
        
                        case SIGNED_BYTE:
                            if ((currentValue & 0x80) == 0) {
                                currentValue &= 0x7F;
                            } else {
                                currentValue |= ~0x7F;
                                currentValue = -currentValue;
                                out.append('-');
                            }
                            out.append("0x");
                            out.append(Hex.b(currentValue));
                            break;
        
                        case SIGNED_WYDE:
                            if ((currentValue & 0x8000) == 0) {
                                currentValue &= 0x7FFF;
                            } else {
                                currentValue |= ~0x7FFF;
                                currentValue = -currentValue;
                                out.append('-');
                            }
                            out.append("0x");
                            out.append(Hex.w(currentValue));
                            break;
        
                        case TERMINATE:
                            out.terminate();
                            break;
        
                        case SET_COUNTDOWN_6:
                            out.setCountdown(6);
                            break;
                            
                        case SET_COUNTDOWN_8:
                            out.setCountdown(8);
                            break;
                            
                        case SET_COUNTDOWN_12:
                            out.setCountdown(12);
                            break;
                            
                        case SWITCH_BACK:
                            out.switchBack();
                            break;
        
                        case BYTE_SIGNEDREL_1:
                            if ((currentValue & 0x80) == 0) {
                                currentValue &= 0x7F;
                            } else {
                                currentValue |= ~0x7F;
                            }
                            currentValue += in.getCurrentInstructionAddress() + 1;
                            break;
        
                        case BYTE_SIGNEDREL_2:
                            if ((currentValue & 0x80) == 0) {
                                currentValue &= 0x7F;
                            } else {
                                currentValue |= ~0x7F;
                            }
                            currentValue += in.getCurrentInstructionAddress() + 2;
                            break;
        
                        case AND_3:
                            currentValue &= 3;
                            break;
        
                        case AND_7:
                            currentValue &= 7;
                            break;
        
                        case AND_0x38:
                            currentValue &= 0x38;
                            break;
        
                        case DECIMAL:
                            out.append(currentValue);
                            break;
        
                        case COMPLETE:
                            return currentInstructionSize.get();
        
                        default:
                            throw new RuntimeException("bug detected");
                    }
                }
            }
        }

        public static abstract class DeciphererOutput {
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
    }

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