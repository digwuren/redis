package net.mirky.redis;

import java.io.PrintStream;

// Utilities for parsing and generating hexadecimal numbers.
// Hex.dump() also lives here.
public final class Hex {
	public static final String t(int x) {
		String result = Long.toHexString(x & 0xFFFFFFFFL).toUpperCase();
		StringBuilder sb = new StringBuilder();
		for (int i = 8 - result.length(); i > 0; i--) {
			sb.append('0');
		}
		sb.append(result);
		assert sb.length() == 8;
		return sb.toString();
	}

	public static final String w(int x) {
		String result = Integer.toHexString(x & 0xFFFF).toUpperCase();
		StringBuilder sb = new StringBuilder();
		for (int i = 4 - result.length(); i > 0; i--) {
			sb.append('0');
		}
		sb.append(result);
		assert sb.length() == 4;
		return sb.toString();
	}

	public static final String b(int x) {
		String result = Integer.toHexString(x & 0xFF).toUpperCase();
		if ((x & 0xFF) < 0x10) {
			return "0" + result;
		} else {
			return result;
		}
	}
	
	static final String bs(byte[] data) {
		StringBuilder sb = new StringBuilder();
		for (int b : data) {
			b &= 0xFF;
			if (b < 0x10) {
				sb.append('0');
			}
			sb.append(Integer.toHexString(b).toUpperCase());
		}
		assert sb.length() == data.length * 2;
		return sb.toString();
	}

	static final String bs(byte[] data, char sep) {
		StringBuilder sb = new StringBuilder();
		for (int b : data) {
			b &= 0xFF;
			if (b < 0x10) {
				sb.append('0');
			}
			sb.append(Integer.toHexString(b).toUpperCase());
			sb.append(sep);
		}
		if (sb.length() != 0) {
			assert sb.length() == data.length * 3;
			sb.setLength(sb.length() - 1);
		} else {
			assert data.length == 0;
		}
		return sb.toString();
	}

	static final int pt(String s) {
		if (s.length() != 8) {
			throw new NumberFormatException("hex tetrabyte length mismatch");
		}
		return (int) Long.parseLong(s, 16);
	}

	public static final byte pb(String s) {
		if (s.length() != 2) {
			throw new NumberFormatException("hex byte length mismatch");
		}
		return (byte) Integer.parseInt(s, 16);
	}

	static final byte[] pbs(String s) {
		if (s.length() == 0) {
			return new byte[0];
		} else {
			if (s.length() % 3 != 2) {
				// Is considering an array of bytes a number cheating?
				throw new NumberFormatException("length mismatch");
			}
			byte[] bytes = new byte[(s.length() + 1) / 3];
			for (int i = -1; i < s.length(); i += 3) {
				if (i != -1 && s.charAt(i) != '.') {
					throw new NumberFormatException("bad separator");
				}
				bytes[(i + 1) / 3] = pb(s.substring(i + 1, i + 3));
			}
			return bytes;
		}
	}

	static final int[] pts(String s) {
		if (s.length() == 0) {
			return new int[0];
		} else {
			if (s.length() % 9 != 8) {
				throw new NumberFormatException("length mismatch");
			}
			int[] tetras = new int[(s.length() + 1) / 9];
			for (int i = -1; i < s.length(); i += 9) {
				if (i != -1 && s.charAt(i) != '.') {
					throw new NumberFormatException("bad separator");
				}
				tetras[(i + 1) / 9] = pt(s.substring(i + 1, i + 9));
			}
			return tetras;
		}
	}

	// decoding may be given as null; then, the character column will not be output
	public static final void dump(byte[] data, int origin, Decoding decoding, PrintStream port) {
		dump(data, origin, decoding, null, port);
	}

	public static final void dump(byte[] data, int origin, Decoding decoding, boolean[] mask, PrintStream port) {
        if (data.length == 0) {
            port.println("(nothing to dump)");
			return;
		}
        BichromaticStringBuilder bsb = new BichromaticStringBuilder("34"); // the secondary colour is blue
		for (int row = origin & ~0xF; row - origin < data.length; row += 16) {
			bsb.clear();
			bsb.sb.append(t(row));
			bsb.sb.append(':');
            for (int col = 0; col < 16; col++) {
				int offset = row + col - origin;
				bsb.sb.append(' ');
				if ((col & 3) == 0) {
					bsb.sb.append(' ');
				}
                if (offset >= 0 && offset < data.length) {
                    boolean masked = getMaskBit(mask, offset);
				    if (masked) {
                        /*
                         * {@link BichromaticStringBuilder#beginColour()} takes
                         * care of not starting colour again if we're already in
                         * colour mode.
                         */
	                    bsb.beginColour();
				    }
					bsb.sb.append(b(data[offset]));
					if (masked && (col == 15 || !getMaskBit(mask, offset  +1))) {
					    bsb.endColour();
					}
				} else {
				    bsb.sb.append("  ");
				}
			}
			if (decoding != null) {
				bsb.sb.append("  ");
				for (int col = 0; col < 16; col++) {
					int offset = row + col - origin;
					if (offset < 0) {
						bsb.sb.append(' ');
					} else if (offset < data.length) {
					    boolean masked = getMaskBit(mask, offset);
					    if (masked) {
					        bsb.beginColour();
					    } else {
					        bsb.endColour();
					    }
						char dc = decoding.decode(data[offset]);
						if (dc == 0) {
							dc = '.';
						}
						bsb.sb.append(dc);
					} else {
					    bsb.endColour();
						break;
					}
				}
                bsb.endColour();
            }
			bsb.printLine(port);
		}
    }

    static final boolean getMaskBit(boolean[] mask, int offset) {
        boolean masked = mask != null && offset >= 0 && offset < mask.length && !mask[offset];
        return masked;
    }
}