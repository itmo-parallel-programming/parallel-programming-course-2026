package org.labs.benchmark;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import org.labs.collectors.CollectorType;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsistencyCheckTest {

    private static final long[] VALUES = Zipf.generate(1 << 20, 1023, 1.15, 42);

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

    @Test
    void configuredRunnerUsesRequestedSnapshotAndRepeatCounts() throws InterruptedException {
        PrintStream original = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            ConsistencyCheck.runConfigured(CollectorType.DOUBLE_BUFFER, VALUES, 2, 100, 2);
        } finally {
            System.setOut(original);
        }
        String[] lines = output.toString(StandardCharsets.UTF_8).strip().split("\\R");
        assertEquals(2, lines.length);
        for (String line : lines) {
            assertTrue(line.startsWith("double-buffer,100,0.00,0,0,"), line);
            assertTrue(line.endsWith(",0"), line);
        }
    }
}
