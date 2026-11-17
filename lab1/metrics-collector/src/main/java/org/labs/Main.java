package org.labs;

import org.labs.benchmark.Benchmark;
import org.labs.benchmark.ConsistencyCheck;
import org.labs.benchmark.Zipf;
import org.labs.collectors.CollectorType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Измените конфигурацию ниже, затем запустите Main без аргументов. */
public final class Main {
    // ---------------- конфигурация запуска ----------------
    enum Mode { BENCHMARK, CONSISTENCY, ALL }
    private static final Mode MODE = Mode.BENCHMARK;
    private static final CollectorType COLLECTOR = CollectorType.DOUBLE_BUFFER;

    // Замер скорости: один коллектор на JVM, baseline всегда в одном потоке.
    private static final int[] THREADS = {1, 2, 4, 8, 10};
    private static final long WARMUP_MILLIS = 5_000;
    private static final long RUN_MILLIS = 5_000;
    private static final int BENCHMARK_RUNS = 5;

    // Проверка согласованности снимков.
    private static final int WRITERS = 4;
    private static final int SNAPSHOTS = 10_000;
    private static final int CONSISTENCY_REPEATS = 3;

    // Входные данные генерируются до начала замеров.
    private static final int VALUES_SIZE = 1 << 20;
    private static final int MAX_VALUE = 1023;
    private static final double ZIPF_EXPONENT = 1.15;
    private static final long SEED = 42;

    // Полный прогон: наборы коллекторов и каталог результатов.
    private static final List<CollectorType> BENCHMARK_COLLECTORS = List.of(
            CollectorType.SINGLE_THREAD, CollectorType.EMPTY_LOCK, CollectorType.SYNCHRONIZED,
            CollectorType.STRIPED_LOCK, CollectorType.THREAD_LOCAL, CollectorType.DOUBLE_BUFFER);
    private static final List<CollectorType> CONSISTENCY_COLLECTORS = List.of(
            CollectorType.STRIPED_LOCK, CollectorType.THREAD_LOCAL,
            CollectorType.DOUBLE_BUFFER, CollectorType.DOUBLE_BUFFER_NO_RECHECK);
    private static final Path RESULTS_DIRECTORY = Path.of("results");
    private static final String PYTHON_EXECUTABLE = "python3";
    // ---------------- запуск и вывод результатов ----------------

    private Main() { }

    public static void main(String[] args) throws Exception {
        requireNoArguments(args);
        switch (MODE) {
            case BENCHMARK -> runBenchmark();
            case CONSISTENCY -> runConsistency();
            case ALL -> runAll();
        }
    }

    public static void requireNoArguments(String[] args) {
        if (args.length != 0) {
            throw new IllegalArgumentException("Настройте переменные в Main.java и запустите без аргументов");
        }
    }

    private static long[] values() {
        if (VALUES_SIZE <= 0 || MAX_VALUE <= 0 || !Double.isFinite(ZIPF_EXPONENT) || ZIPF_EXPONENT <= 0) {
            throw new IllegalArgumentException("VALUES_SIZE, MAX_VALUE and ZIPF_EXPONENT must be positive");
        }
        return Zipf.generate(VALUES_SIZE, MAX_VALUE, ZIPF_EXPONENT, SEED);
    }

    public static void runBenchmark() throws InterruptedException {
        benchmark(COLLECTOR);
    }

    private static void benchmark(CollectorType type) throws InterruptedException {
        if (THREADS.length == 0) throw new IllegalArgumentException("THREADS must not be empty");
        for (int threads : THREADS) {
            if (threads <= 0) throw new IllegalArgumentException("THREADS must contain positive values");
        }
        Benchmark.Settings settings = new Benchmark.Settings(WARMUP_MILLIS, RUN_MILLIS, BENCHMARK_RUNS);
        Benchmark.runConfigured(type, THREADS, values(), settings);
    }

    public static void runConsistency() throws InterruptedException {
        consistency(COLLECTOR);
    }

    private static void consistency(CollectorType type) throws InterruptedException {
        if (WRITERS <= 0 || SNAPSHOTS <= 0 || CONSISTENCY_REPEATS <= 0) {
            throw new IllegalArgumentException("WRITERS, SNAPSHOTS and CONSISTENCY_REPEATS must be positive");
        }
        if (!type.threadSafe()) throw new IllegalArgumentException("Consistency check requires a concurrent collector");
        ConsistencyCheck.runConfigured(type, values(), WRITERS, SNAPSHOTS, CONSISTENCY_REPEATS);
    }

    private static void runAll() throws Exception {
        Files.createDirectories(RESULTS_DIRECTORY);
        writeEnvironment();
        Path throughput = RESULTS_DIRECTORY.resolve("throughput.csv");
        try (BufferedWriter out = Files.newBufferedWriter(throughput)) {
            out.write("collector,threads,mops\n");
            for (int i = 0; i < BENCHMARK_COLLECTORS.size(); i++) {
                runWorker(BenchmarkWorker.class, i, out);
            }
        }
        try (BufferedWriter out = Files.newBufferedWriter(RESULTS_DIRECTORY.resolve("consistency.csv"))) {
            out.write("collector,snapshots,broken_percent,buckets_less,buckets_greater,record_calls,final_count,final_diff\n");
            for (int i = 0; i < CONSISTENCY_COLLECTORS.size(); i++) {
                runWorker(ConsistencyWorker.class, i, out);
            }
        }
        Process plot = new ProcessBuilder(PYTHON_EXECUTABLE, "scripts/plot.py", throughput.toString(),
                RESULTS_DIRECTORY.resolve("throughput.svg").toString()).inheritIO().start();
        if (plot.waitFor() != 0) throw new IOException("Не удалось построить график");
        System.out.println("Готово: " + RESULTS_DIRECTORY.toAbsolutePath());
    }

    private static void runWorker(Class<?> worker, int index, BufferedWriter out) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        Path java = Path.of(System.getProperty("java.home"), "bin", executable);
        // Передаётся только индекс работы; все параметры читает тот же скомпилированный Main.
        Process process = new ProcessBuilder(java.toString(), "-cp", System.getProperty("java.class.path"),
                worker.getName(), Integer.toString(index)).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        try {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = reader.readLine()) != null;) {
                    System.out.println(line);
                    out.write(line);
                    out.newLine();
                }
            }
            if (process.waitFor() != 0) throw new IOException("Неудачный прогон " + worker.getSimpleName() + " #" + index);
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    private static void writeEnvironment() throws IOException, InterruptedException {
        Path env = RESULTS_DIRECTORY.resolve("env.txt");
        Files.writeString(env, "Java " + System.getProperty("java.version") + "\n"
                + System.getProperty("os.name") + " " + System.getProperty("os.version") + " "
                + System.getProperty("os.arch") + "\nlogical cpus: " + Runtime.getRuntime().availableProcessors() + "\n");
        List<String> cpuCommand = System.getProperty("os.name").startsWith("Mac")
                ? List.of("sysctl", "machdep.cpu.brand_string", "hw.ncpu", "hw.perflevel0.physicalcpu", "hw.perflevel1.physicalcpu")
                : List.of("lscpu");
        try {
            new ProcessBuilder(cpuCommand).redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(env.toFile())).start().waitFor();
        } catch (IOException ignored) {
            // На системах без lscpu/sysctl остаются данные JVM и ОС.
        }
    }

    /** Точка входа скрипта полного прогона; настройки также только в Main. */
    public static final class AllRuns {
        public static void main(String[] args) throws Exception {
            requireNoArguments(args);
            runAll();
        }
    }

    /** Внутренние точки входа дочерних JVM, по одной на коллектор. */
    public static final class BenchmarkWorker {
        public static void main(String[] args) throws InterruptedException {
            benchmark(BENCHMARK_COLLECTORS.get(Integer.parseInt(args[0])));
        }
    }

    public static final class ConsistencyWorker {
        public static void main(String[] args) throws InterruptedException {
            consistency(CONSISTENCY_COLLECTORS.get(Integer.parseInt(args[0])));
        }
    }
}
