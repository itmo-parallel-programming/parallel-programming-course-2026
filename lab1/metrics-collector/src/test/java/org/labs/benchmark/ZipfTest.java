package org.labs.benchmark;

import org.junit.jupiter.api.Test;
import org.labs.Histogram;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipfTest {

    private static final long[] VALUES = Zipf.generate();

    @Test
    void generatesTwoToTheTwentiethValuesInRange() {
        assertEquals(1 << 20, VALUES.length);
        for (long v : VALUES) {
            assertTrue(v >= 1 && v <= 1023, "значение вне диапазона: " + v);
        }
    }

    @Test
    void isDeterministicForFixedSeed() {
        assertArrayEquals(VALUES, Zipf.generate());
    }

    @Test
    void aboutAThirdFallsIntoBucketZero() {
        // Теоретически P(k <= 3) ≈ 0.354
        double share = fraction(v -> Histogram.bucketOf(v) == 0);
        assertEquals(0.354, share, 0.005);
    }

    @Test
    void hasLongTail() {
        // Около половины быстрые (<= 10 мс), около пятой части дольше 100 мс.
        assertEquals(0.525, fraction(v -> v <= 10), 0.005);
        assertEquals(0.200, fraction(v -> v > 100), 0.005);
    }

    private static double fraction(java.util.function.LongPredicate predicate) {
        long hits = 0;
        for (long v : VALUES) {
            if (predicate.test(v)) {
                hits++;
            }
        }
        return (double) hits / VALUES.length;
    }
}
