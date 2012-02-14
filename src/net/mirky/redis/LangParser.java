package net.mirky.redis;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.mirky.redis.ControlData.LineParseError;
import net.mirky.redis.Disassembler.Lang.Tabular.BytecodeCollector;

final class LangParser {
    byte[] bytecode;
    private final BytecodeCollector coll;
    final int[] dispatchTable;
    final Disassembler.Lang.Tabular.Linkage linkage;
    final Map<String, Integer> minitablesByName;
    private int minitableCounter;
    final Map<String, Integer> referredLanguagesByName;
    int referredLanguageCounter;
    int dispatchSuboffset;
    int defaultCountdown;
    boolean trivial;
    private final List<MinitableReferencePatch> minitableReferencePatches;

    LangParser() {
        dispatchTable = new int[256];
        for (int i = 0; i < 256; i++) {
            dispatchTable[i] = -1;
        }
        coll = new BytecodeCollector();
        linkage = new Disassembler.Lang.Tabular.Linkage();
        minitablesByName = new HashMap<String, Integer>();
        minitableCounter = 0;
        referredLanguagesByName = new HashMap<String, Integer>();
        referredLanguageCounter = 0;
        dispatchSuboffset = 0;
        defaultCountdown = 0; // by default, no default countdown
        trivial = false; // by default, not trivial
        minitableReferencePatches = new ArrayList<MinitableReferencePatch>();
    }

    final void parse(String name, BufferedReader reader) throws IOException, DisassemblyTableParseError, LineParseError {
        Set<String> knownHeaderItems = new TreeSet<String>();
        // note that the membership is checked with a downcased specimen
        knownHeaderItems.add("dispatch-suboffset");
        knownHeaderItems.add("default-countdown");
        knownHeaderItems.add("trivial");
        Set<String> seenHeaderLines = new TreeSet<String>();
        
        ParseUtil.IndentationSensitiveLexer lexer = new ParseUtil.IndentationSensitiveLexer(new ParseUtil.FileLineSource(reader, name), '#');
        String itemName = null;
        try {
            try {
                while (lexer.atWord() && knownHeaderItems.contains((itemName = lexer.peekThisDashedWord()).toLowerCase())) {
                    if (seenHeaderLines.contains(itemName)) {
                        throw new DisassemblyTableParseError("duplicate lang header item " + itemName);
                    }
                    seenHeaderLines.add(itemName);
                    lexer.parseThisDashedWord();
                    lexer.skipSpaces();
                    lexer.pass(':');
                    // Note that comments are not ignored after header items.
                    lexer.skipSpaces();
                    processHeader(itemName, lexer.parseRestOfLine());
                    lexer.passNewline();
                }
            } catch (NumberFormatException e) {
                throw new DisassemblyTableParseError("error parsing lang header item " + itemName, e);
            } catch (ControlData.LineParseError e) {
                throw new DisassemblyTableParseError("error parsing lang header item " + itemName, e);
            }
            try {
                lexer.passDashedWord("dispatch");
                parseDispatchTable(lexer);
                while (!lexer.atEndOfFile()) {
                    lexer.noIndent();
                    if (lexer.passOptDashedWord("minitable")) {
                        parseMinitableDeclaration(lexer);
                    } else {
                        lexer.complain("expected end of file");
                    }
                }
            } catch (ControlData.LineParseError e) {
                throw new DisassemblyTableParseError("error parsing lang description " + name, e);
            }
        } finally {
            reader.close();
        }
        
        bytecode = coll.finish();
        for (MinitableReferencePatch patch : minitableReferencePatches) {
            patch.apply();
        }
    }

    // Called by {@code parse(...)} for each header name-value pair. Guaranteed
    // to be called at most once per name.
    private final void processHeader(String name, String value) throws NumberFormatException, DisassemblyTableParseError {
        if (name.equalsIgnoreCase("Dispatch-suboffset")) {
            dispatchSuboffset = Integer.parseInt(value);
        } else if (name.equalsIgnoreCase("Default-countdown")) {
            defaultCountdown = Integer.parseInt(value);
        } else if (name.equalsIgnoreCase("Trivial")) {
            trivial = parseBoolean(value);
        } else {
            throw new DisassemblyTableParseError("unknown lang file header item " + name);
        }
    }

    private static final boolean parseBoolean(String value) throws DisassemblyTableParseError {
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        throw new DisassemblyTableParseError("not a Boolean value: " + value);
    }

    private final void parseMinitableDeclaration(ParseUtil.IndentationSensitiveLexer lexer) throws LineParseError,
            IOException, DisassemblyTableParseError {
        lexer.skipSpaces();
        String tableName = lexer.parseDashedWord("minitable name");
        if (minitablesByName.containsKey(tableName)) {
            lexer.complain("duplicate minitable name");
        }
        lexer.skipSpaces();
        lexer.pass(':');
        lexer.skipSpaces();
        List<String> minitable = new ArrayList<String>();
        while (lexer.atWord()) {
            minitable.add(lexer.parseThisDashedWord());
            lexer.skipSpaces();
        }
        // minitable size must be a power of two
        // (so that we can mask off excess high bits meaningfully)
        if (minitable.size() == 0 || (minitable.size() & (minitable.size() - 1)) != 0) {
            throw new DisassemblyTableParseError("invalid minitable size for " + tableName);
        }
        if (minitableCounter >= Disassembler.Bytecode.MAX_MINITABLE_COUNT) {
            throw new RuntimeException("too many minitables");
        }
        minitablesByName.put(tableName, new Integer(minitableCounter));
        linkage.minitables[minitableCounter++] = minitable.toArray(new String[0]);
        lexer.passNewline();
    }

    private final void parseDispatchTable(ParseUtil.IndentationSensitiveLexer lexer) throws LineParseError, IOException,
            RuntimeException, DisassemblyTableParseError {
        lexer.passNewline();
        lexer.passIndent();
        while (!lexer.atDedent()) {
            lexer.noIndent();
            lexer.pass('[');
            lexer.skipSpaces();
            CodeSet set = CodeSet.parse(lexer);
            lexer.skipSpaces();
            lexer.pass(']');
            lexer.skipSpaces();
            for (int i = 0; i < 256; i++) {
                if (set.matches(i)) {
                    if (dispatchTable[i] != -1) {
                        throw new DisassemblyTableParseError("duplicate decipherer for 0x" + Hex.b(i));
                    }
                    dispatchTable[i] = coll.currentPosition();
                }
            }
            int size = 0;
            while (!lexer.hor.atEndOfLine()) {
                char c = lexer.getChar();
                if (c != '<') {
                    if (c < 0x20 || c > 0x7E) {
                        throw new RuntimeException("invalid literal character code 0x" + Hex.w(c));
                    }
                    coll.add((byte) c);
                } else {
                    try {
                        size = 0;
                        do {
                            lexer.skipSpaces();
                            if (lexer.hor.atEndOfLine()) {
                                lexer.complain("missing '>'");
                            }
                            String step = lexer.passUntilDelimiter(">,").trim();
                            size = parseProcessingStep(step, size);
                        } while (lexer.passOpt(','));
                        lexer.pass('>');
                        if (size != 0) {
                            throw new DisassemblyTableParseError("final step missing");
                        }
                    } catch (DisassemblyTableParseError e) {
                        throw new RuntimeException("error parsing opcode decipherer", e);
                    }
                }
            }
            coll.add(Disassembler.Bytecode.COMPLETE);
            lexer.passNewline();
        }
        lexer.skipThisDedent();
    }

    /**
     * Determines the index of the given referred language. Adds
     * this language to the referred language list if it is not
     * already found. Throws an exception if the referred
     * language list is full.
     */
    private final int resolveReferredLanguage(String newLangName) {
        Integer index = referredLanguagesByName.get(newLangName);
        if (index == null) {
            if (referredLanguageCounter >= Disassembler.Bytecode.MAX_REFERRED_LANGUAGE_COUNT) {
                throw new RuntimeException("too many referred languages");
            }
            linkage.referredLanguages[referredLanguageCounter] = newLangName;
            referredLanguagesByName.put(newLangName, index);
            return referredLanguageCounter++;
        } else {
            return index.intValue();
        }
    }

    final int parseProcessingStep(String step, int size) throws DisassemblyTableParseError, RuntimeException {
        Disassembler.Bytecode.StepDeclaration resolvedStep = Disassembler.Bytecode.resolveInitialStep(step);
        if (resolvedStep != null) {
            if (!resolvedStep.typeMatches(size)) {
                throw new DisassemblyTableParseError("type mismatch for step " + step);
            }
            coll.add(resolvedStep.code);
            if (resolvedStep.sizeAfter != -1) {
                size = resolvedStep.sizeAfter;
            }
        } else {
            if (step.startsWith("tempswitch ")) {
                String newLangName = step.substring(11).trim();
                coll.add((byte) (Disassembler.Bytecode.TEMPSWITCH_0 + resolveReferredLanguage(newLangName)));
                size = 0;
            } else {
                if (size == 0) {
                    throw new DisassemblyTableParseError("attempt to process void value");
                }
                if (step.equals("unsigned")) {
                    switch (size) {
                        case 1:
                            coll.add(Disassembler.Bytecode.UNSIGNED_BYTE);
                            break;
                        case 2:
                            coll.add(Disassembler.Bytecode.UNSIGNED_WYDE);
                            break;
                        default:
                            throw new RuntimeException("bug detected");
                    }
                    size = 0;
                } else if (step.equals("signed")) {
                    switch (size) {
                        case 1:
                            coll.add(Disassembler.Bytecode.SIGNED_BYTE);
                            break;
                        case 2:
                            coll.add(Disassembler.Bytecode.SIGNED_WYDE);
                            break;
                        default:
                            throw new RuntimeException("bug detected");
                    }
                    size = 0;
                } else if (step.equals("and 0x38")) {
                    coll.add(Disassembler.Bytecode.AND_0x38);
                } else if (step.equals("and 3")) {
                    coll.add(Disassembler.Bytecode.AND_3);
                } else if (step.equals("and 7")) {
                    coll.add(Disassembler.Bytecode.AND_7);
                } else if (step.equals("decimal")) {
                    coll.add(Disassembler.Bytecode.DECIMAL);
                    size = 0;
                } else if (step.startsWith("dispatch ")) {
                    String newLangName = step.substring(9).trim();
                    coll.add((byte) (Disassembler.Bytecode.DISPATCH_0 + resolveReferredLanguage(newLangName)));
                    size = 0;
                } else {
                    int position = coll.currentPosition();
                    coll.add((Disassembler.Bytecode.INVALID));
                    size = 0;
                    
                    MinitableReferencePatch patch = new MinitableReferencePatch(position, step);
                    minitableReferencePatches.add(patch);
                }
            }
        }
        return size;
    }

    class MinitableReferencePatch {
        public final int position;
        public final String minitableName;
        
        public MinitableReferencePatch(int position, String minitableName) {
            this.position = position;
            this.minitableName = minitableName;
        }
    
        public final void apply() throws DisassemblyTableParseError {
            if (bytecode[position] != Disassembler.Bytecode.INVALID) {
                throw new RuntimeException("bug detected");
            }
            if (!minitablesByName.containsKey(minitableName)) {
                throw new DisassemblyTableParseError("unknown minitable: " + minitableName);
            }
            int minitableNumber = minitablesByName.get(minitableName).intValue();
            assert minitableNumber < Disassembler.Bytecode.MAX_MINITABLE_COUNT;
            bytecode[position] = (byte) (Disassembler.Bytecode.MINITABLE_LOOKUP_0 + minitableNumber);
        }
    }

    /*
     * An instance of this with a brief message is thrown internally
     * when lang file parsing fails. Considering that all our lang files
     * are considered internal to the project, this is not supposed to
     * happen, so we catch all our {@link DisassemblyTableParseError}:s
     * and throw a RuntimeException to the caller instead (but we'll
     * retain the {@link DisassemblyTableParseError} as a cause).
     */
    static final class DisassemblyTableParseError extends Exception {
        DisassemblyTableParseError(String msg) {
            super(msg);
        }

        public DisassemblyTableParseError(String msg, Exception cause) {
            super(msg, cause);
        }
    }
}