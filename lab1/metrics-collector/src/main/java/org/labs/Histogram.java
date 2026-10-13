package org.labs;

public final class Histogram {

    public static final int BUCKETS = 256;
    public static final int BUCKET_WIDTH_MS = 4;

    private Histogram() {
    }

    public static int bucketOf(long value) {
        return (int) Math.min(value / BUCKET_WIDTH_MS, BUCKETS - 1);
    }

    public static long percentile(long[] buckets, long count, double q) {
        double threshold = count * q;
        long accumulated = 0;
        for (int i = 0; i < buckets.length; i++) {
            accumulated += buckets[i];
            if (accumulated >= threshold) {
                return (long) i * BUCKET_WIDTH_MS;
            }
        }
        return (long) (buckets.length - 1) * BUCKET_WIDTH_MS;
    }
}
