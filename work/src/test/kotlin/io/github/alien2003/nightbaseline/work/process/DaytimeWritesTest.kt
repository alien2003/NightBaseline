package io.github.alien2003.nightbaseline.work.process

import io.github.alien2003.nightbaseline.algo.ChannelId
import io.github.alien2003.nightbaseline.algo.Sample
import io.github.alien2003.nightbaseline.algo.SyntheticNights
import io.github.alien2003.nightbaseline.algo.WaitReason
import io.github.alien2003.nightbaseline.data.hc.HcChannels
import io.github.alien2003.nightbaseline.data.hc.HcPointRecord
import io.github.alien2003.nightbaseline.data.hc.HcSession
import io.github.alien2003.nightbaseline.data.hc.fake.SyntheticSource
import io.github.alien2003.nightbaseline.work.process.Harness.Companion.HOUR
import io.github.alien2003.nightbaseline.work.process.Harness.Companion.MIN
import io.github.alien2003.nightbaseline.work.process.Harness.Companion.endOf
import io.github.alien2003.nightbaseline.work.schedule.RunResult
import io.github.alien2003.nightbaseline.work.schedule.WorkPlanner
import io.github.alien2003.nightbaseline.work.schedule.WorkStep
import io.github.alien2003.nightbaseline.work.worker.NightRun
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Regression (release 0.1.0-dev.5): a complete night was not evaluated in the morning because the "source still
 * writing" check counted every record of the source written since the night started, including the day's step records
 * and all-day heart rate, so a check soon after any daytime sync always waited. Synthetic data (dates, zone and times
 * are made up) with the shape of that morning: the source re-saves the previous night in the early morning, a short
 * nap of the previous day is written with it, the new night is published about 10 min after its end, step records of
 * the source and of the phone keep arriving all morning, and "Check now" runs about two hours after the end.
 */
class DaytimeWritesTest {
    private val offset: ZoneOffset = ZoneOffset.ofHours(2)
    private val zone: ZoneId = offset
    private val h = Harness(zone)
    private val gen =
        SyntheticNights(
            SyntheticNights.Config(
                seed = 26,
                nights = 30,
                firstWakeDate = LocalDate.of(2026, 5, 4),
                offset = offset,
                missingNightP = 0.0,
                shortNightP = 0.0,
                napP = 0.0,
                splitP = 0.0,
            ),
        ).generate()
    private val previous = gen[gen.size - 2]
    private val last = gen.last()
    private val end = endOf(last)

    /** "Check now", two hours after the night's end. */
    private val check = end + 2 * HOUR

    private fun local(time: String): Long =
        LocalDateTime
            .parse(time)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    /** The history up to the previous night, evaluated on its own morning as the app did, then the next morning. */
    private suspend fun morningAfterTheNewNight() {
        SyntheticSource.load(h.gateway, gen.dropLast(1))
        h.clock.nowMs = endOf(previous) + 3 * HOUR
        assertEquals(previous.wakeDate, (h.processor.process() as ProcessOutcome.Evaluated).date)

        // Early morning: the source re-saves the previous day, with a short nap of that afternoon.
        val resave = local("2026-06-02T07:40")
        SyntheticSource.rewriteNight(h.gateway, previous, previous, rewrittenAtMs = resave)
        h.gateway.sessions +=
            HcSession("nap", SyntheticSource.ORIGIN, local("2026-06-01T14:10"), local("2026-06-01T14:25"), offset.totalSeconds, resave)

        // The new night is published about 10 min after its end, the person is up 5 min after it.
        SyntheticSource.loadNight(h.gateway, last, writeDelayMs = 10 * MIN, stepsAfterEndMs = 5 * MIN)

        // The rest of the morning: the source's steps and all-day heart rate, and the phone's own step counter, are
        // written every few minutes, the newest one a minute before the check.
        var t = end + HOUR + 15 * MIN
        while (t < check) {
            val written = minOf(t + MIN, check - MIN)
            h.gateway.add(HcChannels.STEPS_ID, SyntheticSource.ORIGIN, HcPointRecord(t, written, listOf(Sample(t, STEPS_PER_MIN))))
            h.gateway.add(ChannelId.HEART_RATE, SyntheticSource.ORIGIN, HcPointRecord(t, written, listOf(Sample(t, DAY_HR))))
            h.gateway.add(HcChannels.STEPS_ID, PHONE, HcPointRecord(t, written, listOf(Sample(t, STEPS_PER_MIN))))
            t += 5 * MIN
        }
    }

    @Test
    fun checkNowEvaluatesTheCompleteNightDespiteDaytimeWrites() =
        runTest {
            morningAfterTheNewNight()
            h.clock.nowMs = check
            h.diagnostics.clear()

            // "Check now" runs the same NightRun as the daily work.
            val result = NightRun(h.backfill, h.processor, h.runLog, h.problemNotifier, h.settings).run()
            val outcome = (result as RunResult.Outcome).outcome as ProcessOutcome.Evaluated
            assertEquals("2026-06-02", outcome.date)
            assertEquals(last.wakeDate, outcome.date)
            // The previous night, re-saved by the source, gets its one re-evaluation in the same run.
            assertEquals(listOf(previous.wakeDate), outcome.reevaluated)
            assertTrue(checkNotNull(h.record(previous)).final)
            assertFalse(checkNotNull(h.record(last)).final)

            // Home's newest stored night is now the expected wake date; the chain continues at the next daily time.
            assertEquals(last.wakeDate, h.nights.all.maxOf { it.date })
            val nextDaily = local("2026-06-03T08:00")
            assertEquals(WorkStep.RunAt(nextDaily), WorkPlanner.plan(result, attempt = 0, nextDailyMs = nextDaily))

            assertTrue(h.diagnostics.any { it.startsWith("night 2026-06-02 end ") && it.endsWith("-> COMPLETE") }, h.diagnostics.toString())
            assertTrue("night 2026-06-02 evaluated and stored" in h.diagnostics, h.diagnostics.toString())
        }

    @Test
    fun aLateUploadOfTheNightsOwnSamplesStillWaitsForQuiet() =
        runTest {
            morningAfterTheNewNight()
            // A respiratory-rate sample inside the night, written a minute before the check (a late upload).
            val inNight = end - 20 * MIN
            h.gateway.add(
                ChannelId.RESPIRATORY_RATE,
                SyntheticSource.ORIGIN,
                HcPointRecord(inNight, check - MIN, listOf(Sample(inNight, 14.0))),
            )
            h.clock.nowMs = check

            val wait = h.processor.process() as ProcessOutcome.Retry
            assertEquals(last.wakeDate, wait.date)
            assertEquals(listOf(WaitReason.SOURCE_STILL_WRITING), wait.reasons)
            assertTrue(h.diagnostics.any { it.endsWith("-> WAIT (SOURCE_STILL_WRITING)") }, h.diagnostics.toString())

            h.clock.nowMs = wait.atMs
            assertEquals(last.wakeDate, (h.processor.process() as ProcessOutcome.Evaluated).date)
        }

    @Test
    fun diagnosticsCarryOnlyDatesTimesAndReasons() =
        runTest {
            morningAfterTheNewNight()
            h.clock.nowMs = end + 30 * MIN
            h.processor.process()
            h.clock.nowMs = check
            h.processor.process()

            assertTrue(h.diagnostics.isNotEmpty())
            h.diagnostics.forEach { line ->
                val rest =
                    line
                        .replace(Regex("""\d{4}-\d{2}-\d{2}"""), "")
                        .replace(Regex("""\b\d{2}:\d{2}\b"""), "")
                        .replace(ChannelId.SPO2.key, "")
                assertFalse(rest.any { it.isDigit() }, "a number other than a date or a time: $line")
            }
        }

    private companion object {
        const val PHONE = "com.android.healthconnect.phone.synthetic"
        const val STEPS_PER_MIN = 20.0
        const val DAY_HR = 80.0
    }
}
