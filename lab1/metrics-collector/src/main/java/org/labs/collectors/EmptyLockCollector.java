package org.labs.collectors;

import org.labs.MetricsCollector;
import org.labs.Snapshot;

public class EmptyLockCollector implements MetricsCollector {

    private final Object lock = new Object();

    @Override
    public void record(long value) {
        synchronized (lock) {
            // внутри пусто
        }
    }

    @Override
    public Snapshot snapshot() {
        synchronized (lock) {
            return Snapshot.empty();
        }
    }

}
