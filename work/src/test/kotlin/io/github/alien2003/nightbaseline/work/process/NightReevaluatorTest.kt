package io.github.alien2003.nightbaseline.work.process

import io.github.alien2003.nightbaseline.algo.AlgoFingerprint
import io.github.alien2003.nightbaseline.algo.AlgoParams
import io.github.alien2003.nightbaseline.algo.Evaluator
import io.github.alien2003.nightbaseline.algo.NightStatus
import io.github.alien2003.nightbaseline.data.hc.HcAvailability
import io.github.alien2003.nightbaseline.data.hc.fake.SyntheticSource
import io.github.alien2003.nightbaseline.data.local.NightRecord
import io.github.alien2003.nightbaseline.data.local.RunLogKind
import io.github.alien2003.nightbaseline.data.local.settings.AppSettings
import io.github.alien2003.nightbaseline.data.local.settings.SensitivityLevel
import io.github.alien2003.nightbaseline.work.process.Harness.Companion.HOUR
import io.github.alien2003.nightbaseline.work.process.Harness.Companion.endOf
import io.github.alien2003.nightbaseline.work.process.Harness.Companion.nights
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NightReevaluatorTest {
    private val h = Harness()

    /** What the current algorithm says about every stored night with [params]. */
    private fun expected(params: AlgoParams): List<NightRecord> {
        val history = h.nights.all.map { it.summary }
        return h.nights.all.map { it.copy(evaluation = Evaluator.evaluate(it.summary, history, params)) }
    }

    @Test
    fun everyStoredNightCarriesTheCurrentFingerprint() =
        runTest {
            val gen = nights(20)
            SyntheticSource.load(h.gateway, gen)
            h.clock.nowMs = endOf(gen.last()) + 2 * HOUR
            h.processor.process()

            val current = AlgoFingerprint.of(AppSettings().algoParams())
            assertEquals(gen.size, h.nights.all.size)
            assertTrue(h.nights.all.all { it.algoFingerprint == current })
        }

    @Test
    fun fingerprintChangeReevaluatesStoredNightsFromTheirSummariesWithoutNotifying() =
        runTest {
            val gen = nights(40, episodes = mapOf(39 to 1))
            SyntheticSource.load(h.gateway, gen)
            h.clock.nowMs = endOf(gen.last()) + 2 * HOUR
            val first = h.processor.process() as ProcessOutcome.Evaluated
            assertEquals(NightStatus.DEVIATIONS, first.status)
            assertEquals(1, h.notices.size)
            val notifiedAt = checkNotNull(h.record(gen.last())).notifiedAtMs

            // Rows of an older app version: another algorithm's evaluations, an older (or no) fingerprint.
            val stale = AlgoParams(zThreshold = 4.5)
            val history = h.nights.all.map { it.summary }
            h.nights.all.forEachIndexed { i, r ->
                val old = Evaluator.evaluate(r.summary, history, stale)
                h.nights.upsert(r.copy(evaluation = old, algoFingerprint = if (i % 2 == 0) null else "v1-0000000000000000"))
            }
            assertNotEquals(expected(AppSettings().algoParams()), h.nights.all)
            // Health Connect is not needed for the re-evaluation: only stored summaries are used.
            h.gateway.status = HcAvailability.NOT_SUPPORTED
            val reads = h.gateway.reads
            h.clock.nowMs += HOUR
            h.notices.clear()

            assertTrue(h.processor.process() is ProcessOutcome.Blocked)
            assertEquals(reads, h.gateway.reads)

            val params = AppSettings().algoParams()
            val current = AlgoFingerprint.of(params)
            assertEquals(expected(params).map { it.evaluation }, h.nights.all.map { it.evaluation })
            assertTrue(h.nights.all.all { it.algoFingerprint == current })
            assertTrue(h.notices.isEmpty(), "recomputed nights are never notified")
            assertEquals(notifiedAt, checkNotNull(h.record(gen.last())).notifiedAtMs)
            assertEquals(1, h.runLog.lines.count { it.kind == RunLogKind.RECOMPUTED })
            assertTrue(h.runLog.lines.none { it.kind == RunLogKind.NOTIFIED && it.timeMs == h.clock.nowMs })
        }

    @Test
    fun sensitivityChangeReevaluatesWithTheNewParametersWithoutNotifying() =
        runTest {
            val gen = nights(40, episodes = mapOf(25 to 2))
            SyntheticSource.load(h.gateway, gen)
            h.clock.nowMs = endOf(gen.last()) + 2 * HOUR
            h.processor.process()
            val before = h.nights.all
            val noticesBefore = h.notices.size

            h.settings.setSensitivity(SensitivityLevel.HIGH)
            val written = h.reevaluator.reevaluateIfNeeded()

            val params = AppSettings(sensitivity = SensitivityLevel.HIGH).algoParams()
            assertEquals(gen.size, written)
            assertEquals(expected(params).map { it.evaluation }, h.nights.all.map { it.evaluation })
            assertNotEquals(before.map { it.evaluation }, h.nights.all.map { it.evaluation })
            assertTrue(h.nights.all.all { it.algoFingerprint == AlgoFingerprint.of(params) })
            // Settled state and notification history are kept.
            assertEquals(before.map { it.final }, h.nights.all.map { it.final })
            assertEquals(before.map { it.evaluationCount }, h.nights.all.map { it.evaluationCount })
            assertEquals(before.map { it.notifiedAtMs }, h.nights.all.map { it.notifiedAtMs })
            assertEquals(noticesBefore, h.notices.size)

            // The processor sees the same parameters: nothing left to do.
            h.clock.nowMs += HOUR
            h.processor.process()
            assertEquals(1, h.runLog.lines.count { it.kind == RunLogKind.RECOMPUTED })
            assertEquals(noticesBefore, h.notices.size)
        }

    @Test
    fun currentNightsAreNotRewritten() =
        runTest {
            val gen = nights(15)
            SyntheticSource.load(h.gateway, gen)
            h.clock.nowMs = endOf(gen.last()) + 2 * HOUR
            h.processor.process()
            val upserts = h.nights.upserts.size

            assertEquals(0, h.reevaluator.reevaluateIfNeeded())
            assertEquals(upserts, h.nights.upserts.size)
            assertTrue(h.runLog.lines.none { it.kind == RunLogKind.RECOMPUTED })
        }
}
