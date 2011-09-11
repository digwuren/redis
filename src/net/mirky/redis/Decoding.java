package net.mirky.redis;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// A Decoding instance represents a particular way to decode bytes into characters.
// Normally, it's like an encoding, except one-way (and the back-way).
// These are mainly used for hexdumps but also for decoding strings from native encodings
// of ancient computer systems.
public final class Decoding {
	final String name;
	private final char[] codes;

	Decoding(String name, char[] codes) {
		assert codes.length == 256;
		this.name = name;
		this.codes = codes;
	}

	// Decode the given byte.  Return its Unicode value, if available and if it's printable.
	// Return 0 otherwise.  (In a hexdump, this would then be output as a period.)
	public final char decode(byte code) {
		return codes[code & 0xFF];
	}
	
	// Decode a filename, given as a sequence of bytes in this decoding, into the given
	// StringBuilder.  Escape characters that are inconvenient (everything but letters,
	// digits, whitespace, exclamation point, apostrophe, period, underscore, and hyphen)
	// after decoding using the given escape char and two hex digits.
	public final void decodeFilename(byte[] filename, char escape, StringBuilder sb) {
		for (int i = 0; i < filename.length; i++) {
			byte b = filename[i];
			char c = decode(b);
			if ((c >= 0x41 && c <= 0x5A) || (c >= 0x61 && c <= 0x7A) || (c >= 0x30 && c <= 0x39) || " !'._-".indexOf(c) != -1) {
				sb.append(c);
			} else {
				sb.append(escape);
				sb.append(Hex.b(c));
			}
		}
	}

	// Display a filename, given as a sequence of bytes in this decoding, in stdout.
	// Escape nonprintables, leading and trailing whitespaces, and brokets by surrounding
	// them with brokets and representing their hex values of the original codes.  Multiple
	// adjacent inconvenient codes are delimited by a period.
	public final void displayForeignString(byte[] s, PrintStream port) {
		try {
			StringBuilder sb = new StringBuilder();
			boolean justAppendedHex = false;
			for (int i = 0; i < s.length; i++) {
				char c = decode(s[i]);
				if (c >= 0x20 && c <= 0x7E && c != '<' && c != '>' &&
						(c != 0x20 || (i != 0
								&& i != s.length - 1
								&& s[i + 1] != 0x20
								&& s[i - 1] != 0x20))) {
					sb.append(c);
					justAppendedHex = false;
				} else {
					if (justAppendedHex) {
						sb.setCharAt(sb.length() - 1, '.');
					} else {
						sb.append('<');
					}
					sb.append(Hex.b(s[i]));
					sb.append('>');
					justAppendedHex = true;
				}
			}
			port.write(sb.toString().getBytes("utf-8"));
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException("utf-8 is an Unsupported Encoding?  In Java???", e);
		} catch (IOException e) {
			throw new RuntimeException("I/O error", e);
		}
	}

	private static final char[] LATIN_1_CODES = new char[256];
	static {
		for (int i = 0; i < 256; i++) {
			LATIN_1_CODES[i] = 0;
		}
		for (int i = 0x20; i <= 0x7E; i++) {
			LATIN_1_CODES[i] = (char) i;
		}
		for (int i = 0xA1; i <= 0xFF; i++) { // leave out $A0, hard space ...
			LATIN_1_CODES[i] = (char) i;
		}
		// ... and $AD, soft hyphen
		LATIN_1_CODES[0xAD] = 0;
	}
	
	private static final char[] ZX_SPECTRUM_CODES = new char[256];
	static {
		for (int i = 0; i < 256; i++) {
			ZX_SPECTRUM_CODES[i] = 0;
		}
		for (int i = 0x20; i <= 0x7E; i++) {
			ZX_SPECTRUM_CODES[i] = (char) i;
		}
		ZX_SPECTRUM_CODES[0x5E] = 0x2191;
		ZX_SPECTRUM_CODES[0x60] = 0x00A3;
		ZX_SPECTRUM_CODES[0x7F] = 0x00A9;
		// block graphics
		ZX_SPECTRUM_CODES[0X80] = 0X00A0;
		ZX_SPECTRUM_CODES[0x81] = 0x259D;
        ZX_SPECTRUM_CODES[0x82] = 0x2598;
        ZX_SPECTRUM_CODES[0x83] = 0x2580;
        ZX_SPECTRUM_CODES[0x84] = 0x2597;
        ZX_SPECTRUM_CODES[0x85] = 0x2590;
        ZX_SPECTRUM_CODES[0x86] = 0x259A;
        ZX_SPECTRUM_CODES[0x87] = 0x259C;
        ZX_SPECTRUM_CODES[0x88] = 0x2596;
        ZX_SPECTRUM_CODES[0x89] = 0x259E;
        ZX_SPECTRUM_CODES[0x8A] = 0x258C;
        ZX_SPECTRUM_CODES[0x8B] = 0x259B;
        ZX_SPECTRUM_CODES[0x8C] = 0x2584;
        ZX_SPECTRUM_CODES[0x8D] = 0x259F;
        ZX_SPECTRUM_CODES[0x8E] = 0x2599;
        ZX_SPECTRUM_CODES[0x8F] = 0x2588;
	}

	private static final char[] PETSCII_CODES = new char[256];
	static {
		for (int i = 0; i < 256; i++) {
			PETSCII_CODES[i] = 0;
		}
		for (int i = 0x20; i <= 0x5A; i++) {
			PETSCII_CODES[i] = (char) i;
		}
		PETSCII_CODES[0x5B] = '[';
		PETSCII_CODES[0x5C] = 0x00A3; // pound sterling
		PETSCII_CODES[0x5D] = ']';
		PETSCII_CODES[0x5E] = 0x2191; // up arrow
		PETSCII_CODES[0x5F] = 0x2190; // left arrow
		
		PETSCII_CODES[0x60] = 0x2500; // horizontal
		PETSCII_CODES[0x61] = 0x2660; // filled spades
		PETSCII_CODES[0x62] = 0x2502; // vertical
		PETSCII_CODES[0x63] = 0x2500; // also horizontal
		// 0x64 .. 0x66 are displaced horizontals with no Unicode match
		// 0x67 .. 0x68 are displaced verticals with no Unicode match
		PETSCII_CODES[0x69] = 0x256E; // arc down and left
		PETSCII_CODES[0x6A] = 0x2570; // arc up and right
		PETSCII_CODES[0x6B] = 0x256F; // arc up and left
		// 0x6C is bottom quarter block overstruck with left quarter block, no Unicode match
		PETSCII_CODES[0x6D] = 0x2572; // diagonal from top left to bottom right
		PETSCII_CODES[0x6E] = 0x2571; // diagonal from top right to bottom left
		// 0x6F is top quarter block overstruck with left quarter block, no Unicode match
		
		// 0x70 is top quarter block overstruck with right quarter block, no Unicode match
		PETSCII_CODES[0x71] = 0x25CF; // filled circle
		// 0x72 is a displaced horizontal with no Unicode match
		PETSCII_CODES[0x73] = 0x2665; // filled hearts
		// 0x74 is a displaced vertical with no Unicode match
		PETSCII_CODES[0x75] = 0x256D; // arc down and right
		PETSCII_CODES[0x76] = 0x2573; // diagonal cross
		PETSCII_CODES[0x77] = 0x25CB; // hollow circle
		PETSCII_CODES[0x78] = 0x2663; // filled clubs
		// 0x79 is a displaced vertical with no Unicode match
		PETSCII_CODES[0x7A] = 0x2666; // filled diamonds
		PETSCII_CODES[0x7B] = 0x253C; // horizontal and vertical
		// 0x7C is left half shade, no Unicode match
		PETSCII_CODES[0x7D] = 0x2502; // also vertical
		PETSCII_CODES[0x7E] = 0x03C0; // Greek lowercase pi
		PETSCII_CODES[0x7F] = 0x25E5; // upper right triangle
		
		PETSCII_CODES[0xA0] = 0x00A0; // nbsp
		PETSCII_CODES[0xA1] = 0x258C; // left half block
		PETSCII_CODES[0xA2] = 0x2584; // bottom half block
		PETSCII_CODES[0xA3] = 0x2594; // top one eighth block
		PETSCII_CODES[0xA4] = 0x2581; // bottom one eighth block
		PETSCII_CODES[0xA5] = 0x258E; // left quarter block
		PETSCII_CODES[0xA6] = 0x2592; // shade
		// 0xA7 is right one quarter block, no Unicode match
		// 0xA8 is bottom half shade, no Unicode match
		PETSCII_CODES[0xA9] = 0x25E4; // upper left triangle
		// 0xAA is also right one quarter block, no Unicode match
		PETSCII_CODES[0xAB] = 0x251C; // vertical and right
		PETSCII_CODES[0xAC] = 0x2597; // lower right quadrant
		PETSCII_CODES[0xAD] = 0x2514; // up and right
		PETSCII_CODES[0xAE] = 0x2510; // down and left
		PETSCII_CODES[0xAF] = 0x2582; // bottom quarter block
		
		PETSCII_CODES[0xB0] = 0x250C; // down and right
		PETSCII_CODES[0xB1] = 0x2534; // horizontal and up
		PETSCII_CODES[0xB2] = 0x252C; // horizontal and down
		PETSCII_CODES[0xB3] = 0x2524; // vertical and left
		PETSCII_CODES[0xB4] = 0x258E; // also left quarter block
		PETSCII_CODES[0xB5] = 0x258D; // left 3/8 block
		// 0xB6 is right 3/8 block, no Unicode match
		// 0xB7 is top quarter block, no Unicode match
		// 0xB8 is top 3/8 block, no Unicode match
		PETSCII_CODES[0xB9] = 0X2583; // bottom 3/8 block
		// 0xBA is bottom quarter block overstruck with right quarter block, no Unicode match
		PETSCII_CODES[0xBB] = 0x2596; // lower left quadrant
		PETSCII_CODES[0xBC] = 0x259D; // upper right quadrant
		PETSCII_CODES[0xBD] = 0x2518; // up and left
		PETSCII_CODES[0xBE] = 0x2598; // upper left quadrant
		PETSCII_CODES[0xBF] = 0x259A; // upper left and lower right quadrant
	}
	
	public static final class DecodingBuilder {
	    private final char[] codes;
	    
	    public DecodingBuilder() {
	        codes = new char[256];
	        for (int i = 0; i < 256; i++) {
	            codes[i] = 0;
	        }
	    }
	    
	    public final void addSingleEntry(int local, char unicode) {
	        assert unicode != 0;
	        if (codes[local] != 0) {
	            throw new RuntimeException("multiple decoding rules for $" + Hex.b(local));
	        }
	        codes[local] = unicode;
	    }
	    
	    public final void addRange(int firstLocal, int firstUnicode, int count) {
	        assert count > 1;
	        assert firstLocal >= 0;
	        assert firstUnicode > 0; // strict!
	        assert firstLocal + count <= 0xFF;
	        assert firstUnicode + count <= 0xFFFF;
	        for (int i = 0; i < count; i++) {
	            if (codes[firstLocal + i] != 0) {
	                throw new RuntimeException("multiple decoding rules for $" + Hex.b(firstLocal + i));
	            }
	        }
            for (int i = 0; i < count; i++) {
                codes[firstLocal + i] = (char) (firstUnicode + i);
            }
	    }

	    public static final Decoding parse(String name) {
            DecodingBuilder builder = new DecodingBuilder();
            for (String line : new TextResource(name + ".decoding")) {
                line = line.trim();
                if (line.length() != 0 && line.charAt(0) != '#') {
                    Matcher matcher;
                    if ((matcher = DecodingBuilder.SINGLE_ENTRY_RE.matcher(line)).matches()) {
                        int local = Integer.parseInt(matcher.group(1), 16);
                        int unicode = Integer.parseInt(matcher.group(2), 16);
                        if (unicode == 0) {
                            throw new RuntimeException("decoding rules are not permitted to map local codes to U+0000");
                        }
                        builder.addSingleEntry(local, (char) unicode);
                    } else if ((matcher = DecodingBuilder.RANGE_ENTRY_RE.matcher(line)).matches()) {
                        int firstLocal = Integer.parseInt(matcher.group(1), 16);
                        int lastLocal = Integer.parseInt(matcher.group(2), 16);
                        int firstUnicode = Integer.parseInt(matcher.group(3), 16);
                        int lastUnicode = Integer.parseInt(matcher.group(4) , 16);
                        if (firstLocal >= lastLocal || firstUnicode >= lastUnicode) {
                            throw new RuntimeException("invalid range specification");
                        }
                        if (lastLocal - firstLocal != lastUnicode - firstUnicode) {
                            throw new RuntimeException("range size mismatch");
                        }
                        if (firstUnicode == 0) {
                            throw new RuntimeException("decoding rules are not permitted to map local codes to U+0000");
                        }
                        builder.addRange(firstLocal, firstUnicode, lastLocal - firstLocal + 1);
                    } else {
                        throw new RuntimeException("decoding parse error on line \"" + line + '"');
                    }
                }
            }
            return new Decoding(name, builder.codes);
        }

        private static final Pattern SINGLE_ENTRY_RE = Pattern.compile("^\\$([\\da-f]{2})\\s*->\\s*U\\+([\\da-f]{4})$",
	            Pattern.CASE_INSENSITIVE);
        private static final Pattern RANGE_ENTRY_RE = Pattern.compile("^\\$([\\da-f]{2})\\s*\\.\\.\\s*\\$([\\da-f]{2})\\s*->\\s*U\\+([\\da-f]{4})\\s*\\.\\.\\s*U\\+([\\da-f]{4})$",
                Pattern.CASE_INSENSITIVE);
	}

	public final void dumpDecoding(PrintStream port) {
	    int rangeStart = -1; // local code (if >= 0)
	    int delta = 0;
	    for (int i = 0; i <= 0x100; i++) {
	        char unicode = i <= 0xFF ? codes[i] : 0;
	        if (rangeStart >= 0 && (unicode == 0 || unicode - i != delta)) {
                dumpDecodingEntry(rangeStart, delta, i - rangeStart, port);
                rangeStart = -1;
            }
            if (rangeStart < 0 && unicode != 0) {
                rangeStart = i;
                delta = unicode - rangeStart;
            }
	    }
	}

    private static final void dumpDecodingEntry(int rangeStart, int delta, int count, PrintStream port) {
        assert count >= 1;
        if (count != 1) {
            port.println('$' + Hex.b(rangeStart) + "..$" + Hex.b(rangeStart + count - 1)
                    + " -> U+" + Hex.w(rangeStart + delta) + "..U+" + Hex.w(rangeStart + count - 1 + delta));
        } else {
            port.println('$' + Hex.b(rangeStart) + " -> U+" + Hex.w(rangeStart + delta));
        }
    }
	
	private static final Map<String, String> aliases = new HashMap<String, String>();
	static {
		aliases.put("latin1", "latin-1");
	}
	
	private static final Map<String, Decoding> decodings = new HashMap<String, Decoding>();
	static {
		decodings.put("ascii", DecodingBuilder.parse("ascii"));
		decodings.put("folded-ascii", DecodingBuilder.parse("folded-ascii"));
		decodings.put("latin-1", new Decoding("latin-1", LATIN_1_CODES));
		decodings.put("zx-spectrum", new Decoding("zx-spectrum", ZX_SPECTRUM_CODES));
		decodings.put("petscii", new Decoding("petscii", PETSCII_CODES));
	}

	static final Decoding get(String name) {
		if (aliases.containsKey(name)) {
			return get(aliases.get(name));
		} else {
			return decodings.get(name);
		}
	}
}