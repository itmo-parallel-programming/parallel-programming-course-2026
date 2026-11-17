package org.labs.benchmark;

import java.util.Arrays;
import java.util.SplittableRandom;

public final class Zipf {

    private Zipf() {
    }

    public static long[] generate(int size, int maxValue, double exponent, long seed) {
        // cdf[k - 1] = P(X <= k)
        double[] cdf = new double[maxValue];
        double acc = 0;
        for (int k = 1; k <= maxValue; k++) {
            acc += 1.0 / Math.pow(k, exponent);
            cdf[k - 1] = acc;
        }
        for (int i = 0; i < maxValue; i++) {
            cdf[i] /= acc;
        }
        cdf[maxValue - 1] = 1.0;

        SplittableRandom random = new SplittableRandom(seed);
        long[] values = new long[size];
        for (int i = 0; i < size; i++) {
            int pos = Arrays.binarySearch(cdf, random.nextDouble());
            int index = pos >= 0 ? pos : -pos - 1;
            values[i] = index + 1;
        }
        return values;
    }
}
