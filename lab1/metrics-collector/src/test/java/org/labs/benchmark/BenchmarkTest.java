package org.labs.benchmark;

import org.junit.jupiter.api.Test;
import org.labs.collectors.CollectorType;
import org.labs.collectors.SynchronizedCollector;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

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

    @Test
    void configuredRunnerUsesRequestedThreadsAndKeepsBaselineSingleThreaded() throws InterruptedException {
        PrintStream original = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            Benchmark.Settings settings = new Benchmark.Settings(10, 30, 1);
            Benchmark.runConfigured(CollectorType.DOUBLE_BUFFER, new int[]{1, 2}, VALUES, settings);
            Benchmark.runConfigured(CollectorType.SINGLE_THREAD, new int[]{2, 4}, VALUES, settings);
        } finally {
            System.setOut(original);
        }
        String[] lines = output.toString(StandardCharsets.UTF_8).strip().split("\\R");
        assertEquals(3, lines.length);
        assertTrue(lines[0].startsWith("double-buffer,1,"));
        assertTrue(lines[1].startsWith("double-buffer,2,"));
        assertTrue(lines[2].startsWith("single-thread,1,"));
    }
}
