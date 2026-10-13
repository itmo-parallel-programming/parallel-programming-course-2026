package org.labs.collectors;

import org.labs.Histogram;
import org.labs.MetricsCollector;
import org.labs.Snapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DoubleBufferCollector implements MetricsCollector {

    static final int NOWHERE = -1;

    static final class ThreadBuffers {
        final long[][] buckets = new long[2][Histogram.BUCKETS];
        final long[] count = new long[2];
        final long[] sum = new long[2];
        final long[] min = {Long.MAX_VALUE, Long.MAX_VALUE};
        final long[] max = {0, 0};

        final AtomicInteger inside = new AtomicInteger(NOWHERE);
    }

    volatile int active = 0;

    private final Object snapLock = new Object();
    private final List<ThreadBuffers> allBuffers = new ArrayList<>();

    private final ThreadLocal<ThreadBuffers> myBuffers = ThreadLocal.withInitial(() -> {
        ThreadBuffers s = new ThreadBuffers();
        synchronized (snapLock) {
            allBuffers.add(s);
        }
        return s;
    });

    private final long[] totalBuckets = new long[Histogram.BUCKETS];
    private long totalCount;
    private long totalSum;
    private long totalMin = Long.MAX_VALUE;
    private long totalMax;

    @Override
    public void record(long value) {
        ThreadBuffers my = myBuffers.get();
        int b = enter(my);

        int bucket = Histogram.bucketOf(value);
        my.buckets[b][bucket]++;
        my.count[b]++;
        my.sum[b] += value;
        if (value < my.min[b]) {
            my.min[b] = value;
        }
        if (value > my.max[b]) {
            my.max[b] = value;
        }

        my.inside.setRelease(NOWHERE);
    }

    int enter(ThreadBuffers my) {
        while (true) {
            int b = active;
            my.inside.set(b);
            if (active == b) {
                return b;
            }
            my.inside.set(NOWHERE);
        }
    }

    @Override
    public Snapshot snapshot() {
        synchronized (snapLock) {
            int old = active;
            active = 1 - old;

            for (ThreadBuffers s : allBuffers) {
                while (s.inside.get() == old) {
                    Thread.onSpinWait();
                }
            }

            for (ThreadBuffers s : allBuffers) {
                drain(s, old);
            }

            return Snapshot.of(totalBuckets.clone(), totalCount, totalSum, totalMin, totalMax);
        }
    }

    private void drain(ThreadBuffers s, int old) {
        long[] buckets = s.buckets[old];
        for (int i = 0; i < Histogram.BUCKETS; i++) {
            totalBuckets[i] += buckets[i];
        }
        totalCount += s.count[old];
        totalSum += s.sum[old];
        totalMin = Math.min(totalMin, s.min[old]);
        totalMax = Math.max(totalMax, s.max[old]);

        Arrays.fill(buckets, 0);
        s.count[old] = 0;
        s.sum[old] = 0;
        s.min[old] = Long.MAX_VALUE;
        s.max[old] = 0;
    }
}
