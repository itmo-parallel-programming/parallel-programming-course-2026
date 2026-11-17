package lab2;

import java.util.function.Supplier;

/** Все параметры меняются здесь перед компиляцией и запуском Main без аргументов. */
public final class Main {
    // ---------------- конфигурация запуска ----------------
    enum Experiment { SCHEDULER, STRESS, MINI_STRESS }
    enum ScheduleStrategy { FIRST, RANDOM, RANDOMS, REPLAY, DFS, BOUNDED }

    private static final Experiment EXPERIMENT = Experiment.SCHEDULER;

    // Этапы 1–6. TARGET: withdraw-v0/v1/v2, transfer, collector-<variant>-<writers>x<n>.
    private static final String TARGET = "withdraw-v1";
    private static final ScheduleStrategy STRATEGY = ScheduleStrategy.FIRST;
    private static final boolean LOG = false; // пошаговый лог FIRST, RANDOM и REPLAY
    private static final long RANDOM_SEED = 0; // первый seed для RANDOMS
    private static final int RANDOM_RUNS = 1_000;
    private static final int REPLAY_RUNS = 10; // проверка первой ошибки RANDOMS
    private static final String REPLAY_TRACE = "AAAABB";
    private static final int PREEMPTION_BOUND = 2;

    // Этап 0: обычные потоки, без планировщика.
    private static final int STRESS_RUNS = 10_000;
    private static final String STRESS_WINDOW = "none"; // none | yield | sleep
    private static final String STRESS_VERSION = "v1"; // v1 | v2

    // Этап 7: Store Buffering. В naive одна ячейка и rounds * cells раундов.
    private static final String MEMORY_MODE = "acqrel"; // plain | acqrel | volatile | naive
    private static final int MEMORY_ROUNDS = 2_000;
    private static final int MEMORY_CELLS = 10_000;
    // ---------------- запуск и вывод результатов ----------------

    record Outcome(String trace, String failure) { }

    static Outcome runOnce(Supplier<Targets.Target> factory, Strategy st, boolean log) {
        Scheduler s = new Scheduler();
        Targets.Target t = factory.get();
        s.logging = log;
        t.setup(s);
        st.beginRun();
        s.run(st);
        if (log) s.log.forEach(l -> System.out.println("  " + l));
        String failure = s.deadlock != null ? "DEADLOCK " + s.deadlock : t.check();
        return new Outcome(s.traceString(), failure);
    }

    static void single(Supplier<Targets.Target> f, Strategy st, boolean log) {
        Outcome o = runOnce(f, st, log);
        System.out.println("schedule " + o.trace + " -> " + (o.failure == null ? "ok" : o.failure));
    }

    static void explore(Supplier<Targets.Target> f, int bound) {
        Strategies.Dfs dfs = new Strategies.Dfs(bound);
        long t0 = System.nanoTime();
        int explored = 0, fail = 0;
        Outcome first = null;
        do {
            Outcome o = runOnce(f, dfs, false);
            explored++;
            if (o.failure != null) { fail++; if (first == null) first = o; }
        } while (dfs.endRun());
        double sec = (System.nanoTime() - t0) / 1e9;
        System.out.printf("explored %d schedules, %d fail  (%.2f s)%n", explored, fail, sec);
        if (first != null) System.out.println("first failing: " + first.trace + " -> " + first.failure);
    }

    static void randoms(Supplier<Targets.Target> f, long startSeed, int count, int replayRuns) {
        if (count <= 0 || replayRuns <= 0) throw new IllegalArgumentException("RANDOM_RUNS and REPLAY_RUNS must be positive");
        Math.addExact(startSeed, count - 1L);
        int fail = 0; long firstSeed = 0;
        Outcome first = null;
        for (int i = 0; i < count; i++) {
            long seed = startSeed + i;
            Outcome o = runOnce(f, new Strategies.RandomPick(seed), false);
            if (o.failure != null) { fail++; if (first == null) { firstSeed = seed; first = o; } }
        }
        System.out.printf("random: %d seeds, %d fail (%.1f%%)%n", count, fail, 100.0 * fail / count);
        if (first != null) {
            Outcome o = first;
            System.out.println("first failing seed " + firstSeed + ": " + o.trace + " -> " + o.failure);
            int same = 0;
            for (int i = 0; i < replayRuns; i++) {
                Outcome r = runOnce(f, new Strategies.Replay(o.trace), false);
                if (r.trace.equals(o.trace) && o.failure.equals(r.failure)) same++;
            }
            System.out.println("replay " + o.trace + ": same failure " + same + "/" + replayRuns);
        }
    }

    public static void main(String[] args) throws Exception {
        requireNoArguments(args);
        switch (EXPERIMENT) {
            case SCHEDULER -> runScheduler();
            case STRESS -> runStress();
            case MINI_STRESS -> runMiniStress();
        }
    }

    static void requireNoArguments(String[] args) {
        if (args.length != 0) throw new IllegalArgumentException("Настройте переменные в Main.java и запустите без аргументов");
    }

    static void runStress() throws Exception {
        Stress.run(STRESS_RUNS, STRESS_WINDOW, STRESS_VERSION);
    }

    static void runMiniStress() throws Exception {
        MiniStress.run(MEMORY_MODE, MEMORY_ROUNDS, MEMORY_CELLS);
    }

    private static void runScheduler() {
        Supplier<Targets.Target> f = Targets.byName(TARGET);
        switch (STRATEGY) {
            case FIRST -> single(f, new Strategies.FirstAlive(), LOG);
            case RANDOM -> single(f, new Strategies.RandomPick(RANDOM_SEED), LOG);
            case REPLAY -> single(f, new Strategies.Replay(REPLAY_TRACE), LOG);
            case RANDOMS -> randoms(f, RANDOM_SEED, RANDOM_RUNS, REPLAY_RUNS);
            case DFS -> explore(f, Integer.MAX_VALUE);
            case BOUNDED -> {
                if (PREEMPTION_BOUND < 0) throw new IllegalArgumentException("PREEMPTION_BOUND must be non-negative");
                explore(f, PREEMPTION_BOUND);
            }
        }
    }
}
