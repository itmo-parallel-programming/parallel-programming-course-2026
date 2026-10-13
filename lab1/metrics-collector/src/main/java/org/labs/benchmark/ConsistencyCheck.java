package org.labs.benchmark;

import org.labs.MetricsCollector;
import org.labs.Snapshot;
import org.labs.collectors.CollectorType;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;

public final class ConsistencyCheck {

    public static final int DEFAULT_WRITERS = 4;
    public static final int DEFAULT_SNAPSHOTS = 10_000;

    private ConsistencyCheck() {
    }

    public static void main(String[] args) throws InterruptedException {
        if (args.length == 0) {
            System.err.println("Использование: ConsistencyCheck <collector> [--writers N] [--snapshots N] [--repeat N]");
            System.err.println("Коллекторы: " + CollectorType.ids());
            System.exit(2);
        }

        CollectorType type = CollectorType.byId(args[0]);
        int writers = DEFAULT_WRITERS;
        int snapshots = DEFAULT_SNAPSHOTS;
        int repeat = 1;
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--writers" -> writers = Integer.parseInt(args[++i]);
                case "--snapshots" -> snapshots = Integer.parseInt(args[++i]);
                case "--repeat" -> repeat = Integer.parseInt(args[++i]);
                default -> throw new IllegalArgumentException("Неизвестный аргумент: " + args[i]);
            }
        }

        long[] values = Zipf.generate();
        System.err.printf("%s: %d писателя, %d снимков%n", type.title(), writers, snapshots);
        for (int r = 0; r < repeat; r++) {
            Report report = run(type.create(), values, writers, snapshots);
            System.err.printf(Locale.ROOT,
                    "  битых %.2f%% (sum<count: %d, sum>count: %d); вызовов record=%d, итоговый count=%d, разница=%d%n",
                    report.brokenPercent(), report.bucketsLess(), report.bucketsGreater(),
                    report.recordCalls(), report.finalCount(), report.finalDiff());
            System.out.println(report.csv(type));
        }
    }

    public static Report run(MetricsCollector collector, long[] values, int writers, int snapshots) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(writers);
        CountDownLatch start = new CountDownLatch(1);
        Flag flag = new Flag();
        long[] calls = new long[writers];
        Thread[] threads = new Thread[writers];

        for (int k = 0; k < writers; k++) {
            int id = k;
            threads[k] = new Thread(() -> {
                long localCount = 0;
                int i = (id * 1000) % values.length;
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                while (!flag.stop) {
                    collector.record(values[i]);
                    localCount++;
                    if (++i == values.length) {
                        i = 0;
                    }
                }
                calls[id] = localCount;
            }, "writer-" + k);
            threads[k].start();
        }

        ready.await();
        start.countDown();

        int broken = 0;
        int less = 0;
        int greater = 0;
        for (int s = 0; s < snapshots; s++) {
            Snapshot snap = collector.snapshot();
            long bucketsSum = snap.bucketsSum();
            if (bucketsSum != snap.count()) {
                broken++;
                if (bucketsSum < snap.count()) {
                    less++;
                } else {
                    greater++;
                }
            }
        }

        flag.stop = true;
        for (Thread t : threads) {
            t.join();
        }

        long totalCalls = 0;
        for (long c : calls) {
            totalCalls += c;
        }
        Snapshot last = collector.snapshot();
        return new Report(snapshots, broken, less, greater, totalCalls, last.count(), last.bucketsSum());
    }

    public record Report(int snapshots, int broken, int bucketsLess, int bucketsGreater,
                         long recordCalls, long finalCount, long finalBucketsSum) {

        public double brokenPercent() {
            return snapshots == 0 ? 0 : 100.0 * broken / snapshots;
        }

        public long finalDiff() {
            return finalCount - recordCalls;
        }

        public String csv(CollectorType type) {
            return String.format(Locale.ROOT, "%s,%d,%.2f,%d,%d,%d,%d,%d",
                    type.id(), snapshots, brokenPercent(), bucketsLess, bucketsGreater,
                    recordCalls, finalCount, finalDiff());
        }
    }

    private static final class Flag {
        volatile boolean stop;
    }

}
