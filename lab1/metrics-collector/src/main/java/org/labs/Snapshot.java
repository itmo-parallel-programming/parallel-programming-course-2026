package org.labs;

import java.util.Arrays;

public record Snapshot(
        long[] buckets,
        long count,
        long sum,
        long min,
        long max,
        long p50,
        long p99
) {

    public Snapshot {
        if (buckets.length != Histogram.BUCKETS) {
            throw new IllegalArgumentException("buckets.length must be " + Histogram.BUCKETS + ", got " + buckets.length);
        }
    }

    public static Snapshot of(long[] buckets, long count, long sum, long min, long max) {
        return new Snapshot(
                buckets, count, sum, min, max,
                Histogram.percentile(buckets, count, 0.50),
                Histogram.percentile(buckets, count, 0.99)
        );
    }

    public static Snapshot empty() {
        return of(new long[Histogram.BUCKETS], 0, 0, Long.MAX_VALUE, 0);
    }

    public long bucketsSum() {
        long total = 0;
        for (long b : buckets) {
            total += b;
        }
        return total;
    }

    public double mean() {
        return count == 0 ? 0 : (double) sum / count;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Snapshot s
                && count == s.count && sum == s.sum && min == s.min && max == s.max
                && p50 == s.p50 && p99 == s.p99
                && Arrays.equals(buckets, s.buckets);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(buckets) + Long.hashCode(count);
    }

    @Override
    public String toString() {
        return "Snapshot[count=%d, sum=%d, min=%d, max=%d, p50=%d, p99=%d, bucketsSum=%d]"
                .formatted(count, sum, min, max, p50, p99, bucketsSum());
    }
}
