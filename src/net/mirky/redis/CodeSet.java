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

    public static final CodeSet.Masked parseMasked(ParseUtil.IndentationSensitiveLexer lexer) {
        int bits = 0;
        int mask = ~0;
        int digitWidth;
        int base;
        String digits;
        if (lexer.hor.at("0x")) {
            lexer.hor.skipChar();
            lexer.hor.skipChar();
            digitWidth = 4;
            base = 16;
            digits = "0123456789abcdefABCDEF";
        } else if (lexer.hor.at("0o")) {
            lexer.hor.skipChar();
            lexer.hor.skipChar();
            digitWidth = 3;
            base = 8;
            digits = "01234567";
        } else if (lexer.hor.at("0b")) {
            lexer.hor.skipChar();
            lexer.hor.skipChar();
            digitWidth = 1;
            base = 2;
            digits = "01";
        } else {
            throw new RuntimeException("invalid masked value");
        }
        while (true) {
            if (lexer.hor.at('?')) {
                bits <<= digitWidth;
                mask <<= digitWidth;
                lexer.hor.skipChar();
                continue;
            } else if (lexer.hor.at('_')) {
                // ignore
                lexer.hor.skipChar();
                continue;
            } else if (lexer.hor.atAnyOf(digits)) {
                try {
                    bits <<= digitWidth;
                    mask <<= digitWidth;
                    bits |= Integer.parseInt(Character.toString(lexer.peekChar()), base);
                    mask |= (1 << digitWidth) - 1;
                } catch (NumberFormatException e) {
                    throw new RuntimeException("bug detected");
                }
                lexer.hor.skipChar();
                continue;
            } else {
                break;
            }
        }
        if (lexer.hor.atAlphanumeric()) {
            throw new RuntimeException("not a base " + base + " digit");
        }
        return new Masked(bits, mask);
    }

    // Also eats up the whitespace immediately following the set.
    static final CodeSet parse(ParseUtil.IndentationSensitiveLexer lexer) {
        CodeSet soFar = parseMasked(lexer);
        lexer.hor.skipSpaces();
        while (lexer.hor.at('-')) {
            lexer.hor.skipChar();
            lexer.hor.skipSpaces();
            soFar = new Difference(soFar, parseMasked(lexer));
            lexer.hor.skipSpaces();
        }
        return soFar;
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