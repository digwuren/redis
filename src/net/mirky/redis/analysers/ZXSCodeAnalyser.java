package net.mirky.redis.analysers;

import java.util.regex.Matcher;

import net.mirky.redis.Format;

/* This subclass of {@link RawBinaryParser} exists so that it could taste the ZXLFN file name 
 * which the main raw binary analyser doesn't do and shouldn't do.
 */
@Format.Options(value = "zxs-code/decoding:decoding=zx-spectrum/origin:unsigned-hex=0x8000/cpu:lang=z80/api:api=zxs/entry:entry",
        aliases = ".zx3")
public final class ZXSCodeAnalyser extends RawBinaryAnalyser {
    @Override
    public final Format taste(Format format, String filename) {
        Matcher matcher = ZXSTapeBlockPairer.ZXLFN_REGEX.matcher(filename);
        if (matcher.matches()) {
            int param1 = ZXSTapeBlockPairer.parseZxlfnParam(matcher.group(2));
            try {
                if (param1 != -1) {
                    return new Format(format, "origin", Integer.toString(param1));
                }
            } catch (Format.OptionError e) {
                throw new RuntimeException("bug detected", e);
            }
        }
        return format;
    }

}
