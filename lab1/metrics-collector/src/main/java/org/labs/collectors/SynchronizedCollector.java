package org.labs.collectors;

import org.labs.Histogram;
import org.labs.MetricsCollector;
import org.labs.Snapshot;

public class SynchronizedCollector implements MetricsCollector {

    private final long[] buckets = new long[Histogram.BUCKETS];
    private long count;
    private long sum;
    private long min = Long.MAX_VALUE;
    private long max;

    @Override
    public synchronized void record(long value) {
        buckets[Histogram.bucketOf(value)]++;
        count++;
        sum += value;
        if (value < min) {
            min = value;
        }
        if (value > max) {
            max = value;
        }
    }

    @Override
    public synchronized Snapshot snapshot() {
        return Snapshot.of(buckets.clone(), count, sum, min, max);
    }
}
