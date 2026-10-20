package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

/* Этап 1: планировщик, который ведёт настоящие потоки строго по одному (передача хода через семафоры).
 * Этап 2: обнаружение deadlock в главном цикле и корректное завершение застрявших потоков.
 * Внутреннее устройство (свои классы, семафоры, вспомогательные методы для Shared и SchedLock) придумываете сами. */
public final class Scheduler {
    /** Если true, каждая операция с общими данными добавляет строку в log. Выставляет runner. */
    boolean logging;
    /** Лог прогона: строки "<поток>  <операция>", например "A  lock mu", "A  balance -> 100". */
    final List<String> log = new ArrayList<>();
    /** null, если deadlock не было; иначе "held={a:A, b:B}" (этап 2). */
    String deadlock;

    private static final BooleanSupplier ALWAYS = () -> true;
    /** Поток планировщика, который сейчас исполняется в этом потоке ОС (null вне планировщика). */
    private static final ThreadLocal<Worker> CURRENT = new ThreadLocal<>();

    private final List<Worker> workers = new ArrayList<>();
    private final List<SchedLock> locks = new ArrayList<>();
    private final StringBuilder trace = new StringBuilder();
    /** Поток отдаёт ход планировщику: пауза или завершение. */
    private final Semaphore toScheduler = new Semaphore(0);
    /** Прогон прерван (deadlock или ошибка стратегии): проснувшиеся потоки раскручивают стек. */
    private volatile boolean aborted;
    private boolean started;

    /** Поток под управлением планировщика. */
    static final class Worker {
        final Scheduler scheduler;
        final int id;
        final char letter;
        final Runnable body;
        final Semaphore turn = new Semaphore(0);
        Thread thread;
        /** Не null, пока поток стоит на паузе: поток в enabled, только если guard истинно. */
        BooleanSupplier guard;
        /** С прошлой паузы поток трогал общее состояние: перед следующей операцией нужна пауза.
         *  Изначально true — старт доводит поток до паузы перед первой операцией. */
        boolean needPause = true;
        boolean done;
        Throwable error;

        Worker(Scheduler scheduler, int id, Runnable body) {
            this.scheduler = scheduler;
            this.id = id;
            this.letter = (char) ('A' + id);
            this.body = body;
        }
    }

    /** Бросается в застрявших потоках после прерывания прогона, чтобы раскрутить их стек. */
    private static final class Aborted extends RuntimeException {
        Aborted() { super("run aborted", null, false, false); }
    }

    public Scheduler() { }

    /** Зарегистрировать поток. Потоки получают буквы A, B, C… в порядке вызова spawn. */
    public void spawn(Runnable body) {
        if (started) throw new IllegalStateException("spawn after run");
        workers.add(new Worker(this, workers.size(), body));
    }

    /** Запустить потоки и вести их по одному: на каждой паузе спросить strategy.pick(enabled, last). */
    public void run(Strategy strategy) {
        if (started) throw new IllegalStateException("scheduler is single-use");
        started = true;
        try {
            // Старт: каждый поток по очереди доходит до паузы перед первой операцией (или до конца).
            for (Worker w : workers) {
                w.thread = new Thread(() -> threadMain(w), "sched-" + w.letter);
                w.thread.setDaemon(true);
                w.thread.start();
                toScheduler.acquireUninterruptibly();
            }
            int last = -1;
            while (true) {
                List<Integer> enabled = new ArrayList<>();
                boolean alive = false;
                for (Worker w : workers) {
                    if (w.done) continue;
                    alive = true;
                    if (w.guard.getAsBoolean()) enabled.add(w.id);
                }
                if (!alive) break;
                if (enabled.isEmpty()) {
                    deadlock = heldLocks();
                    break;
                }
                int next = strategy.pick(enabled, last);
                if (!enabled.contains(next)) throw new IllegalStateException("strategy picked disabled thread " + next);
                trace.append(workers.get(next).letter);
                last = next;
                workers.get(next).turn.release();
                toScheduler.acquireUninterruptibly();
            }
        } finally {
            abortAndJoin();
        }
        for (Worker w : workers) {
            if (w.error != null) throw new RuntimeException("thread " + w.letter + " failed", w.error);
        }
    }

    /** Трасса прогона: буквы потоков, выбранных во всех решениях подряд, например "AAAABB". */
    public String traceString() {
        return trace.toString();
    }

    /** window() из тестовых программ: пауза перед следующей операцией с общими данными обязательна. */
    public static void point() {
        Worker w = CURRENT.get();
        if (w == null) return;
        w.scheduler.log(w, "window");
        w.needPause = true;
    }

    /** Ждать, пока cond не станет true. До этого поток не попадает в enabled. */
    public static void await(BooleanSupplier cond) {
        Worker w = CURRENT.get();
        if (w == null) {
            while (!cond.getAsBoolean()) Thread.onSpinWait();
            return;
        }
        w.scheduler.beforeOp(w, cond);
        w.scheduler.log(w, "await done");
        w.needPause = true;
    }

    // ---------------- для Shared и SchedLock ----------------

    /** Текущий поток планировщика или null, если вызов идёт не из-под планировщика. */
    static Worker current() {
        return CURRENT.get();
    }

    /** Пауза перед операцией с общим состоянием (если она нужна) и ожидание, пока ready не станет true.
     *  После возврата операция выполняется сразу: остальные потоки стоят на семафорах. */
    void beforeOp(Worker w, BooleanSupplier ready) {
        if (w.needPause || !ready.getAsBoolean()) pause(w, ready);
        w.needPause = true;
    }

    void beforeOp(Worker w) {
        beforeOp(w, ALWAYS);
    }

    void log(Worker w, String op) {
        if (logging && !aborted) log.add(w.letter + "  " + op);
    }

    void register(SchedLock lock) {
        locks.add(lock);
    }

    boolean aborted() {
        return aborted;
    }

    // ---------------- внутреннее ----------------

    private void pause(Worker w, BooleanSupplier ready) {
        w.guard = ready;
        toScheduler.release();
        w.turn.acquireUninterruptibly();
        if (aborted) throw new Aborted();
        w.guard = null;
        w.needPause = false;
    }

    private void threadMain(Worker w) {
        CURRENT.set(w);
        try {
            w.body.run();
        } catch (Aborted ignored) {
            // прогон прерван: просто выходим
        } catch (Throwable t) {
            w.error = t;
        } finally {
            w.done = true;
            CURRENT.remove();
            toScheduler.release();   // завершение тоже передаёт ход планировщику
        }
    }

    private String heldLocks() {
        StringJoiner held = new StringJoiner(", ", "held={", "}");
        for (SchedLock l : locks) {
            if (l.owner != null) held.add(l.name + ":" + l.owner.letter);
        }
        return held.toString();
    }

    /** Разбудить застрявшие потоки (они бросят Aborted) и дождаться всех. */
    private void abortAndJoin() {
        boolean stuck = false;
        for (Worker w : workers) stuck |= w.thread != null && !w.done;
        if (stuck) {
            aborted = true;
            for (Worker w : workers) {
                if (w.thread != null && !w.done) w.turn.release();
            }
        }
        for (Worker w : workers) {
            if (w.thread == null) continue;
            try {
                w.thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }
}
