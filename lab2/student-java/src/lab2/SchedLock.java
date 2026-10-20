package lab2;

/* Этап 2: лок, о котором знает планировщик. Поток, ждущий занятый лок, не попадает в enabled;
 * обычный synchronized / ReentrantLock здесь нельзя — поток уснёт с ходом в руках и всё зависнет.
 * Лог: "lock <имя>", "unlock <имя>". Планировщик должен знать все локи, чтобы печатать held={a:A, b:B}. */
public final class SchedLock {
    final String name;
    private final Scheduler scheduler;
    /** Владелец лока; null — свободен. Меняется только потоком, у которого сейчас ход. */
    Scheduler.Worker owner;

    /** Создаётся в setup() тестовой программы; зарегистрируйте лок в планировщике s. */
    public SchedLock(Scheduler s, String name) {
        this.name = name;
        this.scheduler = s;
        s.register(this);
    }

    /** Пауза; если лок занят — ждать, не участвуя в выборе; затем стать владельцем. */
    public void lock() {
        Scheduler.Worker w = current();
        if (owner == w) throw new IllegalStateException("lock " + name + " is not reentrant");
        scheduler.beforeOp(w, () -> owner == null);
        owner = w;
        scheduler.log(w, "lock " + name);
    }

    /** Пауза; освободить лок, ждавшие потоки снова могут быть выбраны. */
    public void unlock() {
        if (scheduler.aborted()) return;   // прогон прерван: тихо выходим
        Scheduler.Worker w = current();
        if (owner != w) throw new IllegalStateException("unlock " + name + " by non-owner");
        scheduler.beforeOp(w);
        owner = null;
        scheduler.log(w, "unlock " + name);
    }

    private Scheduler.Worker current() {
        Scheduler.Worker w = Scheduler.current();
        if (w == null || w.scheduler != scheduler) throw new IllegalStateException("SchedLock " + name + " used outside its scheduler");
        return w;
    }
}
