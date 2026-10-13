package org.labs.collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.labs.Histogram;
import org.labs.MetricsCollector;
import org.labs.Snapshot;

import java.util.SplittableRandom;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Многопоточная точность: после join итог должен в точности совпадать с тем, что записали потоки.
 */
class ConcurrentCollectorTest {

    private static final int THREADS = 4;
    private static final int RECORDS_PER_THREAD = 200_000;

    @ParameterizedTest
    @EnumSource(value = CollectorType.class, names = {"SYNCHRONIZED", "STRIPED_LOCK", "THREAD_LOCAL", "DOUBLE_BUFFER"})
    void noRecordIsLostOrDuplicated(CollectorType type) throws InterruptedException {
        MetricsCollector collector = type.create();
        long[][] perThread = new long[THREADS][];
        for (int t = 0; t < THREADS; t++) {
            perThread[t] = new SplittableRandom(t).longs(RECORDS_PER_THREAD, 0, 1100).toArray();
        }

        // Параллельно с записью дёргаем snapshot(): для двойной буферизации это гоняет буферы туда-обратно.
        Thread[] writers = new Thread[THREADS];
        CountDownLatch start = new CountDownLatch(1);
        for (int t = 0; t < THREADS; t++) {
            long[] values = perThread[t];
            writers[t] = new Thread(() -> {
                awaitQuietly(start);
                for (long v : values) {
                    collector.record(v);
                }
            });
            writers[t].start();
        }
        start.countDown();
        boolean anyAlive = true;
        while (anyAlive) {
            collector.snapshot();
            anyAlive = false;
            for (Thread w : writers) {
                anyAlive |= w.isAlive();
            }
        }
        for (Thread w : writers) {
            w.join();
        }

        long[] expectedBuckets = new long[Histogram.BUCKETS];
        long expectedSum = 0;
        long expectedMin = Long.MAX_VALUE;
        long expectedMax = 0;
        for (long[] values : perThread) {
            for (long v : values) {
                expectedBuckets[Histogram.bucketOf(v)]++;
                expectedSum += v;
                expectedMin = Math.min(expectedMin, v);
                expectedMax = Math.max(expectedMax, v);
            }
        }

        Snapshot snapshot = collector.snapshot();
        assertEquals((long) THREADS * RECORDS_PER_THREAD, snapshot.count());
        assertEquals(expectedSum, snapshot.sum());
        assertEquals(expectedMin, snapshot.min());
        assertEquals(expectedMax, snapshot.max());
        assertArrayEquals(expectedBuckets, snapshot.buckets());
    }

    @Test
    void doubleBufferSnapshotsAreAlwaysConsistentUnderLoad() throws InterruptedException {
        MetricsCollector collector = new DoubleBufferCollector();
        Thread[] writers = new Thread[THREADS];
        for (int t = 0; t < THREADS; t++) {
            writers[t] = new Thread(() -> {
                for (int i = 0; i < RECORDS_PER_THREAD; i++) {
                    collector.record(i % 1500);
                }
            });
            writers[t].start();
        }

        long previousCount = 0;
        for (int i = 0; i < 5_000; i++) {
            Snapshot snapshot = collector.snapshot();
            assertEquals(snapshot.count(), snapshot.bucketsSum());
            assertTrue(snapshot.count() >= previousCount, "count не должен уменьшаться");
            previousCount = snapshot.count();
        }
        for (Thread w : writers) {
            w.join();
        }
        assertEquals((long) THREADS * RECORDS_PER_THREAD, collector.snapshot().count());
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
