package org.labs.collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CollectorTypeTest {

    @ParameterizedTest
    @EnumSource(CollectorType.class)
    void byIdRoundTrips(CollectorType type) {
        assertEquals(type, CollectorType.byId(type.id()));
    }

    @ParameterizedTest
    @EnumSource(CollectorType.class)
    void createReturnsFreshInstance(CollectorType type) {
        assertNotSame(type.create(), type.create());
    }

    @Test
    void unknownIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> CollectorType.byId("nope"));
    }
}
