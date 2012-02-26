package net.mirky.redis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import net.mirky.redis.ClassicLang.Bytecode.DeciphererOutput;

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
                        int instructionSize = sequencer.getCurrentLang().decipher(input, ctx);
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
                        size = lang.decipher(input, new DeciphererOutputStringBuilder(sb));
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
    final class WavingContext extends DeciphererOutput {
        @Override
        public final void switchTemporarily(ClassicLang newLang) {
            sequencer.switchTemporarily(newLang);
        }

        @Override
        public final void noteAbsoluteEntryPoint(int address) {
            noteAbsoluteEntryPoint(address, sequencer.getCurrentLang());
        }

        @Override
        public final void noteAbsoluteEntryPoint(int address, ClassicLang lang) {
            Disassembler.this.noteAbsoluteEntryPoint(address, lang);
        }

        @Override
        public final void terminate() {
            sequencer.switchPermanently(ClassicLang.NONE);
        }

        @Override
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

        @Override
        public final void setCountdown(int newCountdown) {
            sequencer.setCountdown(newCountdown);
        }

        @Override
        public final void switchBack() {
            sequencer.switchBack();
        }

        @Override
        public final void append(int i) {
            // no effect in the waving phase
        }

        @Override
        public final void append(String s) {
            // no effect in the waving phase
        }

        @Override
        public final void append(char c) {
            // no effect in the waving phase
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