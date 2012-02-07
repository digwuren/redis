package net.mirky.redis;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

final class LangParser {
    final byte[][] decipherers;
    final String[][] minitables;
    final Map<String, Integer> minitablesByName;
    private int minitableCounter;
    final String[] referredLanguages; // note the late binding
    final Map<String, Integer> referredLanguagesByName;
    int referredLanguageCounter;
    int dispatchSuboffset;
    private boolean dispatchSuboffsetDeclared;
    int defaultCountdown;
    private boolean defaultCountdownDeclared;
    boolean trivial;

    LangParser() {
        decipherers = new byte[256][];
        for (int i = 0; i < 256; i++) {
            decipherers[i] = null;
        }
        minitables = new String[Disassembler.Bytecode.MAX_MINITABLE_COUNT][];
        minitablesByName = new HashMap<String, Integer>();
        minitableCounter = 0;
        referredLanguages = new String[Disassembler.Bytecode.MAX_REFERRED_LANGUAGE_COUNT];
        referredLanguagesByName = new HashMap<String, Integer>();
        referredLanguageCounter = 0;
        dispatchSuboffset = 0;
        dispatchSuboffsetDeclared = false;
        defaultCountdown = 0; // by default, no default countdown
        defaultCountdownDeclared = false;
        trivial = false; // by default, not trivial
    }

    final void parse(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.length() == 0 || line.charAt(0) == '#') {
                continue;
            }
            parseDitLine(line);
        }
    }

    final void parseDitLine(String line) throws RuntimeException {
        String dispatchSuboffsetDeclarator = "Dispatch-suboffset:";
        String defaultCountdownDeclarator = "Default-countdown:";
        if (line.startsWith(dispatchSuboffsetDeclarator)) {
            String parameter = line.substring(dispatchSuboffsetDeclarator.length()).trim();
            if (dispatchSuboffsetDeclared) {
                throw new RuntimeException("duplicate Dispatch-suboffset: declaration in lang file");
            }
            dispatchSuboffset = Integer.parseInt(parameter);
            dispatchSuboffsetDeclared = true;
        } else if (line.startsWith(defaultCountdownDeclarator)) {
            String parameter = line.substring(defaultCountdownDeclarator.length()).trim();
            if (defaultCountdownDeclared) {
                throw new RuntimeException("duplicate Default-countdown: declaration in lang file");
            }
            defaultCountdown = Integer.parseInt(parameter);
            defaultCountdownDeclared = true;
        } else if (line.equals("Trivial!")) {
            trivial = true;
            // no duplicity check
        } else {
            // Besides the metadata, a lang file has lines of two
            // types:
            // [mask] decipherer
            // minitable[] value, value, ...
            int leftBracket = line.indexOf('[');
            int rightBracket = line.indexOf(']', leftBracket + 1);
            if (leftBracket == -1 || rightBracket == -1) {
                throw new RuntimeException("invalid lang file line: " + line);
            }
            String tableName = line.substring(0, leftBracket).trim();
            String setSpec = line.substring(leftBracket + 1, rightBracket).trim();
            String content = line.substring(rightBracket + 1).trim();
            try {
                if (tableName.length() != 0) {
                    // minitable line
                    if (setSpec.length() != 0) {
                        throw new RuntimeException("invalid lang file line: " + line);
                    }
                    parseMinitableLine(tableName, content);
                } else {
                    // decipherer line
                    parseDeciphererLine(setSpec, content);
                }
            } catch (DisassemblyTableParseError e) {
                throw new RuntimeException("invalid lang file line: " + line, e);
            }
        }
    }

    final void parseDeciphererLine(String setSpec, String content) throws RuntimeException, DisassemblyTableParseError {
        Disassembler.Lang.Tabular.CodeSet set = Disassembler.Lang.Tabular.CodeSet.parse(setSpec);
        byte[] decipherer = parseDecipherer(content);
        for (int i = 0; i < 256; i++) {
            if (set.matches(i)) {
                if (decipherers[i] != null) {
                    throw new DisassemblyTableParseError("duplicate decipherer for 0x" + Hex.b(i));
                }
                decipherers[i] = decipherer;
            }
        }
    }

    /**
     * Parse a decipherer from a lang file into the internal
     * bytecode.
     * 
     * Note that the lang file syntax is currently not considered a
     * public interface, so we're not trying to be particularly
     * user-friendly. We have a whitespace-sensitive syntax, opcodes
     * with fixed and very particular parameters, and not very
     * informative error messages.
     */
    final byte[] parseDecipherer(String s) {
        return new DeciphererParser(s).parse();
    }

    final void parseMinitableLine(String tableName, String content) throws RuntimeException {
        String[] minitable = Disassembler.Lang.Tabular.SPACED_COMMA.split(content, -1);
        // minitable size must be a power of two
        // (so that we can mask off excess high bits meaningfully)
        if (minitable.length == 0 || (minitable.length & (minitable.length - 1)) != 0) {
            throw new RuntimeException("invalid minitable size");
        }
        if (minitablesByName.containsKey(tableName)) {
            throw new RuntimeException("duplicate minitable name: " + tableName);
        }
        if (minitableCounter >= Disassembler.Bytecode.MAX_MINITABLE_COUNT) {
            throw new RuntimeException("too many minitables");
        }
        minitablesByName.put(tableName, new Integer(minitableCounter));
        minitables[minitableCounter++] = minitable;
    }

    final class DeciphererParser {
        private final String string;
        private int veil;
        private int probe;
        private int size;
        private final Disassembler.Lang.Tabular.BytecodeCollector coll;

        DeciphererParser(String string) {
            this.string = string;
            veil = 0;
            probe = 0;
            size = 0;
            coll = new Disassembler.Lang.Tabular.BytecodeCollector();
        }

        final void parseProcessingStep(String step) throws DisassemblyTableParseError, RuntimeException {
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
                    if (minitablesByName.containsKey(step)) {
                        int minitableNumber = minitablesByName.get(step).intValue();
                        assert minitableNumber < Disassembler.Bytecode.MAX_MINITABLE_COUNT;
                        coll.add((byte) (Disassembler.Bytecode.MINITABLE_LOOKUP_0 | minitableNumber));
                        size = 0;
                    } else if (step.equals("unsigned")) {
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
                        throw new DisassemblyTableParseError("unknown processing step: " + step);
                    }
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
                if (referredLanguageCounter >= Disassembler.Bytecode.MAX_REFERRED_LANGUAGE_COUNT) {
                    throw new RuntimeException("too many referred languages");
                }
                referredLanguages[referredLanguageCounter] = newLangName;
                referredLanguagesByName.put(newLangName, index);
                return referredLanguageCounter++;
            } else {
                return index.intValue();
            }
        }

        final void passLiteralText() throws RuntimeException {
            while (veil < probe) {
                char c = string.charAt(veil);
                if (c < 0x20 || c > 0x7E) {
                    throw new RuntimeException("invalid literal character code 0x" + Hex.w(c));
                }
                coll.add((byte) c);
                veil++;
            }
        }

        final byte[] parse() throws RuntimeException {
            while ((probe = string.indexOf('<', veil)) != -1) {
                int rightBroket = string.indexOf('>', probe);
                if (rightBroket == -1) {
                    throw new RuntimeException("error parsing opcode decipherer " + string);
                }
                passLiteralText();
                String broketedPart = string.substring(probe + 1, rightBroket);
                String[] stepSpecs = Disassembler.Lang.Tabular.SPACED_COMMA.split(broketedPart, -1);
                try {
                    if (stepSpecs.length == 0) {
                        throw new DisassemblyTableParseError("empty broketed part");
                    }
                    size = 0;
                    for (int i = 0; i < stepSpecs.length; i++) {
                        parseProcessingStep(stepSpecs[i]);
                    }
                    if (size != 0) {
                        throw new DisassemblyTableParseError("final step missing");
                    }
                } catch (DisassemblyTableParseError e) {
                    throw new RuntimeException("error parsing opcode decipherer broketed part <"
                            + broketedPart + ">", e);
                }
                veil = rightBroket + 1;
            }
            probe = string.length();
            passLiteralText();
            return coll.finish();
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
    }
}