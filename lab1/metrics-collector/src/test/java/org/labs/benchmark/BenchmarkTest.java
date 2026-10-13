package org.labs.benchmark;

import org.junit.jupiter.api.Test;
import org.labs.collectors.CollectorType;
import org.labs.collectors.SynchronizedCollector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkTest {

    private static final long[] VALUES = Zipf.generate(10_000, 1023, 1.15, 1);

    @Test
    void runCountsOperationsOfAllThreads() throws InterruptedException {
        SynchronizedCollector collector = new SynchronizedCollector();

        double opsPerSec = Benchmark.run(collector, VALUES, 2, 100);

        assertTrue(opsPerSec > 0);
        assertTrue(collector.snapshot().count() > 0);
    }

    @Test
    void measurePointReturnsMedianOfRuns() throws InterruptedException {
        Benchmark.Point point = Benchmark.measurePoint(
                CollectorType.THREAD_LOCAL, VALUES, 2, new Benchmark.Settings(50, 50, 3));

        assertEquals(3, point.runs().length);
        assertEquals(Benchmark.median(point.runs()), point.opsPerSec());
        assertTrue(point.finalCount() > 0);
        assertTrue(point.csv().startsWith("thread-local,2,"));
    }

    @Test
    void medianOfOddAndEvenSizes() {
        assertEquals(3.0, Benchmark.median(new double[]{5, 1, 3}));
        assertEquals(2.5, Benchmark.median(new double[]{4, 1, 3, 2}));
    }
}
