package net.mirky.redis;

/**
 * A CodeSet instance represents a particular set of integer codes,
 * typically in the range of 0-255. We have two kinds of CodeSet:s,
 * {@link CodeSet.Masked}, which work analogously to IPv4
 * netaddr/netmask pairs, and {@link CodeSet.Difference} which works
 * as a normal set difference operator.
 */
abstract class CodeSet {
    abstract boolean matches(int candidate);

    private static final CodeSet.Masked parseMasked(String s) {
        ParseUtil.LineLexer lexer = new ParseUtil.LineLexer(s);
        int bits = 0;
        int mask = ~0;
        int digitWidth;
        int base;
        if (lexer.at("0x")) {
            lexer.skipChar();
            lexer.skipChar();
            digitWidth = 4;
            base = 16;
        } else if (lexer.at("0o")) {
            lexer.skipChar();
            lexer.skipChar();
            digitWidth = 3;
            base = 8;
        } else if (lexer.at("0b")) {
            lexer.skipChar();
            lexer.skipChar();
            digitWidth = 1;
            base = 2;
        } else {
            throw new RuntimeException("invalid masked value " + s);
        }
        while (!lexer.atEndOfLine()) {
            if (lexer.at('?')) {
                bits <<= digitWidth;
                mask <<= digitWidth;
            } else if (lexer.at('_')) {
                // ignore
            } else {
                try {
                    bits <<= digitWidth;
                    mask <<= digitWidth;
                    bits |= Integer.parseInt(Character.toString(lexer.peekChar()), base);
                    mask |= (1 << digitWidth) - 1;
                } catch (NumberFormatException e) {
                    throw new RuntimeException("invalid masked value " + s);
                }
            }
            lexer.skipChar();
        }
        return new Masked(bits, mask);
    }

    static final CodeSet parse(String s) {
        CodeSet soFar = null;
        int veil = 0;
        int probe;
        while ((probe = s.indexOf('-', veil)) != -1) {
            soFar = parseStep(soFar, s.substring(veil, probe));
            veil = probe + 1;
        }
        return parseStep(soFar, s.substring(veil));
    }

    private static final CodeSet parseStep(CodeSet soFar, String item) {
        CodeSet.Masked parsedRight = CodeSet.parseMasked(item);
        if (soFar == null) {
            return parsedRight;
        } else {
            return new Difference(soFar, parsedRight);
        }
    }

    static final class Masked extends CodeSet {
        private final int bits;
        private final int mask;

        Masked(int bits, int mask) {
            this.bits = bits;
            this.mask = mask;
        }

        @Override
        final boolean matches(int candidate) {
            return (candidate & mask) == bits;
        }
    }

    static final class Difference extends CodeSet {
        private final CodeSet left;
        private final CodeSet right;

        Difference(CodeSet left, CodeSet right) {
            this.left = left;
            this.right = right;
        }

        @Override
        final boolean matches(int candidate) {
            return left.matches(candidate) && !right.matches(candidate);
        }
    }
}