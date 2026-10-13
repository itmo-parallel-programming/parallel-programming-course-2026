package org.labs;

public interface MetricsCollector {

    void record(long value);

    Snapshot snapshot();

}
