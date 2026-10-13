package org.labs.benchmark;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.labs.collectors.CollectorType;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsistencyCheckTest {

    private static final long[] VALUES = Zipf.generate();

    @ParameterizedTest
    @EnumSource(value = CollectorType.class, names = {"SYNCHRONIZED", "DOUBLE_BUFFER"})
    void consistentCollectorsNeverProduceTornSnapshots(CollectorType type) throws InterruptedException {
        ConsistencyCheck.Report report = ConsistencyCheck.run(type.create(), VALUES, 4, 2_000);

        assertEquals(0, report.broken(), report.toString());
        assertEquals(0, report.finalDiff(), report.toString());
    }

    @ParameterizedTest
    @EnumSource(value = CollectorType.class, names = {"STRIPED_LOCK", "THREAD_LOCAL"})
    void tornCollectorsStillCountEveryCallAfterJoin(CollectorType type) throws InterruptedException {
        ConsistencyCheck.Report report = ConsistencyCheck.run(type.create(), VALUES, 4, 2_000);

        assertEquals(report.broken(), report.bucketsLess() + report.bucketsGreater());
        assertEquals(0, report.finalDiff(), report.toString());
        assertEquals(report.finalCount(), report.finalBucketsSum());
    }
}
