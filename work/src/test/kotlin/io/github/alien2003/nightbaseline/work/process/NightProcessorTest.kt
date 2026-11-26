package io.github.alien2003.nightbaseline.work.process

import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.algo.MissingReason
import io.github.alien2003.nightbaseline.algo.NightStatus
import io.github.alien2003.nightbaseline.algo.Sample
import io.github.alien2003.nightbaseline.algo.WaitReason
import io.github.alien2003.nightbaseline.data.hc.HcAvailability
import io.github.alien2003.nightbaseline.data.hc.HcChannels
import io.github.alien2003.nightbaseline.data.hc.HcError
import io.github.alien2003.nightbaseline.data.hc.HcPermissions
import io.github.alien2003.nightbaseline.data.hc.HcPointRecord
import io.github.alien2003.nightbaseline.data.hc.fake.SyntheticSource
import io.github.alien2003.nightbaseline.data.local.RunLogKind
import io.github.alien2003.nightbaseline.work.notify.DataProblem
import io.github.alien2003.nightbaseline.work.process.Harness.Companion.HOUR
import io.github.alien2003.nightbaseline.work.process.Harness.Companion.MIN
import io.github.alien2003.nightbaseline.work.process.Harness.Companion.endOf
import io.github.alien2003.nightbaseline.work.process.Harness.Companion.nights
import io.github.alien2003.nightbaseline.work.schedule.RunResult
import io.github.alien2003.nightbaseline.work.worker.NightRun
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class NightProcessorTest {
    private val h = Harness()

    @Test
    fun learningPeriodEvaluatesWithoutNotifying() =
        runTest {
            val gen = nights(5)
            SyntheticSource.load(h.gateway, gen)
            h.clock.nowMs = endOf(gen.last()) + 2 * HOUR

            val outcome = h.processor.process() as ProcessOutcome.Evaluated
            assertEquals(gen.last().wakeDate, outcome.date)
            assertEquals(NightStatus.LEARNING, outcome.status)
            assertFalse(outcome.notified)
            assertTrue(h.notices.isEmpty())
            // Older nights were filled in as final history; the newest awaits its one re-evaluation.
            assertEquals(gen.map { it.wakeDate }, h.nights.all.map { it.date })
            assertEquals(listOf(true, true, true, true, false), h.nights.all.map { it.final })
            assertEquals(1, checkNotNull(h.record(gen.last())).evaluationCount)
        }

    @Test
    fun waitsUntilThePersonIsUpThenEvaluates() =
        runTest {
            val gen = nights(30)
            SyntheticSource.load(h.gateway, gen.dropLast(1))
            SyntheticSource.loadNight(h.gateway, gen.last(), stepsAfterEndMs = null)
            val end = endOf(gen.last())
            h.clock.nowMs = end + 2 * HOUR

            val wait = h.processor.process() as ProcessOutcome.Retry
            assertEquals(gen.last().wakeDate, wait.date)
            assertEquals(listOf(WaitReason.NOT_UP_YET), wait.reasons)
            assertTrue(wait.atMs >= h.clock.nowMs + NightProcessor.MIN_RETRY_MS)
            assertTrue(wait.atMs <= h.clock.nowMs + NightProcessor.WAIT_STEP_MS)
            assertNull(h.record(gen.last()))
            assertTrue(h.runLog.lines.any { it.kind == RunLogKind.WAITING })

            // The person gets up: a brisk step record after the end.
            val steps = end + 3 * HOUR
            h.gateway.add(HcChannels.STEPS_ID, SyntheticSource.ORIGIN, HcPointRecord(steps, steps + MIN, listOf(Sample(steps, 40.0))))
            h.clock.nowMs = steps + HOUR
            val done = h.processor.process() as ProcessOutcome.Evaluated
            assertEquals(gen.last().wakeDate, done.date)
        }

    @Test
    fun theCapEndsTheWaitAndRetriesNeverGoPastIt() =
        runTest {
            val gen = nights(30)
            SyntheticSource.load(h.gateway, gen.dropLast(1))
            SyntheticSource.loadNight(h.gateway, gen.last(), stepsAfterEndMs = null)
            val end = endOf(gen.last())
            val cap = end + 12 * HOUR

            h.clock.nowMs = cap - 20 * MIN
            val wait = h.processor.process() as ProcessOutcome.Retry
            assertEquals(cap, wait.atMs)
            h.clock.nowMs = cap - 5 * MIN
            // Never sooner than 15 minutes, even right before the cap.
            assertEquals(h.clock.nowMs + NightProcessor.MIN_RETRY_MS, (h.processor.process() as ProcessOutcome.Retry).atMs)

            h.clock.nowMs = cap
            assertTrue(h.processor.process() is ProcessOutcome.Evaluated)
        }

    @Test
    fun deviationsNotifyOnce() =
        runTest {
            val gen = nights(40, episodes = mapOf(39 to 1))
            SyntheticSource.load(h.gateway, gen)
            h.clock.nowMs = endOf(gen.last()) + 2 * HOUR

            val first = h.processor.process() as ProcessOutcome.Evaluated
            assertEquals(NightStatus.DEVIATIONS, first.status)
            assertTrue(first.notified)
            assertEquals(1, h.notices.size)
            val notice = h.notices.single()
            assertEquals(gen.last().wakeDate, notice.date)
            assertTrue(MetricId.SLEEPING_HR in notice.metrics)
            val stored = checkNotNull(h.record(gen.last()))
            assertEquals(NightStatus.DEVIATIONS, stored.notifiedStatus)
            assertEquals(h.clock.nowMs, stored.notifiedAtMs)

            // The same night is not evaluated or notified again.
            h.clock.nowMs += HOUR
            assertTrue(h.processor.process() is ProcessOutcome.NothingNew)
            assertEquals(1, h.notices.size)
        }

    @Test
    fun nightsReadWithoutAccessAreReadAgainOnceAccessIsBack() =
        runTest {
            val gen = nights(10)
            SyntheticSource.load(h.gateway, gen)
            h.gateway.granted -= HcPermissions.READ_OXYGEN_SATURATION
            h.clock.nowMs = endOf(gen.last()) + 2 * HOUR
            assertTrue(h.processor.process() is ProcessOutcome.Evaluated)
            // Without access the gap is "no access", never "the source doesn't write it".
            assertTrue(h.nights.all.all { it.summary.values[MetricId.SPO2]?.missing == MissingReason.NO_ACCESS })

            h.gateway.granted += HcPermissions.READ_OXYGEN_SATURATION
            h.clock.nowMs += HOUR
            h.processor.process()
            val older = h.nights.all.filter { it.date < gen.last().wakeDate }
            assertTrue(older.isNotEmpty())
            assertTrue(
                older.none {
                    it.summary.values[MetricId.SPO2]?.missing == MissingReason.NO_ACCESS
                },
                "${older.map { it.summary.values[MetricId.SPO2] }}",
            )
            assertTrue(older.any { it.summary.values[MetricId.SPO2]?.value != null })
        }

    @Test
    fun noNotificationWhenDisabledOrBlockedBySystem() =
        runTest {
            val gen = nights(40, episodes = mapOf(39 to 1))
            SyntheticSource.load(h.gateway, gen)
            h.clock.nowMs = endOf(gen.last()) + 2 * HOUR
            h.settings.setNotificationsEnabled(false)

            val outcome = h.processor.process() as ProcessOutcome.Evaluated
            assertEquals(NightStatus.DEVIATIONS, outcome.status)
            assertFalse(outcome.notified)
            assertTrue(h.notices.isEmpty())
            assertNull(checkNotNull(h.record(gen.last())).notifiedAtMs)
        }

    @Test
    fun reevaluationAfterARewriteNotifiesWhenTheStatusChangesIntoDeviations() =
        runTest {
            val plain = nights(32)
            val withEpisode = nights(32, episodes = mapOf(30 to 1))
            SyntheticSource.load(h.gateway, plain.take(31))
            h.clock.nowMs = endOf(plain[30]) + 2 * HOUR

            val first = h.processor.process() as ProcessOutcome.Evaluated
            assertNotEquals(NightStatus.DEVIATIONS, first.status)
            assertTrue(h.notices.isEmpty())

            // Next morning the source rewrites night 30 (now showing the episode) and publishes night 31.
            SyntheticSource.rewriteNight(h.gateway, plain[30], withEpisode[30], rewrittenAtMs = endOf(plain[31]) + 15 * MIN)
            SyntheticSource.loadNight(h.gateway, plain[31])
            h.clock.nowMs = endOf(plain[31]) + 2 * HOUR

            val second = h.processor.process() as ProcessOutcome.Evaluated
            assertEquals(plain[31].wakeDate, second.date)
            assertEquals(listOf(plain[30].wakeDate), second.reevaluated)
            val re = checkNotNull(h.record(plain[30]))
            assertEquals(NightStatus.DEVIATIONS, re.evaluation.status)
            assertTrue(re.final)
            assertEquals(2, re.evaluationCount)
            assertEquals(listOf(plain[30].wakeDate), h.notices.map { it.date })

            // Final: no further re-evaluation, no further notification.
            h.clock.nowMs += 3 * 24 * HOUR
            h.processor.process()
            assertEquals(2, checkNotNull(h.record(plain[30])).evaluationCount)
            assertEquals(1, h.notices.size)
        }

    @Test
    fun reevaluationThatStaysInDeviationsDoesNotNotifyAgain() =
        runTest {
            val gen = nights(32, episodes = mapOf(30 to 1))
            SyntheticSource.load(h.gateway, gen.take(31))
            h.clock.nowMs = endOf(gen[30]) + 2 * HOUR
            assertTrue((h.processor.process() as ProcessOutcome.Evaluated).notified)

            SyntheticSource.loadNight(h.gateway, gen[31])
            h.clock.nowMs = endOf(gen[31]) + 2 * HOUR
            val second = h.processor.process() as ProcessOutcome.Evaluated
            assertEquals(listOf(gen[30].wakeDate), second.reevaluated)
            assertEquals(NightStatus.DEVIATIONS, checkNotNull(h.record(gen[30])).evaluation.status)
            assertEquals(listOf(gen[30].wakeDate), h.notices.map { it.date })
        }

    @Test
    fun aPendingNightIsReevaluatedAtTheLatest36HoursAfterItsEnd() =
        runTest {
            val gen = nights(10)
            SyntheticSource.load(h.gateway, gen)
            h.clock.nowMs = endOf(gen.last()) + 2 * HOUR
            h.processor.process()
            assertFalse(checkNotNull(h.record(gen.last())).final)

            // No newer night (watch not worn): the fallback re-evaluates it.
            h.clock.nowMs = endOf(gen.last()) + NightProcessor.REEVALUATE_AFTER_MS
            val outcome = h.processor.process() as ProcessOutcome.NothingNew
            assertEquals(listOf(gen.last().wakeDate), outcome.reevaluated)
            assertTrue(checkNotNull(h.record(gen.last())).final)
        }

    @Test
    fun waitsHourlyInTheMorningWhileNoNewNightIsVisible() =
        runTest {
            val gen = nights(10)
            SyntheticSource.load(h.gateway, gen)
            h.clock.nowMs = endOf(gen.last()) + 2 * HOUR
            h.processor.process()

            // Next day 09:00 local (UTC+3): the new night is not uploaded yet.
            val nextDay =
                LocalDate
                    .parse(gen.last().wakeDate)
                    .plusDays(1)
                    .atTime(9, 0)
                    .atZone(h.zone)
            h.clock.nowMs = nextDay.toInstant().toEpochMilli()
            val wait = h.processor.process() as ProcessOutcome.Retry
            assertNull(wait.date)
            assertEquals(h.clock.nowMs + NightProcessor.NO_NIGHT_RETRY_MS, wait.atMs)
            // After 14:00 the day is given up.
            h.clock.nowMs = nextDay.withHour(15).toInstant().toEpochMilli()
            assertTrue(h.processor.process() is ProcessOutcome.NothingNew)
        }

    @Test
    fun missingPermissionBlocksWithoutStoringAnything() =
        runTest {
            val gen = nights(10)
            SyntheticSource.load(h.gateway, gen)
            h.clock.nowMs = endOf(gen.last()) + 2 * HOUR
            h.gateway.granted -= HcPermissions.READ_HEART_RATE

            assertEquals(ProcessOutcome.Blocked(BlockReason.PERMISSION_MISSING), h.processor.process())
            assertTrue(h.nights.all.isEmpty())
            assertTrue(h.notices.isEmpty())
            assertTrue(h.runLog.lines.any { it.kind == RunLogKind.BLOCKED })
        }

    @Test
    fun backgroundWithoutTheBackgroundPermissionIsBlocked() =
        runTest {
            val gen = nights(10)
            SyntheticSource.load(h.gateway, gen)
            h.clock.nowMs = endOf(gen.last()) + 2 * HOUR
            h.gateway.granted -= HcPermissions.READ_HEALTH_DATA_IN_BACKGROUND

            assertEquals(ProcessOutcome.Blocked(BlockReason.BACKGROUND_PERMISSION_MISSING), h.processor.process())
            h.inForeground = true
            assertTrue(h.processor.process() is ProcessOutcome.Evaluated)
        }

    @Test
    fun unavailableHealthConnectIsBlocked() =
        runTest {
            h.gateway.status = HcAvailability.UPDATE_REQUIRED
            assertEquals(ProcessOutcome.Blocked(BlockReason.HEALTH_CONNECT_UNAVAILABLE), h.processor.process())
        }

    @Test
    fun rateLimitsAndUnexpectedErrorsBecomeFailuresOfTheRun() =
        runTest {
            val gen = nights(10)
            SyntheticSource.load(h.gateway, gen)
            h.clock.nowMs = endOf(gen.last()) + 2 * HOUR
            val run = NightRun(h.backfill, h.processor, h.runLog, h.problemNotifier, h.settings)

            h.gateway.failures += HcError.RateLimited("read")
            assertEquals(RunResult.Failure(transient = true), run.run())
            h.gateway.failures += HcError.Other("read", "IllegalArgumentException", retryable = false)
            assertEquals(RunResult.Failure(transient = false), run.run())
            assertTrue(h.runLog.lines.count { it.kind == RunLogKind.ERROR } == 2)
            // Log lines carry no measurement values.
            assertTrue(h.runLog.lines.none { line -> Regex("\\d+\\.\\d").containsMatchIn(line.message) })

            val ok = run.run() as RunResult.Outcome
            assertTrue(ok.outcome is ProcessOutcome.Evaluated)
        }

    @Test
    fun blockedRunsShowTheirProblemAndGoodRunsClearIt() =
        runTest {
            val gen = nights(10)
            SyntheticSource.load(h.gateway, gen)
            h.clock.nowMs = endOf(gen.last()) + 2 * HOUR
            val run = NightRun(h.backfill, h.processor, h.runLog, h.problemNotifier, h.settings)

            h.gateway.granted -= HcPermissions.READ_HEALTH_DATA_IN_BACKGROUND
            run.run()
            h.gateway.granted += HcPermissions.READ_HEALTH_DATA_IN_BACKGROUND
            h.gateway.granted -= HcPermissions.READ_SLEEP
            run.run()
            h.gateway.granted += HcPermissions.READ_SLEEP
            run.run()
            assertEquals(listOf(DataProblem.BACKGROUND, DataProblem.PERMISSION, null), h.problems)
        }

    @Test
    fun lateSyncOfAnOlderNightIsNotNotified() =
        runTest {
            val gen = nights(40, episodes = mapOf(39 to 1))
            SyntheticSource.load(h.gateway, gen)
            // The night is first seen three days after it ended.
            h.clock.nowMs = endOf(gen.last()) + 3 * 24 * HOUR
            h.processor.process()
            assertEquals(NightStatus.DEVIATIONS, checkNotNull(h.record(gen.last())).evaluation.status)
            assertTrue(h.notices.isEmpty())
        }
}
