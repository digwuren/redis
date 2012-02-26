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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public final class Disassembler {
    private final byte[] data;
    private final Format format;
    private final API api;
    private final boolean[] undeciphered;
    private final Map<Integer, TreeSet<ClassicLang>> deciphered;
    private final Map<Integer, ArrayList<String>> problems;

    /**
     * Set of instructions referred by the format or other instructions.
     */
    private final HashSet<Integer> entryPoints;
    /**
     * Addresses that would be considered entry points because they are referred
     * to from the disassembled code but aren't because they lie outside the
     * code's boundaries. Multiple languages per interest point are supported.
     */
    private final TreeMap<Integer, TreeSet<ClassicLang>> externalPointsOfInterest;

    /** The breadth-first traversal queue */
    private final LinkedList<PendingEntryPoint> queue;

    // Disassembler's internal state
    private int currentOffset;
    private final LangSequencer sequencer;

    /*
     * Bytecode values for the internal bytecode. Note that the bytecode is
     * currently not considered a medium for data storage or external
     * communication; it *will* change significantly and is not suitable for
     * external data storage yet.
     */

    static final class Bytecode {
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
    }

    // Constructs a disassembler. The given format is used to determine the
    // data's loading
    // origin and the API (that is, the special subroutines and their meanings).
    public Disassembler(byte[] data, Format format) {
        this.data = data;
        this.format = format;
        api = (API) ((Format.Option.SimpleOption) format.getOption("api")).value;
        undeciphered = new boolean[data.length];
        for (int i = 0; i < data.length; i++) {
            undeciphered[i] = true;
        }
        deciphered = new HashMap<Integer, TreeSet<ClassicLang>>();
        problems = new HashMap<Integer, ArrayList<String>>();
        entryPoints = new HashSet<Integer>();
        queue = new LinkedList<PendingEntryPoint>();
        externalPointsOfInterest = new TreeMap<Integer, TreeSet<ClassicLang>>();
        currentOffset = -1;
        sequencer = new LangSequencer();
    }

    private final void addInstructionEntry(int offset, ClassicLang lang) {
        TreeSet<ClassicLang> point = deciphered.get(new Integer(offset));
        if (point == null) {
            point = new TreeSet<ClassicLang>();
            deciphered.put(new Integer(offset), point);
        }
        assert !point.contains(lang);
        point.add(lang);
    }

    final void recordProblem(String message) {
        ArrayList<String> point = problems.get(new Integer(currentOffset));
        if (point == null) {
            point = new ArrayList<String>();
            problems.put(new Integer(currentOffset), point);
        }
        point.add(message);
    }

    final boolean haveProcessed(int offset, ClassicLang lang) {
        TreeSet<ClassicLang> point = deciphered.get(new Integer(offset));
        return point != null && point.contains(lang);
    }

    final int getUnsignedLewyde(int suboffset) throws IncompleteInstruction {
        int base = currentOffset + suboffset;
        if (base < 0 || data.length - 2 < base) {
            throw new IncompleteInstruction();
        }
        return (data[base] & 0xFF) + (data[base + 1] & 0xFF) * 256;
    }

    final int getUnsignedByte(int suboffset) throws IncompleteInstruction {
        int base = currentOffset + suboffset;
        if (base < 0 || base >= data.length) {
            throw new IncompleteInstruction();
        }
        return data[base] & 0xFF;
    }

    public final void noteAbsoluteEntryPoint(int address, ClassicLang lang) {
        // special case
        if (lang == ClassicLang.NONE) {
            return;
        }
        int offset = address - format.getOrigin();
        if (offset >= 0 && offset < data.length) {
            entryPoints.add(new Integer(offset));
            if (!haveProcessed(offset, lang)) {
                queue.add(new PendingEntryPoint(offset, new LangSequencer.Frame[]{new LangSequencer.Frame(
                        lang.getDefaultCountdown(), lang)}));
            }
        } else {
            TreeSet<ClassicLang> set = externalPointsOfInterest.get(new Integer(address));
            if (set == null) {
                set = new TreeSet<ClassicLang>();
                externalPointsOfInterest.put(new Integer(address), set);
            }
            set.add(lang);
        }
    }

    public final void run() throws RuntimeException {
        DeciphererInput input = this.getDeciphererInput();
        WavingContext ctx = this.getWavingContext();
        while (!queue.isEmpty()) {
            PendingEntryPoint entryPoint = queue.removeFirst();
            currentOffset = entryPoint.offset;
            sequencer.init(entryPoint.sequencerFrames);
            DECIPHERING_LOOP : do {
                if (haveProcessed(currentOffset, sequencer.getCurrentLang())) {
                    break DECIPHERING_LOOP;
                }
                try {
                    try {
                        int instructionSize = sequencer.getCurrentLang().decipher(getUnsignedByte(0), input, ctx);
                        if (instructionSize < 1) {
                            instructionSize = 1;
                        }
                        addInstructionEntry(currentOffset, sequencer.getCurrentLang());
                        for (int i = 0; i < instructionSize; i++) {
                            undeciphered[currentOffset + i] = false;
                        }
                        currentOffset += instructionSize;
                        sequencer.advance();
                    } catch (ClassicLang.UnknownOpcode e) {
                        // FIXME: we should indicate to the user what the opcode
                        // is, and this should work in a reasonable way no
                        // matter its suboffset
                        this.recordProblem("unknown " + e.lang.name + " opcode");
                        break DECIPHERING_LOOP;
                    }
                } catch (IncompleteInstruction e) {
                    recordProblem("abrupt end of " + sequencer.getCurrentLang().name + " code");
                    break DECIPHERING_LOOP;
                }
            } while (sequencer.sequencerHasMore());
        }
    }

    /**
     * Run the given disassembler bytecode, determine the instruction's size, and mark referred entry
     * points. This is the wave-phase variant of the bytecode interpreter; it
     * does not generate output.
     * 
     * @return the instruction's size
     * 
     * @throws IncompleteInstruction
     *             if the end of the binary object in the {@link Disassembler}
     *             is encountered before the instruction ends
     * @throws ClassicLang.UnknownOpcode
     *             if {@link Lang.Tabular} dispatch fails (which is not
     *             necessarily dispatch by the first byte in this instruction;
     *             some languages have instructions with multiple dispatches)
     */
    static final int decipher(byte[] code, int startPosition, ClassicLang.Tabular.Linkage linkage, DeciphererInput input, WavingContext ctx) throws IncompleteInstruction, ClassicLang.UnknownOpcode {
        Maximiser currentInstructionSize = new Maximiser(0);
        int currentValue = 0;
        for (int i = startPosition;; i++) {
            byte step = code[i];
            if (step >= 0x20 && step <= 0x7E) {
                // ignore -- no output
            } else if (step >= Bytecode.MINITABLE_LOOKUP_0
                    && step < Bytecode.MINITABLE_LOOKUP_0 + Bytecode.MAX_MINITABLE_COUNT) {
                // ignore -- no output
            } else if (step >= Bytecode.DISPATCH_0
                    && step < Bytecode.DISPATCH_0 + Bytecode.MAX_REFERRED_LANGUAGE_COUNT) {
                int suboffset = step - Bytecode.DISPATCH_0;
                ClassicLang newLang = linkage.getReferredLanguage(suboffset);
                int subsize = newLang.decipher(currentValue, input, ctx);
                currentInstructionSize.feed(suboffset + subsize);
            } else if (step >= Bytecode.TEMPSWITCH_0
                    && step < Bytecode.TEMPSWITCH_0 + Bytecode.MAX_REFERRED_LANGUAGE_COUNT) {
                ClassicLang newLang = linkage.getReferredLanguage(step - Bytecode.TEMPSWITCH_0);
                ctx.switchTemporarily(newLang);
            } else if (step >= Bytecode.ENTRY_POINT_0
                    && step < Bytecode.ENTRY_POINT_0 + Bytecode.MAX_REFERRED_LANGUAGE_COUNT) {
                ClassicLang lang = linkage.getReferredLanguage(step - Bytecode.ENTRY_POINT_0);
                ctx.noteAbsoluteEntryPoint(currentValue, lang);
            } else if (step >= Bytecode.GET_BYTE_0 && step <= Bytecode.GET_BYTE_0 + Bytecode.MAX_SUBOFFSET) {
                int suboffset = step - Bytecode.GET_BYTE_0;
                currentValue = input.getUnsignedByte(suboffset);
                currentInstructionSize.feed(suboffset + 1);
            } else if (step >= Bytecode.GET_LEWYDE_0 && step <= Bytecode.GET_LEWYDE_0 + Bytecode.MAX_SUBOFFSET) {
                int suboffset = step - Bytecode.GET_LEWYDE_0;
                currentValue = input.getUnsignedLewyde(suboffset);
                currentInstructionSize.feed(suboffset + 2);
            } else {
                switch (step) {
                    case Bytecode.SHR_3:
                        currentValue >>>= 3;
                        break;

                    case Bytecode.SHR_4:
                        currentValue >>>= 4;
                        break;

                    case Bytecode.SHR_5:
                        currentValue >>>= 5;
                        break;

                    case Bytecode.SHR_6:
                        currentValue >>>= 6;
                        break;

                    case Bytecode.ENTRY_POINT_REFERENCE:
                        ctx.noteAbsoluteEntryPoint(currentValue);
                        break;

                    case Bytecode.SUBROUTINE_ENTRY_POINT_REFERENCE:
                        ctx.noteAbsoluteEntryPoint(currentValue);
                        ctx.lookupAPI(currentValue);
                        break;

                    case Bytecode.UNSIGNED_BYTE:
                    case Bytecode.UNSIGNED_WYDE:
                    case Bytecode.SIGNED_BYTE:
                    case Bytecode.SIGNED_WYDE:
                        // ignore -- no output
                        break;

                    case Bytecode.TERMINATE:
                        ctx.terminate();
                        break;

                    case Bytecode.SET_COUNTDOWN_6:
                        ctx.setCountdown(6);
                        break;
                        
                    case Bytecode.SET_COUNTDOWN_8:
                        ctx.setCountdown(8);
                        break;
                        
                    case Bytecode.SET_COUNTDOWN_12:
                        ctx.setCountdown(12);
                        break;
                        
                    case Bytecode.SWITCH_BACK:
                        ctx.switchBack();
                        break;

                    case Bytecode.BYTE_SIGNEDREL_1:
                        if ((currentValue & 0x80) == 0) {
                            currentValue &= 0x7F;
                        } else {
                            currentValue |= ~0x7F;
                        }
                        currentValue += input.getCurrentInstructionAddress() + 1;
                        break;

                    case Bytecode.BYTE_SIGNEDREL_2:
                        if ((currentValue & 0x80) == 0) {
                            currentValue &= 0x7F;
                        } else {
                            currentValue |= ~0x7F;
                        }
                        currentValue += input.getCurrentInstructionAddress() + 2;
                        break;

                    case Bytecode.AND_3:
                        currentValue &= 3;
                        break;

                    case Bytecode.AND_7:
                        currentValue &= 7;
                        break;

                    case Bytecode.AND_0x38:
                        currentValue &= 0x38;
                        break;

                    case Bytecode.DECIMAL:
                        // ignore -- no output
                        break;

                    case Bytecode.COMPLETE:
                        return currentInstructionSize.get();

                    default:
                        throw new RuntimeException("bug detected");
                }
            }
        }
    }

    /**
     * Run the given disassembler bytecode, append the output to the given {@link StringBuilder}, determine
     * the instruction's size, and mark referred entry points. This is the
     * output generation phase variant; it does not mark the entry points or
     * affect the sequencer.
     * 
     * @return the instruction's size
     * 
     * @throws IncompleteInstruction
     *             if the end of the binary object in the {@link Disassembler}
     *             is encountered before the instruction ends
     * @throws ClassicLang.UnknownOpcode
     *             if {@link Lang.Tabular} dispatch fails (which is not
     *             necessarily dispatch by the first byte in this instruction;
     *             some languages have instructions with multiple dispatches)
     */
    static final int decipher(byte[] code, int startPosition, ClassicLang.Tabular.Linkage linkage, DeciphererInput input, StringBuilder sb) throws IncompleteInstruction, ClassicLang.UnknownOpcode {
        Maximiser currentInstructionSize = new Maximiser(0);
        int currentValue = 0;
        for (int i = startPosition;; i++) {
            byte step = code[i];
            if (step >= 0x20 && step <= 0x7E) {
                sb.append((char) step);
            } else if (step >= Bytecode.MINITABLE_LOOKUP_0
                    && step < Bytecode.MINITABLE_LOOKUP_0 + Bytecode.MAX_MINITABLE_COUNT) {
                String[] minitable = linkage.minitables[step - Bytecode.MINITABLE_LOOKUP_0];
                // note that we're checking this at the minitable construction
                // time
                assert minitable.length > 0 && (minitable.length & (minitable.length - 1)) == 0;
                // mask off excess bits, then fetch a string from the minitable
                sb.append(minitable[currentValue & (minitable.length - 1)]);
            } else if (step >= Bytecode.DISPATCH_0
                    && step < Bytecode.DISPATCH_0 + Bytecode.MAX_REFERRED_LANGUAGE_COUNT) {
                int suboffset = step - Bytecode.DISPATCH_0;
                ClassicLang newLang = linkage.getReferredLanguage(suboffset);
                int subsize = newLang.decipher(currentValue, input, sb);
                currentInstructionSize.feed(suboffset + subsize);
            } else if (step >= Bytecode.TEMPSWITCH_0
                    && step < Bytecode.TEMPSWITCH_0 + Bytecode.MAX_REFERRED_LANGUAGE_COUNT) {
                // ignore in output generation phase
            } else if (step >= Bytecode.ENTRY_POINT_0
                    && step < Bytecode.ENTRY_POINT_0 + Bytecode.MAX_REFERRED_LANGUAGE_COUNT) {
                // ignore in output generation phase
            } else if (step >= Bytecode.GET_BYTE_0 && step <= Bytecode.GET_BYTE_0 + Bytecode.MAX_SUBOFFSET) {
                int suboffset = step - Bytecode.GET_BYTE_0;
                currentValue = input.getUnsignedByte(suboffset);
                currentInstructionSize.feed(suboffset + 1);
            } else if (step >= Bytecode.GET_LEWYDE_0 && step <= Bytecode.GET_LEWYDE_0 + Bytecode.MAX_SUBOFFSET) {
                int suboffset = step - Bytecode.GET_LEWYDE_0;
                currentValue = input.getUnsignedLewyde(suboffset);
                currentInstructionSize.feed(suboffset + 2);
            } else {
                switch (step) {
                    case Bytecode.SHR_3:
                        currentValue >>>= 3;
                        break;
    
                    case Bytecode.SHR_4:
                        currentValue >>>= 4;
                        break;
    
                    case Bytecode.SHR_5:
                        currentValue >>>= 5;
                        break;
    
                    case Bytecode.SHR_6:
                        currentValue >>>= 6;
                        break;
    
                    case Bytecode.ENTRY_POINT_REFERENCE:
                    case Bytecode.SUBROUTINE_ENTRY_POINT_REFERENCE:
                        // ignore in output generation phase
                        break;
    
                    case Bytecode.UNSIGNED_BYTE:
                        sb.append("0x");
                        sb.append(Hex.b(currentValue));
                        break;
    
                    case Bytecode.UNSIGNED_WYDE:
                        sb.append("0x");
                        sb.append(Hex.w(currentValue));
                        break;
    
                    case Bytecode.SIGNED_BYTE:
                        if ((currentValue & 0x80) == 0) {
                            currentValue &= 0x7F;
                        } else {
                            currentValue |= ~0x7F;
                            currentValue = -currentValue;
                            sb.append('-');
                        }
                        sb.append("0x");
                        sb.append(Hex.b(currentValue));
                        break;
    
                    case Bytecode.SIGNED_WYDE:
                        if ((currentValue & 0x8000) == 0) {
                            currentValue &= 0x7FFF;
                        } else {
                            currentValue |= ~0x7FFF;
                            currentValue = -currentValue;
                            sb.append('-');
                        }
                        sb.append("0x");
                        sb.append(Hex.w(currentValue));
                        break;
    
                    case Bytecode.TERMINATE:
                    case Bytecode.SWITCH_BACK:
                    case Bytecode.SET_COUNTDOWN_6:
                    case Bytecode.SET_COUNTDOWN_8:
                    case Bytecode.SET_COUNTDOWN_12:
                        // ignore in output generation phase
                        break;
    
                    case Bytecode.BYTE_SIGNEDREL_1:
                        if ((currentValue & 0x80) == 0) {
                            currentValue &= 0x7F;
                        } else {
                            currentValue |= ~0x7F;
                        }
                        currentValue += input.getCurrentInstructionAddress() + 1;
                        break;
    
                    case Bytecode.BYTE_SIGNEDREL_2:
                        if ((currentValue & 0x80) == 0) {
                            currentValue &= 0x7F;
                        } else {
                            currentValue |= ~0x7F;
                        }
                        currentValue += input.getCurrentInstructionAddress() + 2;
                        break;
    
                    case Bytecode.AND_3:
                        currentValue &= 3;
                        break;
    
                    case Bytecode.AND_7:
                        currentValue &= 7;
                        break;
    
                    case Bytecode.AND_0x38:
                        currentValue &= 0x38;
                        break;
    
                    case Bytecode.DECIMAL:
                        sb.append(currentValue);
                        break;
    
                    case Bytecode.COMPLETE:
                        return currentInstructionSize.get();
    
                    default:
                        throw new RuntimeException("bug detected");
                }
            }
        }
    }

    /**
     * Print results of the disassembly to given port.
     * 
     * @param port
     */
    public final void printResults(PrintStream port) {
        DeciphererInput input = this.getDeciphererInput();
        TreeSet<Integer> decipheredKeys = new TreeSet<Integer>(deciphered.keySet());
        decipheredKeys.addAll(problems.keySet());
        int lastOffset = 0;
        ClassicLang lastLang = ClassicLang.NONE;
        for (Integer boxedOffset : decipheredKeys) {
            int offset = boxedOffset.intValue();
            if (offset > lastOffset) {
                port.println();
                lastOffset = offset;
            }
            ArrayList<String> pointProblems = problems.get(boxedOffset);
            if (pointProblems != null) {
                for (String message : pointProblems) {
                    port.println("          ! " + message);
                }
            }
            TreeSet<ClassicLang> instructions = deciphered.get(boxedOffset);
            if (instructions != null) {
                for (ClassicLang lang : instructions) {
                    currentOffset = offset;
                    sequencer.switchPermanently(lang);
                    sequencer.setCountdown(1);
                    StringBuilder sb = new StringBuilder();
                    int size;
                    try {
                        size = lang.decipher(getUnsignedByte(0), input, sb);
                        if (size < 1) {
                            size = 1;
                        }
                    } catch (ClassicLang.UnknownOpcode e) {
                        throw new RuntimeException("bug detected", e);
                    } catch (IncompleteInstruction e) {
                        throw new RuntimeException("bug detected", e);
                    }
                    DecipheredInstruction instruction = new DecipheredInstruction(size, sb.toString());
                    if (offset < lastOffset) {
                        port.println("          ! retreat " + (lastOffset - offset));
                        lastOffset = offset;
                    }
                    if (!lang.equals(lastLang) && !lang.isTrivial()) {
                        port.println("          .switch " + lang.name);
                        lastLang = lang;
                    }
                    if (entryPoints.contains(new Integer(offset))) {
                        port.print(Hex.t(offset + format.getOrigin()));
                    } else {
                        port.print("      " + Hex.b(offset + format.getOrigin()));
                    }
                    port.println(": " + instruction.asString);
                    lastOffset = offset + instruction.size;
                }
            }
        }
        port.println();
        port.println("External points of interest:");
        if (externalPointsOfInterest.isEmpty()) {
            port.println("    (none)");
        } else {
            for (Map.Entry<Integer, TreeSet<ClassicLang>> entry : externalPointsOfInterest.entrySet()) {
                port.print(Hex.t(entry.getKey().intValue()) + ": ");
                boolean first = true;
                for (ClassicLang lang : entry.getValue()) {
                    if (!first) {
                        port.print(", ");
                    }
                    port.print(lang.name);
                    first = false;
                }
                port.println();
            }
        }
        port.println();
        Hex.dump(data, format.getOrigin(), format.getDecoding(), undeciphered, port);
    }

    public final DeciphererInput getDeciphererInput() {
        return new DeciphererInput();
    }

    public final WavingContext getWavingContext() {
        return new WavingContext();
    }
    
    // An offset-lang pair, used to queue entry points not yet processed.
    static final class PendingEntryPoint {
        final int offset;
        final LangSequencer.Frame[] sequencerFrames;

        PendingEntryPoint(int offset, LangSequencer.Frame[] sequencerFrames) {
            this.offset = offset;
            this.sequencerFrames = sequencerFrames;
        }
    }

    // A size-string pair, used to store disassembled instructions.
    static final class DecipheredInstruction {
        final int size;
        final String asString;

        DecipheredInstruction(int size, String asString) {
            this.size = size;
            this.asString = asString;
        }
    }

    static abstract class SequencerEffect {
        abstract void affectSequencer(LangSequencer sequencer);

        static final class Terminate extends SequencerEffect {
            @Override
            final void affectSequencer(LangSequencer sequencer) {
                sequencer.terminate();
            }
        }

        static final class SwitchPermanently extends SequencerEffect {
            private final ClassicLang lang;

            SwitchPermanently(ClassicLang lang) {
                this.lang = lang;
            }

            @Override
            final void affectSequencer(LangSequencer sequencer) {
                sequencer.switchPermanently(lang);
            }
        }

        static final class SwitchTemporarily extends SequencerEffect {
            private final ClassicLang lang;

            SwitchTemporarily(ClassicLang lang) {
                this.lang = lang;
            }

            @Override
            final void affectSequencer(LangSequencer sequencer) {
                sequencer.switchTemporarily(lang);
            }
        }
    }

    static final class API {
        final String name;
        private final Map<Integer, SequencerEffect> vectors;

        public API(String name, BufferedReader reader) throws IOException {
            this.name = name;
            vectors = new HashMap<Integer, SequencerEffect>();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0 || line.charAt(0) == '#') {
                    continue;
                }
                String[] fields = line.trim().split("\\s+");
                try {
                    int address = ParseUtil.parseUnsignedInteger(fields[0]);
                    if (vectors.containsKey(new Integer(address))) {
                        throw new RuntimeException("duplicate API vector declaration \"" + line + "\"");
                    }
                    if (fields.length < 2) {
                        throw new RuntimeException("invalid API vector declaration \"" + line + "\"");
                    }
                    SequencerEffect effect;
                    if (fields[1].equals("terminate")) {
                        if (fields.length > 2) {
                            throw new RuntimeException("invalid API vector declaration \"" + line + "\"");
                        }
                        effect = new SequencerEffect.Terminate();
                    } else if (fields[1].equals("switch-temporarily")) {
                        if (fields.length > 3) {
                            throw new RuntimeException("invalid API vector declaration \"" + line + "\"");
                        }
                        // this will throw exception if the lang is unknown
                        ClassicLang newLang = ClassicLang.MANAGER.get(fields[2]);
                        effect = new SequencerEffect.SwitchTemporarily(newLang);
                    } else if (fields[1].equals("switch-permanently")) {
                        if (fields.length > 3) {
                            throw new RuntimeException("invalid API vector declaration \"" + line + "\"");
                        }
                        // this will throw exception if the lang is unknown
                        ClassicLang newLang = ClassicLang.MANAGER.get(fields[2]);
                        effect = new SequencerEffect.SwitchPermanently(newLang);
                    } else {
                        throw new RuntimeException("invalid API vector declaration \"" + line + "\"");
                    }
                    vectors.put(new Integer(address), effect);
                } catch (ResourceManager.ResolutionError e) {
                    throw new RuntimeException("reference to unknown language in API vector declaration \"" + line
                            + "\"", e);
                } catch (NumberFormatException e) {
                    throw new RuntimeException(
                            "invalid hexadecimal address in API vector declaration \"" + line + "\"", e);
                }
            }
        }

        final SequencerEffect getSequencerEffect(int vector) {
            return vectors.get(new Integer(vector));
        }

        public static final ResourceManager<API> MANAGER = new ResourceManager<API>("api") {
            @Override
            public final API load(String name, BufferedReader reader) throws IOException, RuntimeException {
                return new API(name, reader);
            }
        };
    }

    // Thrown when the decipherer attempts to access a byte beyond the end of
    // the byte vector.
    // Disassembler.run() catches it and reports "abrupt end of code" to the
    // user.
    static final class IncompleteInstruction extends Exception {
        //
    }

    static final class LangSequencer {
        private ClassicLang currentLang;
        private LinkedList<Frame> stack;

        LangSequencer() {
            currentLang = null;
            stack = new LinkedList<Frame>();
        }

        final void init(Frame[] sequencerFrames) {
            currentLang = null;
            terminate();
            for (Frame newFrame : sequencerFrames) {
                stack.add(newFrame);
            }
        }

        ClassicLang getCurrentLang() {
            if (currentLang == null) {
                if (!stack.isEmpty()) {
                    Frame topFrame = stack.getLast();
                    currentLang = topFrame.lang;
                    if (topFrame.takeOneDown()) {
                        stack.removeLast();
                    }
                } else {
                    currentLang = ClassicLang.NONE;
                }
            }
            return currentLang;
        }

        /**
         * Clear the sequencer stack. This has the effect of terminating
         * disassembly of the current sequence after the current instruction has
         * been deciphered.
         */
        final void terminate() {
            stack.clear();
        }

        // note that the switch does not happen immediately but
        // after the next call to advance()
        final void switchPermanently(ClassicLang newLang) {
            assert newLang != null;
            terminate();
            switchTemporarily(newLang);
        }

        final void switchTemporarily(ClassicLang newLang) {
            assert newLang != null;
            assert newLang.getDefaultCountdown() >= 0;
            stack.addLast(new Frame(newLang.getDefaultCountdown(), newLang));
        }

        final void switchBack() {
            if (!stack.isEmpty()) {
                stack.removeLast();
            }
        }

        final void advance() {
            currentLang = null; // forcing the next call to getCurrentLang() to
                                // perform actual advance
        }

        final boolean sequencerHasMore() {
            return getCurrentLang() != ClassicLang.NONE;
        }

        /**
         * Get a copy of the stack, for use in to decode a code sequence
         * starting from a branch destination.
         * 
         * @return generated copy as an array of {@link Frame} instances
         */
        final Frame[] getStack() {
            Frame[] copy = new Frame[stack.size()];
            int i = 0;
            for (Frame frame : stack) {
                copy[i++] = frame.dup();
            }
            return copy;
        }

        final void setCountdown(int newCountdown) {
            assert newCountdown >= 0;
            stack.getLast().countdown = newCountdown;
        }

        static final class Frame {
            int countdown;
            final ClassicLang lang;

            Frame(int countdown, ClassicLang lang) {
                this.countdown = countdown;
                this.lang = lang;
            }

            /**
             * Create a duplicate of this frame. An indefinite frame -- one with
             * countdown being zero -- is considered immutable, and as an
             * optimisation, we won't allocate a new Frame instance for such.
             */
            final Frame dup() {
                if (countdown > 0) {
                    return new Frame(countdown, lang);
                } else {
                    return this;
                }
            }

            /**
             * If this frame has an active countdown, decrement it.
             * 
             * @return whether the frame has run out of repetitions.
             */
            final boolean takeOneDown() {
                return countdown > 0 && --countdown == 0;
            }
        }
    }
    
    @SuppressWarnings("synthetic-access")
    final class DeciphererInput {
        public final int getUnsignedByte(int suboffset) throws IncompleteInstruction {
            return Disassembler.this.getUnsignedByte(suboffset);
        }

        public final int getUnsignedLewyde(int suboffset) throws IncompleteInstruction {
            return Disassembler.this.getUnsignedLewyde(suboffset);
        }

        public final int getCurrentInstructionAddress() {
            return Disassembler.this.format.getOrigin() + Disassembler.this.currentOffset;
        }
    }

    @SuppressWarnings("synthetic-access")
    final class WavingContext {
        public final void switchTemporarily(ClassicLang newLang) {
            sequencer.switchTemporarily(newLang);
        }

        public final void noteAbsoluteEntryPoint(int address) {
            noteAbsoluteEntryPoint(address, sequencer.getCurrentLang());
        }

        public final void noteAbsoluteEntryPoint(int address, ClassicLang lang) {
            Disassembler.this.noteAbsoluteEntryPoint(address, lang);
        }

        public final void terminate() {
            sequencer.switchPermanently(ClassicLang.NONE);
        }

        public final void lookupAPI(int currentValue) {
            /*
             * XXX: Note that we don't care which language is used
             * to call the API entry point; all can cause the
             * switch. This can theoretically cause false positives.
             * In practice, they would be quite convoluted and
             * reasonably unlikely.
             */
            SequencerEffect effect = api.getSequencerEffect(currentValue);
            if (effect != null) {
                effect.affectSequencer(sequencer);
            }
        }

        public final void setCountdown(int newCountdown) {
            sequencer.setCountdown(newCountdown);
        }

        public final void switchBack() {
            sequencer.switchBack();
        }
    }
    
    public static final class Maximiser {
        private int value;

        public Maximiser(int value) {
            this.value = value;
        }
        
        public final void reset(int newValue) {
            value = newValue;
        }

        public final int get() {
            return value;
        }

        final void feed(int newValue) {
            if (newValue > value) {
                value = newValue;
            }
        }
    }
}