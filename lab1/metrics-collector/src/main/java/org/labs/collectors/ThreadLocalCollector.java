package org.labs.collectors;

import org.labs.Histogram;
import org.labs.MetricsCollector;
import org.labs.Snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

public class ThreadLocalCollector implements MetricsCollector {

    static final class ThreadState {
        final AtomicLongArray buckets = new AtomicLongArray(Histogram.BUCKETS);
        final AtomicLong count = new AtomicLong();
        final AtomicLong sum = new AtomicLong();
        final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        final AtomicLong max = new AtomicLong(0);
    }

    private final List<ThreadState> allStates = new ArrayList<>();
    private final Object listLock = new Object();

    private final ThreadLocal<ThreadState> myState = ThreadLocal.withInitial(() -> {
        ThreadState s = new ThreadState();
        synchronized (listLock) {
            allStates.add(s);
        }
        return s;
    });

    @Override
    public void record(long value) {
        ThreadState s = myState.get();
        int b = Histogram.bucketOf(value);

        s.buckets.setRelease(b, s.buckets.getPlain(b) + 1);
        s.count.setRelease(s.count.getPlain() + 1);
        s.sum.setRelease(s.sum.getPlain() + value);

        if (value < s.min.getPlain()) {
            s.min.setRelease(value);
        }
        if (value > s.max.getPlain()) {
            s.max.setRelease(value);
        }
    }

    @Override
    public Snapshot snapshot() {
        List<ThreadState> copyOfStates;
        synchronized (listLock) {
            copyOfStates = new ArrayList<>(allStates);
        }

        long[] out = new long[Histogram.BUCKETS];
        long count = 0;
        long sum = 0;
        long min = Long.MAX_VALUE;
        long max = 0;

        for (ThreadState s : copyOfStates) {
            for (int i = 0; i < Histogram.BUCKETS; i++) {
                out[i] += s.buckets.get(i);
            }
            count += s.count.get();
            sum += s.sum.get();
            min = Math.min(min, s.min.get());
            max = Math.max(max, s.max.get());
        }
        return Snapshot.of(out, count, sum, min, max);
    }
}
