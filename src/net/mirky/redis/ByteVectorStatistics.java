package net.mirky.redis;

final class ByteVectorStatistics {
    private final int size;
    private final int[] counts;
    private int distinctBytes;
    private final double informationDensity;

    ByteVectorStatistics(byte[] data) {
        size = data.length;
        counts = new int[256];
        for (int i = 0; i < 256; i++) {
            counts[i] = 0;
        }
        distinctBytes = 0;
        for (byte b : data) {
            if (counts[b & 0xFF] == 0) {
                distinctBytes++;
            }
            counts[b & 0xFF]++;
        }
        if (data.length != 0) {
            double sum = 0;
            for (int i = 0; i < 256; i++) {
                if (counts[i] != 0) {
                    double prob = ((double) counts[i]) / data.length;
                    sum += prob * Math.log(prob);
                }
            }
            informationDensity = - sum / Math.log(256);
        } else {
            informationDensity = Double.NaN;
        }
    }

    final void printOut(String linePrefix) {
        if (!Double.isNaN(informationDensity)) {
            System.out.printf(linePrefix + "Estimated information density: %.1f%% (%.2f b/B)\n",
                    new Double(informationDensity * 100),
                    new Double(informationDensity * 8));
    	} else {
            System.out.println(linePrefix + "Estimated information density: 0/0");
    	}
    	System.out.println(linePrefix + "Number of distinct bytes seen: " + distinctBytes);
    	System.out.println(linePrefix + "Frequency of byte values as percentages:");
    	System.out.print(linePrefix + "  ");
    	for (int i = 0; i < 16; i++) {
    		System.out.printf("    %1X", new Integer(i));
    	}
    	System.out.println();
    	System.out.print(linePrefix + "  ");
    	for (int i = 0; i < 83; i++) {
    		System.out.print('-');
    	}
    	System.out.println();
    	int[] colSums = new int[16];
    	for (int row = 0; row < 16; row++) {
    		System.out.printf("%s%1X |", linePrefix, new Integer(row));
    		int rowSum = 0;
    		for (int col = 0; col < 16; col++) {
    			int count = counts[row * 16 + col];
    			rowSum += count;
    			colSums[col] += count;
    			printCount(count, size);
    		}
    		System.out.print(" |");
    		printCount(rowSum, size);
    		System.out.println();
    	}
    	System.out.print(linePrefix + "  ");
    	for (int i = 0; i < 83; i++) {
    		System.out.print('-');
    	}
    	System.out.println();
    	System.out.print(linePrefix + "   ");
    	for (int i = 0; i < 16; i++) {
    		printCount(colSums[i], size);
    	}
    	System.out.println();
    }

    private static final void printCount(int count, int total) {
    	if (count == 0) {
    		System.out.print("   . ");
    	} else {
    		String percentage = String.format("%.1f", new Double(count * 100.0 / total));
    		if (percentage.charAt(0) == '0') {
    			percentage = percentage.substring(1);
    		}
    		System.out.printf("%5s", percentage);
    	}
    }
}