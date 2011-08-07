package net.mirky.redis;

import java.io.PrintStream;

public abstract class Analyser {
    /**
     * After the initial guess has been made by filename suffix,
     * {@link Format#guess(String, byte[])} calls this method to possibly glean
     * some additional metadata from the filename. This is currently only used
     * for zx0 and zx3 to retrieve metadata from ZXLFN-structured filename.
     * 
     * Tasting is not permitted to change the analyser class.
     * 
     * @param format
     *            the guessed format
     * @param filename
     *            filename
     * @return adjusted format
     */
    public Format taste(Format format, String filename) {
        return format;
    }

    protected abstract ReconstructionDataCollector dis(Format format, byte[] data, PrintStream port) throws ImageError;

    /**
     * Perform a hexdump of an object in this analyser's format. Some
     * navigational aids -- such as track and sector numbers for disk images --
     * may be provided but no attempt should be made at dis:ing the object. The
     * default is a fairly plain hexdump that should suit most cases.
     */
    public void hexdump(Format format, byte[] data, PrintStream port) {
        Hex.dump(data, format.getOrigin(), format.getDecoding(), port);
    }
    
    public static abstract class Leaf extends Analyser {
        /**
         * Parent class for format parsers which dis' the input sequentially and
         * may stop at a point before end. The rest will be hexdumped instead.
         */
        public static abstract class PossiblyPartial extends Leaf {
            @Override
            protected final ReconstructionDataCollector dis(Format format, byte[] data, PrintStream port) {
                int disThreshold = disPartially(format, data, port);
                if (disThreshold != data.length) {
                    int origin = format.getOrigin();
                    if (disThreshold != 0) {
                        byte[] remainingData = new byte[data.length - disThreshold];
                        port.println("dis:ing succeeded only partially, hexdumping the rest");
                        System.arraycopy(data, disThreshold, remainingData, 0, remainingData.length);
                        Hex.dump(remainingData, origin + disThreshold, format.getDecoding(), port);
                    } else {
                        // dis:ing failed utterly, hexdumping instead
                        Hex.dump(data, origin, format.getDecoding(), port);
                    }
                }
                return null;
            }

            protected abstract int disPartially(Format format, byte[] data, PrintStream port);
        }
    }

    public static abstract class Container extends Analyser {
        protected abstract ReconstructionDataCollector extractFiles(Format format, byte[] fileData) throws ImageError;

        @Override
        final protected ReconstructionDataCollector dis(Format format, byte[] data, PrintStream port) throws ImageError {
            Hex.dump(data, format.getOrigin(), format.getDecoding(), port);
            return extractFiles(format, data);
        }
    }
}
