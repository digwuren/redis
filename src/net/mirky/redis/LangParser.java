package net.mirky.redis;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class LangParser {
    byte[] bytecode;
    private final ClassicLang.Tabular.BytecodeCollector coll;
    final int[] dispatchTable;
    final ClassicLang.Tabular.Linkage linkage;
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
        coll = new ClassicLang.Tabular.BytecodeCollector();
        linkage = new ClassicLang.Tabular.Linkage();
        minitablesByName = new HashMap<String, Integer>();
        minitableCounter = 0;
        referredLanguagesByName = new HashMap<String, Integer>();
        referredLanguageCounter = 0;
        dispatchSuboffset = 0;
        defaultCountdown = 0; // by default, no default countdown
        trivial = false; // by default, not trivial
        minitableReferencePatches = new ArrayList<MinitableReferencePatch>();
    }

    final void parse(String name, BufferedReader reader) throws IOException {
        Set<String> knownHeaderItems = new TreeSet<String>();
        // note that the membership is checked with a downcased specimen
        knownHeaderItems.add("default-countdown");
        knownHeaderItems.add("trivial");
        Set<String> seenHeaderLines = new TreeSet<String>();
        
        ParseUtil.IndentableLexer lexer = new ParseUtil.IndentableLexer(new ParseUtil.LineSource.File(reader), new ParseUtil.ErrorLocator(name, 0), '#');
        try {
            while (true) {
                if (!lexer.atAlphanumeric()) {
                    break;
                }
                int posBeforeItemName = lexer.getPos();
                String itemName = lexer.peekDashedWord(null);
                if (!knownHeaderItems.contains(itemName.toLowerCase())) {
                    break;
                }
                if (seenHeaderLines.contains(itemName.toLowerCase())) {
                    lexer.errorAtPos(posBeforeItemName, "duplicate lang header");
                }
                seenHeaderLines.add(itemName.toLowerCase());
                int posBeforeName = lexer.getPos();
                lexer.readDashedWord(null);
                lexer.skipSpaces();
                lexer.pass(':');
                lexer.skipSpaces();
                if (itemName.equalsIgnoreCase("Default-countdown")) {
                    defaultCountdown = lexer.readUnsignedInteger("default countdown");
                } else if (itemName.equalsIgnoreCase("Trivial")) {
                    if (lexer.passOptDashedWord("true")) {
                        trivial = true;
                    } else if (lexer.passOptDashedWord("false")) {
                        trivial = false;
                    } else {
                        lexer.error("expected 'true' or 'false'");
                    }
                } else {
                    lexer.errorAtPos(posBeforeName, "unknown lang file header item");
                }
                lexer.passLogicalNewline();
            }
            lexer.passDashedWord("dispatch");
            parseDispatchTable(lexer);
            while (!lexer.atEndOfFile()) {
                lexer.noIndent();
                if (lexer.passOptDashedWord("minitable")) {
                    parseMinitableDeclaration(lexer);
                } else {
                    break;
                }
            }
            lexer.requireEndOfFile();
        } finally {
            reader.close();
        }
        
        bytecode = coll.finish();
        for (MinitableReferencePatch patch : minitableReferencePatches) {
            patch.apply();
        }
    }

    private final void parseMinitableDeclaration(ParseUtil.IndentableLexer lexer) throws IOException {
        lexer.skipSpaces();
        int posBeforeName = lexer.getPos();
        String tableName = lexer.readDashedWord("minitable name");
        if (minitablesByName.containsKey(tableName)) {
            lexer.errorAtPos(posBeforeName, "duplicate minitable name");
        }
        lexer.skipSpaces();
        lexer.pass(':');
        lexer.skipSpaces();
        List<String> minitable = new ArrayList<String>();
        while (lexer.atAlphanumeric()) {
            minitable.add(lexer.readDashedWord("item"));
            lexer.skipSpaces();
        }
        // minitable size must be a power of two
        // (so that we can mask off excess high bits meaningfully)
        if (minitable.size() == 0 || (minitable.size() & (minitable.size() - 1)) != 0) {
            lexer.errorAtPos(posBeforeName, "invalid minitable size");
        }
        if (minitableCounter >= ClassicLang.Bytecode.MAX_MINITABLE_COUNT) {
            lexer.errorAtPos(posBeforeName, "too many minitables");
        }
        minitablesByName.put(tableName, new Integer(minitableCounter));
        linkage.minitables[minitableCounter++] = minitable.toArray(new String[0]);
        lexer.passLogicalNewline();
    }

    private final void parseDispatchTable(ParseUtil.IndentableLexer lexer) throws IOException,
            RuntimeException {
        lexer.skipSpaces();
        lexer.pass('(');
        lexer.skipSpaces();
        dispatchSuboffset = lexer.readUnsignedInteger("dispatch suboffset");
        lexer.skipSpaces();
        lexer.pass(')');
        lexer.passLogicalNewline();
        lexer.passIndent();
        while (!lexer.atDedent()) {
            lexer.noIndent();
            lexer.pass('[');
            lexer.skipSpaces();
            int posBeforeSet = lexer.getPos();
            CodeSet set = CodeSet.parse(lexer);
            lexer.skipSpaces();
            lexer.pass(']');
            lexer.skipSpaces();
            for (int i = 0; i < 256; i++) {
                if (set.matches(i)) {
                    if (dispatchTable[i] != -1) {
                        lexer.errorAtPos(posBeforeSet, "duplicate decipherer for 0x" + Hex.b(i));
                    }
                    dispatchTable[i] = coll.currentPosition();
                }
            }
            while (!lexer.atEndOfLine()) {
                int posBeforeChar = lexer.getPos();
                char c = lexer.readChar();
                if (c == '<') {
                    parseBroketed(lexer);
                } else {
                    if (c < 0x20 || c > 0x7E) {
                        lexer.errorAtPos(posBeforeChar, "invalid literal character code 0x" + Hex.w(c));
                    }
                    coll.add((byte) c);
                }
            }
            coll.add(ClassicLang.Bytecode.COMPLETE);
            lexer.passLogicalNewline();
        }
        lexer.discardDedent();
    }

    // called with lexer's cursor immediately after the opening broket; returns
    // with the cursor immediately after the closing broket
    private final void parseBroketed(ParseUtil.IndentableLexer lexer) {
        int size = 0;
        do {
            lexer.skipSpaces();
            size = parseBroketedStep(lexer, size);
        } while (lexer.passOpt(','));
        if (size != 0) {
            lexer.error("final step missing");
        }
        lexer.pass('>');
    }

    // also eats up the whitespace following the step
    private final int parseBroketedStep(ParseUtil.IndentableLexer lexer, int size) {
        int posBeforeStep = lexer.getPos();
        String verb = lexer.readDashedWord("processing step");
        lexer.skipSpaces();
        int posBeforeArg = lexer.getPos();
        String arg;
        if (lexer.atAlphanumeric()) {
            arg = lexer.readDashedWord("processing step argument");
            lexer.skipSpaces();
        } else {
            arg = null;
        }
        if (verb.equals("tempswitch")) {
            if (size != 0) {
                lexer.errorAtPos(posBeforeStep, "misplaced tempswitch");
            }
            if (arg == null) {
                lexer.errorAtPos(posBeforeArg, "expected lang reference");
            }
            coll.add((byte) (ClassicLang.Bytecode.TEMPSWITCH_0 + resolveReferredLanguage(arg)));
            return 0;
        } else if (verb.equals("dispatch")) {
            if (size != 1) {
                lexer.errorAtPos(posBeforeStep, "misplaced dispatch");
            }
            if (arg == null) {
                lexer.errorAtPos(posBeforeArg, "expected lang reference");
            }
            coll.add((byte) (ClassicLang.Bytecode.DISPATCH_0 + resolveReferredLanguage(arg)));
            return 0;
        } else if (verb.equals("entry") && arg != null) {
            if (size == 0) {
                lexer.errorAtPos(posBeforeStep, "misplaced entry");
            }
            coll.add((byte) (ClassicLang.Bytecode.ENTRY_POINT_0 + resolveReferredLanguage(arg)));
            return size;
            // note that "entry" *without* an argument is handled as an ordinary disassembler instruction
        } else {
            String step = arg == null ? verb : verb + ' ' + arg;
            ClassicLang.Bytecode.StepDeclaration resolvedStep = ClassicLang.Bytecode.resolveSimpleStep(step);
            if (resolvedStep == null) {
                switch (size) {
                    case 1:
                        resolvedStep = ClassicLang.Bytecode.resolveSimpleStep("<byte> " + step);
                        break;
                    case 2:
                        resolvedStep = ClassicLang.Bytecode.resolveSimpleStep("<wyde> " + step);
                        break;
                }
            }
            if (resolvedStep != null) {
                if (!resolvedStep.typeMatches(size)) {
                    lexer.errorAtPos(posBeforeStep, "type mismatch");
                }
                coll.add(resolvedStep.code);
                return resolvedStep.sizeAfter != -1 ? resolvedStep.sizeAfter : size;
            } else {
                // unknown -- assume it's a minitable reference
                if (size == 0) {
                    lexer.errorAtPos(posBeforeStep, "attempt to look up void value");
                }
                int position = coll.currentPosition();
                coll.add((ClassicLang.Bytecode.INVALID));
                minitableReferencePatches.add(new MinitableReferencePatch(position, step, lexer.locAtPos(posBeforeStep)));
                return 0;
            }
        }
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
            if (referredLanguageCounter >= ClassicLang.Bytecode.MAX_REFERRED_LANGUAGE_COUNT) {
                // FIXME: this ought to be properly pinpointed
                throw new RuntimeException("too many referred languages");
            }
            linkage.referredLanguages[referredLanguageCounter] = newLangName;
            referredLanguagesByName.put(newLangName, new Integer(referredLanguageCounter));
            return referredLanguageCounter++;
        } else {
            return index.intValue();
        }
    }

    class MinitableReferencePatch {
        public final int position;
        public final String minitableName;
        private final String loc;
        
        public MinitableReferencePatch(int position, String minitableName, String loc) {
            this.position = position;
            this.minitableName = minitableName;
            this.loc = loc;
        }
    
        public final void apply() {
            if (bytecode[position] != ClassicLang.Bytecode.INVALID) {
                throw new RuntimeException("bug detected");
            }
            if (!minitablesByName.containsKey(minitableName)) {
                throw new ParseUtil.ControlDataError(loc + ": unknown minitable");
            }
            int minitableNumber = minitablesByName.get(minitableName).intValue();
            assert minitableNumber < ClassicLang.Bytecode.MAX_MINITABLE_COUNT;
            bytecode[position] = (byte) (ClassicLang.Bytecode.MINITABLE_LOOKUP_0 + minitableNumber);
        }
    }
}