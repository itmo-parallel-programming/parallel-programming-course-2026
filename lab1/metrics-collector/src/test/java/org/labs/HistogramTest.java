package org.labs;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HistogramTest {

    @ParameterizedTest
    @CsvSource({"0,0", "3,0", "4,1", "7,1", "8,2", "1019,254", "1020,255", "1023,255", "1024,255", "100000,255"})
    void bucketOfUsesFourMsWideBucketsCappedAt255(long value, int bucket) {
        assertEquals(bucket, Histogram.bucketOf(value));
    }

    @Test
    void percentileReturnsLowerBoundOfFirstBucketReachingThreshold() {
        long[] buckets = new long[Histogram.BUCKETS];
        buckets[0] = 50;
        buckets[10] = 49;
        buckets[200] = 1;

        assertEquals(0, Histogram.percentile(buckets, 100, 0.50));
        assertEquals(40, Histogram.percentile(buckets, 100, 0.99));
        assertEquals(800, Histogram.percentile(buckets, 100, 1.0));
    }

    @Test
    void percentileOfEmptyHistogramIsZero() {
        assertEquals(0, Histogram.percentile(new long[Histogram.BUCKETS], 0, 0.99));
    }

    @Test
    void snapshotRejectsWrongBucketCount() {
        assertThrows(IllegalArgumentException.class, () -> Snapshot.of(new long[10], 0, 0, 0, 0));
    }

    @Test
    void snapshotOfComputesPercentiles() {
        long[] buckets = new long[Histogram.BUCKETS];
        buckets[1] = 1;
        buckets[3] = 1;

        Snapshot snapshot = Snapshot.of(buckets, 2, 17, 5, 12);

        assertEquals(4, snapshot.p50());
        assertEquals(12, snapshot.p99());
        assertEquals(2, snapshot.bucketsSum());
        assertEquals(8.5, snapshot.mean());
    }
}
