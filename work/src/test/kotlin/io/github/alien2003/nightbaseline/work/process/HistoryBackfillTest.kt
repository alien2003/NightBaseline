package io.github.alien2003.nightbaseline.work.process

import io.github.alien2003.nightbaseline.algo.NightStatus
import io.github.alien2003.nightbaseline.data.hc.HcFeature
import io.github.alien2003.nightbaseline.data.hc.HcPermissions
import io.github.alien2003.nightbaseline.data.hc.fake.SyntheticSource
import io.github.alien2003.nightbaseline.work.process.Harness.Companion.HOUR
import io.github.alien2003.nightbaseline.work.process.Harness.Companion.endOf
import io.github.alien2003.nightbaseline.work.process.Harness.Companion.nights
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class HistoryBackfillTest {
    private val h = Harness()

    @Test
    fun storesEverySettledNightAsFinalWithoutNotifying() =
        runTest {
            val gen = nights(60, episodes = mapOf(40 to 2))
            SyntheticSource.load(h.gateway, gen)
            h.clock.nowMs = endOf(gen.last()) + 2 * HOUR

            val outcome = h.backfill.runIfNeeded() as BackfillOutcome.Done
            assertTrue(outcome.wholeHistory)
            // The newest night ended 2 h ago: left to the daily check.
            assertEquals(gen.dropLast(1).map { it.wakeDate }, h.nights.all.map { it.date })
            assertEquals(59, outcome.nights)
            assertTrue(h.nights.all.all { it.final && it.evaluationCount == 1 && it.notifiedAtMs == null })
            // History is evaluated night by night: the episode shows up, the first nights are learning.
            assertEquals(
                NightStatus.LEARNING,
                h.nights.all
                    .first()
                    .evaluation.status,
            )
            assertEquals(NightStatus.DEVIATIONS, checkNotNull(h.record(gen[40])).evaluation.status)
            assertTrue(h.notices.isEmpty())
            // Channel data is read in chunks, not per night.
            assertTrue(h.gateway.reads < 60, "reads: ${h.gateway.reads}")
        }

    @Test
    fun withoutTheHistoryPermissionOnlyTheLast30DaysAreRead() =
        runTest {
            val gen = nights(60)
            SyntheticSource.load(h.gateway, gen)
            h.clock.nowMs = endOf(gen.last()) + 2 * HOUR
            h.gateway.granted -= HcPermissions.READ_HEALTH_DATA_HISTORY

            val outcome = h.backfill.runIfNeeded() as BackfillOutcome.Done
            assertEquals(false, outcome.wholeHistory)
            val oldest =
                LocalDate.parse(
                    h.nights.all
                        .first()
                        .date,
                )
            val limit = Instant.ofEpochMilli(h.clock.nowMs - 31 * 24 * HOUR).atZone(h.zone).toLocalDate()
            assertTrue(!oldest.isBefore(limit), "oldest $oldest")
            assertTrue(outcome.nights in 25..30, "nights ${outcome.nights}")
        }

    @Test
    fun theHistoryFeatureIsNeededToo() =
        runTest {
            SyntheticSource.load(h.gateway, nights(5))
            h.gateway.features -= HcFeature.READ_HISTORY
            h.clock.nowMs = endOf(nights(5).last()) + 2 * HOUR
            assertEquals(false, (h.backfill.runIfNeeded() as BackfillOutcome.Done).wholeHistory)
        }

    @Test
    fun notNeededOnceNightsAreStored() =
        runTest {
            val gen = nights(10)
            SyntheticSource.load(h.gateway, gen)
            h.clock.nowMs = endOf(gen.last()) + 2 * HOUR
            h.backfill.runIfNeeded()
            val reads = h.gateway.reads
            assertEquals(BackfillOutcome.NotNeeded, h.backfill.runIfNeeded())
            assertEquals(reads, h.gateway.reads)
        }

    @Test
    fun blockedWithoutRequiredPermission() =
        runTest {
            SyntheticSource.load(h.gateway, nights(5))
            h.gateway.granted -= HcPermissions.READ_SLEEP
            h.clock.nowMs = endOf(nights(5).last()) + 2 * HOUR
            assertEquals(BackfillOutcome.Blocked(BlockReason.PERMISSION_MISSING), h.backfill.runIfNeeded())
            assertTrue(h.nights.all.isEmpty())
        }

    @Test
    fun afterTheBackfillTheNewestNightIsEvaluatedAndNotified() =
        runTest {
            val gen = nights(40, episodes = mapOf(39 to 1))
            SyntheticSource.load(h.gateway, gen)
            h.clock.nowMs = endOf(gen.last()) + 2 * HOUR

            h.backfill.runIfNeeded()
            val outcome = h.processor.process() as ProcessOutcome.Evaluated
            assertEquals(gen.last().wakeDate, outcome.date)
            assertEquals(NightStatus.DEVIATIONS, outcome.status)
            assertEquals(listOf(gen.last().wakeDate), h.notices.map { it.date })
        }
}
