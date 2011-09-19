package net.mirky.redis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
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

	    public static final Decoding parse(String name, BufferedReader reader) throws IOException {
            DecodingBuilder builder = new DecodingBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                int commentStart = line.indexOf('#');
                if (commentStart >= 0) {
                    line = line.substring(0, commentStart);
                }
                line = line.trim();
                if (line.length() != 0) {
                    Matcher matcher;
                    try {
                        if ((matcher = DecodingBuilder.SINGLE_ENTRY_RE.matcher(line)).matches()) {
                            int local = Integer.parseInt(matcher.group(1), 16);
                            int unicode = Integer.parseInt(matcher.group(2), 16);
                            if (unicode == 0) {
                                throw new RuntimeException(
                                        "decoding rules are not permitted to map local codes to U+0000");
                            }
                            builder.addSingleEntry(local, (char) unicode);
                        } else if ((matcher = DecodingBuilder.RANGE_ENTRY_RE.matcher(line)).matches()) {
                            int firstLocal = Integer.parseInt(matcher.group(1), 16);
                            int lastLocal = Integer.parseInt(matcher.group(2), 16);
                            int firstUnicode = Integer.parseInt(matcher.group(3), 16);
                            int lastUnicode = Integer.parseInt(matcher.group(4), 16);
                            if (firstLocal >= lastLocal || firstUnicode >= lastUnicode) {
                                throw new RuntimeException("invalid range specification");
                            }
                            if (lastLocal - firstLocal != lastUnicode - firstUnicode) {
                                throw new RuntimeException("range size mismatch");
                            }
                            if (firstUnicode == 0) {
                                throw new RuntimeException(
                                        "decoding rules are not permitted to map local codes to U+0000");
                            }
                            builder.addRange(firstLocal, firstUnicode, lastLocal - firstLocal + 1);
                        } else {
                            throw new RuntimeException("decoding parse error on line \"" + line + '"');
                        }
                    } catch (NumberFormatException e) {
                        throw new RuntimeException("number format error", e);
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
    
    public final void showDecoding(PrintStream port) {
        port.print("  ");
        for (int col = 0; col <= 15; col++) {
            port.print(' ');
            port.print(Hex.n(col));
        }
        port.println();
        for (int row = 0; row <= 15; row++) {
            boolean rowUsed = false;
            for (int col = 0; col <= 15; col++) {
                if (codes[(row << 4) | col] != 0) {
                    rowUsed = true;
                    break;
                }
            }
            if (!rowUsed) {
                continue;
            }
            port.print(Hex.n(row));
            port.print(' ');
            for (int col = 0; col <= 15; col++) {
                port.print(' ');
                char unicode = codes[(row << 4) | col];
                if (unicode != 0) {
                    try {
                        port.write(Character.toString(unicode).getBytes("utf-8"));
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException("utf-8 is an Unsupported Encoding?  In Java???", e);
                    } catch (IOException e) {
                        throw new RuntimeException("I/O error", e);
                    }
                } else {
                    port.print(' ');
                }
            }
            port.print("  ");
            port.println(Hex.n(row));
        }
        port.print("  ");
        for (int col = 0; col <= 15; col++) {
            port.print(' ');
            port.print(Hex.n(col));
        }
        port.println();
    }

    public static final ResourceManager<Decoding> MANAGER = new ResourceManager<Decoding>("decoding") {
        @Override
        public final Decoding load(String name, BufferedReader reader) throws IOException, RuntimeException {
            return Decoding.DecodingBuilder.parse(name, reader);
        }
    };
    
    static {
        MANAGER.registerAlias("latin1", "latin-1");
    }
}