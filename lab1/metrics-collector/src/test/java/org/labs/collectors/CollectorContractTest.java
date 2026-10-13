package org.labs.collectors;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.labs.Histogram;
import org.labs.MetricsCollector;
import org.labs.Snapshot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Однопоточный контракт, общий для всех реализаций, которые действительно что-то записывают.
 */
class CollectorContractTest {

    @ParameterizedTest
    @EnumSource(value = CollectorType.class, names = "EMPTY_LOCK", mode = EnumSource.Mode.EXCLUDE)
    void emptyCollectorReturnsEmptySnapshot(CollectorType type) {
        assertEquals(Snapshot.empty(), type.create().snapshot());
    }

    @ParameterizedTest
    @EnumSource(value = CollectorType.class, names = "EMPTY_LOCK", mode = EnumSource.Mode.EXCLUDE)
    void snapshotMatchesRecordedValues(CollectorType type) {
        MetricsCollector collector = type.create();
        long[] values = {0, 3, 4, 5, 100, 1023, 5000, 2, 2, 2};
        for (long v : values) {
            collector.record(v);
        }

        Snapshot snapshot = collector.snapshot();

        long[] expectedBuckets = new long[Histogram.BUCKETS];
        expectedBuckets[0] = 5;   // 0, 3, 2, 2, 2
        expectedBuckets[1] = 2;   // 4, 5
        expectedBuckets[25] = 1;  // 100
        expectedBuckets[255] = 2; // 1023, 5000
        assertArrayEquals(expectedBuckets, snapshot.buckets());
        assertEquals(10, snapshot.count());
        assertEquals(6141, snapshot.sum());
        assertEquals(0, snapshot.min());
        assertEquals(5000, snapshot.max());
        assertEquals(0, snapshot.p50());
        assertEquals(1020, snapshot.p99());
    }

    @ParameterizedTest
    @EnumSource(value = CollectorType.class, names = "EMPTY_LOCK", mode = EnumSource.Mode.EXCLUDE)
    void snapshotIsDeepCopy(CollectorType type) {
        MetricsCollector collector = type.create();
        collector.record(10);

        Snapshot first = collector.snapshot();
        first.buckets()[2] = 999;
        Snapshot second = collector.snapshot();

        assertNotSame(first.buckets(), second.buckets());
        assertEquals(1, second.buckets()[2]);
    }

    @ParameterizedTest
    @EnumSource(value = CollectorType.class, names = "EMPTY_LOCK", mode = EnumSource.Mode.EXCLUDE)
    void snapshotsAccumulateAcrossCalls(CollectorType type) {
        MetricsCollector collector = type.create();
        collector.record(100);
        collector.snapshot();
        collector.snapshot();
        collector.snapshot();
        collector.record(200);
        collector.record(50);

        Snapshot snapshot = collector.snapshot();

        assertEquals(3, snapshot.count());
        assertEquals(350, snapshot.sum());
        assertEquals(50, snapshot.min(), "min не должен залипать в 0 после сброса буфера");
        assertEquals(200, snapshot.max());
        assertEquals(snapshot.count(), snapshot.bucketsSum());
    }
}
