package net.mirky.redis.analysers;

import java.util.regex.Matcher;

import net.mirky.redis.Analyser;
import net.mirky.redis.DeciphererOutput;
import net.mirky.redis.Format;
import net.mirky.redis.Hex;
import net.mirky.redis.ImageError;
import net.mirky.redis.ReconstructionDataCollector;

@Format.Options(value = "zxs-basic-program/decoding:decoding=zx-spectrum/autostart:unsigned-decimal=0/var-offset:(unsigned-hex/-1)=nil",
        aliases = ".zx0")
public final class ZXSBasicProgramAnalyser extends Analyser.Container {
    @Override
    public final Format taste(Format guessedFormat, String filename) {
        Format newFormat = guessedFormat;
        Matcher matcher = ZXSTapeBlockPairer.ZXLFN_REGEX.matcher(filename);
        if (matcher.matches()) {
            int param1 = ZXSTapeBlockPairer.parseZxlfnParam(matcher.group(2));
            int param2 = ZXSTapeBlockPairer.parseZxlfnParam(matcher.group(3));
            try {
                if (param1 != -1) {
                    newFormat = new Format(newFormat, "autostart", Integer.toString(param1));
                }
                if (param2 != -1) {
                    newFormat = new Format(newFormat, "var-offset", Integer.toString(param2));
                }
            } catch (Format.OptionError e) {
                throw new RuntimeException("bug detected", e);
            }
        }
        return newFormat;
    }

    @Override
    protected final ReconstructionDataCollector extractFiles(Format format, byte[] data) throws RuntimeException {
        int varOffset = format.getIntegerOption("var-offset");
        // A program with no variables is indicated by {@code var-offset}
        // pointing to the end of file by ZXS custom, but we also consider the
        // special out-of-band value -1 to mean this.
        byte[] basicCodeData, variableData;
        if (varOffset != -1 && varOffset < data.length) {
            basicCodeData = new byte[varOffset];
            System.arraycopy(data, 0, basicCodeData, 0, basicCodeData.length);
            variableData = new byte[data.length - varOffset];
            System.arraycopy(data, varOffset, variableData, 0, variableData.length);
        } else {
            basicCodeData = data;
            variableData = null;
        }
        try {
            ReconstructionDataCollector rcn = new ReconstructionDataCollector(data.length);
            Format basicProgramCodeFormat = Format.getGuaranteedFormat("zxs-basic-code");
            basicProgramCodeFormat = format.imposeDecodingIfExplicit(basicProgramCodeFormat);
            @SuppressWarnings("unchecked")
            Format.Option.SimpleOption<Integer> autostartOption = (Format.Option.SimpleOption) format.getOption("autostart");
            if (autostartOption.explicit) {
                try {
                    basicProgramCodeFormat = new Format(basicProgramCodeFormat, "autostart", autostartOption.value.toString());
                } catch (Format.OptionError e) {
                    throw new RuntimeException("bug detected", e);
                }
            }
            rcn.contiguousFile(".zxsbc", basicCodeData, basicProgramCodeFormat, 0);
            if (variableData != null) {
                Format varFormat = Format.getGuaranteedFormat("zxs-basic-vars");
                varFormat = format.imposeDecodingIfExplicit(varFormat);
                rcn.contiguousFile(".zxsbv", variableData, varFormat, varOffset);
            }
            return rcn;
        } catch (ImageError.DuplicateChildFilenameError e) {
            // we just created the rcn -- must be a spurious error
            throw new RuntimeException("bug detected", e);
        }
    }

    public static final class ZXSpectrumNumber {
        final byte[] bytes;

        public ZXSpectrumNumber(byte[] bytes) {
            assert bytes.length == 5;
            this.bytes = bytes;
        }

        final boolean isValid() {
            return bytes[0] != 0 || (bytes[1] == 0x00 || bytes[1] == 0xFF);
        }

        final boolean isInteger() {
            return bytes[0] == 0 && (bytes[1] == 0x00 || bytes[1] == 0xFF);
        }

        public final boolean is(int etalon) {
            return isInteger() && intValue() == etalon;
        }

        final int intValue() {
            assert isInteger();
            int value = (bytes[3] & 0xFF) * 256 + (bytes[2] & 0xFF);
            if (bytes[1] != 0) {
                value |= ~0xFFFF;
            }
            return value;
        }

        public final String prepareForDisplay() {
            if (isInteger()) {
                return Integer.toString(intValue());
            } else {
                // the significand is 32 bits, stored big-endian starting from
                // the second byte.
                long significand = ((long) (bytes[1] & 0xFF)) << 24;
                significand |= (bytes[2] & 0xFF) << 16;
                significand |= (bytes[3] & 0xFF) << 8;
                significand |= (bytes[4] & 0xFF);
                // However, its top bit is sign bit, and should be replaced by 1
                // for decoding.
                boolean negativep = (significand & 0x80000000L) != 0;
                significand |= 0x80000000L;
                double result = significand * Math.pow(2, ((bytes[0] & 0xFF) - 0xA0));
                if (negativep) {
                    result = -result;
                }
                return Double.toString(result);
            }
        }

        public final void prepareForDisassemblyDisplay(DeciphererOutput out) {
            if (isInteger()) {
                int value = intValue();
                out.append(value);
                out.append(", ");
                if (value < 0) {
                    out.append('-');
                    out.append("0x");
                    out.append(Hex.w(-value));
                } else {
                    out.append("0x");
                    out.append(Hex.w(value));
                }
            } else {
                // the significand is 32 bits, stored big-endian starting from
                // the second byte.
                long significand = ((long) (bytes[1] & 0xFF)) << 24;
                significand |= (bytes[2] & 0xFF) << 16;
                significand |= (bytes[3] & 0xFF) << 8;
                significand |= (bytes[4] & 0xFF);
                // However, its top bit is sign bit, and should be replaced by 1
                // for decoding.
                boolean negativep = (significand & 0x80000000L) != 0;
                significand |= 0x80000000L;
                int exponent = (bytes[0] & 0xFF) - 0xA0;
                double result = significand * Math.pow(2, exponent);
                if (negativep) {
                    result = -result;
                }
                out.append(Double.toString(result));
                out.append(", ");
                if (negativep) {
                    out.append('-');
                }
                out.append("0x");
                out.append(Hex.t((int) significand));
                out.append('p');
                if (exponent >= 0) {
                    out.append('+');
                }
                out.append(exponent);
            }
        }
    }
}