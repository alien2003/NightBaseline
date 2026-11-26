package io.github.alien2003.nightbaseline.algo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlgoFingerprintTest {
    @Test
    fun sameParamsGiveTheSameFingerprint() {
        assertEquals(AlgoFingerprint.of(AlgoParams()), AlgoFingerprint.of(AlgoParams()))
    }

    @Test
    fun fingerprintCarriesTheAlgorithmVersion() {
        assertTrue(AlgoFingerprint.of(AlgoParams()).startsWith("v${AlgoFingerprint.ALGO_VERSION}-"))
    }

    @Test
    fun anyParameterChangesTheFingerprint() {
        val base = AlgoFingerprint.of(AlgoParams())
        assertNotEquals(base, AlgoFingerprint.of(AlgoParams(zThreshold = 3.0)))
        assertNotEquals(base, AlgoFingerprint.of(AlgoParams(windowNights = 21)))
        assertNotEquals(base, AlgoFingerprint.of(AlgoParams(metrics = DefaultMetrics.ALL.take(4))))
    }
}
