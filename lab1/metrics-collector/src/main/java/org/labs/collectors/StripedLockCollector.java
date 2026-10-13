package org.labs.collectors;

import org.labs.Histogram;
import org.labs.MetricsCollector;
import org.labs.Snapshot;

import java.util.concurrent.atomic.AtomicLong;

public class StripedLockCollector implements MetricsCollector {

    public static final int STRIPES = 16;

    private final Object[] locks = new Object[STRIPES];
    private final long[] buckets = new long[Histogram.BUCKETS];

    private final AtomicLong count = new AtomicLong();
    private final AtomicLong sum = new AtomicLong();
    private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong max = new AtomicLong();

    public StripedLockCollector() {
        for (int i = 0; i < STRIPES; i++) {
            locks[i] = new Object();
        }
    }

    @Override
    public void record(long value) {
        int b = Histogram.bucketOf(value);
        synchronized (locks[b % STRIPES]) {
            buckets[b]++;
        }

        count.incrementAndGet();
        sum.addAndGet(value);

        long curMin = min.get();
        while (value < curMin && !min.compareAndSet(curMin, value)) {
            curMin = min.get();
        }
        long curMax = max.get();
        while (value > curMax && !max.compareAndSet(curMax, value)) {
            curMax = max.get();
        }
    }

    @Override
    public Snapshot snapshot() {
        long[] out = new long[Histogram.BUCKETS];
        for (int stripe = 0; stripe < STRIPES; stripe++) {
            synchronized (locks[stripe]) {
                for (int b = stripe; b < Histogram.BUCKETS; b += STRIPES) {
                    out[b] = buckets[b];
                }
            }
        }
        return Snapshot.of(out, count.get(), sum.get(), min.get(), max.get());
    }
}
