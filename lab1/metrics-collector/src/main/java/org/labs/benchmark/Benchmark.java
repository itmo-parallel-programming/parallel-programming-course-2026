package org.labs.benchmark;

import org.labs.MetricsCollector;
import org.labs.collectors.CollectorType;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

public final class Benchmark {

    private Benchmark() {
    }

    public static void main(String[] args) throws InterruptedException {
        org.labs.Main.requireNoArguments(args);
        org.labs.Main.runBenchmark();
    }

    public static void runConfigured(CollectorType type, int[] threads, long[] values,
                                     Settings settings) throws InterruptedException {
        System.err.printf("%s, cpus=%d, warmup=%dms, %d x %dms%n",
                type.title(), Runtime.getRuntime().availableProcessors(),
                settings.warmupMillis(), settings.runs(), settings.runMillis());
        // Однопоточный baseline нельзя запускать несколькими писателями.
        int[] actualThreads = type.threadSafe() ? threads : new int[]{1};
        for (int t : actualThreads) {
            Point point = measurePoint(type, values, t, settings);
            System.err.printf(Locale.ROOT, "  T=%-2d  %8.2f млн оп/сек  runs=%s  count=%d%n",
                    t, point.opsPerSec() / 1e6, formatRuns(point.runs()), point.finalCount());
            System.out.println(point.csv());
        }
    }

    private static String formatRuns(double[] runs) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < runs.length; i++) {
            sb.append(i == 0 ? "" : " ").append(String.format(Locale.ROOT, "%.2f", runs[i] / 1e6));
        }
        return sb.append(']').toString();
    }

    public static double run(MetricsCollector collector, long[] values, int threads, long millis) throws InterruptedException {
        Run run = new Run();
        CountDownLatch start = new CountDownLatch(1);
        long[] ops = new long[threads];
        Thread[] workers = new Thread[threads];

        for (int k = 0; k < threads; k++) {
            int id = k;
            workers[k] = new Thread(() -> {
                long localCount = 0;
                int i = (id * 1000) % values.length;
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                while (!run.stop) {
                    collector.record(values[i]);
                    localCount++;
                    i++;
                    if (i == values.length) {
                        i = 0;
                    }
                }
                ops[id] = localCount;
            }, "bench-" + k);
            workers[k].start();
        }

        long t0 = System.nanoTime();
        start.countDown();
        Thread.sleep(millis);
        run.stop = true;
        long t1 = System.nanoTime();
        for (Thread worker : workers) {
            worker.join();
        }

        return Arrays.stream(ops).sum() / ((t1 - t0) / 1e9);
    }

    public static Point measurePoint(CollectorType type, long[] values, int threads, Settings settings) throws InterruptedException {
        MetricsCollector collector = type.create();
        run(collector, values, threads, settings.warmupMillis());

        double[] results = new double[settings.runs()];
        for (int r = 0; r < settings.runs(); r++) {
            results[r] = run(collector, values, threads, settings.runMillis());
        }
        long count = collector.snapshot().count();
        return new Point(type, threads, median(results), results, count);
    }

    static double median(double[] xs) {
        double[] sorted = xs.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
    }

    public record Settings(long warmupMillis, long runMillis, int runs) {

        public Settings {
            if (warmupMillis < 0 || runMillis <= 0 || runs <= 0) {
                throw new IllegalArgumentException("warmupMillis >= 0, runMillis > 0, runs > 0 required");
            }
        }
    }

    public record Point(CollectorType type, int threads, double opsPerSec, double[] runs, long finalCount) {

        public String csv() {
            return String.format(Locale.ROOT, "%s,%d,%.3f", type.id(), threads, opsPerSec / 1e6);
        }
    }

    private static final class Run {
        volatile boolean stop;
    }
}
