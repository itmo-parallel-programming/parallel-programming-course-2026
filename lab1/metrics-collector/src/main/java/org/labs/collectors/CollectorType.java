package org.labs.collectors;

import org.labs.MetricsCollector;

import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public enum CollectorType {

    SINGLE_THREAD("single-thread", "Этап 0: однопоточный baseline", false, SingleThreadCollector::new),
    EMPTY_LOCK("empty-lock", "Этап 1: пустой лок", true, EmptyLockCollector::new),
    SYNCHRONIZED("synchronized", "Этап 1: единый общий лок", true, SynchronizedCollector::new),
    STRIPED_LOCK("striped-lock", "Этап 2: шардированный лок (16 групп)", true, StripedLockCollector::new),
    THREAD_LOCAL("thread-local", "Этап 3: thread-local состояние", true, ThreadLocalCollector::new),
    DOUBLE_BUFFER("double-buffer", "Этап 4: двойная буферизация", true, DoubleBufferCollector::new),
    DOUBLE_BUFFER_NO_RECHECK("double-buffer-no-recheck", "Этап 4 без шага 3", true, NoRecheckDoubleBufferCollector::new);

    private final String id;
    private final String title;
    private final boolean threadSafe;
    private final Supplier<MetricsCollector> factory;

    CollectorType(String id, String title, boolean threadSafe, Supplier<MetricsCollector> factory) {
        this.id = id;
        this.title = title;
        this.threadSafe = threadSafe;
        this.factory = factory;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public boolean threadSafe() {
        return threadSafe;
    }

    public MetricsCollector create() {
        return factory.get();
    }

    public static CollectorType byId(String id) {
        for (CollectorType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Неизвестный коллектор '" + id + "', доступны: " + ids());
    }

    public static String ids() {
        return Arrays.stream(values()).map(CollectorType::id).collect(Collectors.joining(", "));
    }
}
