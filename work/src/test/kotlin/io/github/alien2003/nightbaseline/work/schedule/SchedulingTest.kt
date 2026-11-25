package io.github.alien2003.nightbaseline.work.schedule

import io.github.alien2003.nightbaseline.algo.NightStatus
import io.github.alien2003.nightbaseline.work.notify.NightIntents
import io.github.alien2003.nightbaseline.work.process.BlockReason
import io.github.alien2003.nightbaseline.work.process.ProcessOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class SchedulingTest {
    private val berlin = ZoneId.of("Europe/Berlin")

    private fun at(text: String): Long = ZonedDateTime.parse(text).toInstant().toEpochMilli()

    @Test
    fun nextRunIsTheNextMorningTime() {
        assertEquals(
            at("2026-06-10T08:00+02:00[Europe/Berlin]"),
            DailySchedule.nextRunMs(at("2026-06-10T07:59+02:00[Europe/Berlin]"), 480, berlin),
        )
        assertEquals(
            at("2026-06-11T08:00+02:00[Europe/Berlin]"),
            DailySchedule.nextRunMs(at("2026-06-10T08:00+02:00[Europe/Berlin]"), 480, berlin),
        )
        // Out-of-range minutes are clamped to the day.
        assertEquals(
            at("2026-06-11T00:00+02:00[Europe/Berlin]"),
            DailySchedule.nextRunMs(at("2026-06-10T08:00+02:00[Europe/Berlin]"), -5, berlin),
        )
    }

    @Test
    fun theLocalTimeSurvivesDstChanges() {
        // Spring forward (2026-03-29): the day has 23 hours, the run stays at 08:00 local.
        val beforeSpring = at("2026-03-28T09:00+01:00[Europe/Berlin]")
        val spring = DailySchedule.nextRunMs(beforeSpring, 480, berlin)
        assertEquals(at("2026-03-29T08:00+02:00[Europe/Berlin]"), spring)
        // 09:00 to 09:00 across the change is 23 hours of real time.
        assertEquals(Duration.ofHours(23), Duration.ofMillis(DailySchedule.nextRunMs(beforeSpring, 9 * 60, berlin) - beforeSpring))
        // Fall back (2026-10-25): 25 hours.
        val beforeFall = at("2026-10-24T09:00+02:00[Europe/Berlin]")
        assertEquals(Duration.ofHours(25), Duration.ofMillis(DailySchedule.nextRunMs(beforeFall, 9 * 60, berlin) - beforeFall))
        // A time inside the spring-forward gap moves to the first valid instant.
        assertEquals(at("2026-03-29T03:30+02:00[Europe/Berlin]"), DailySchedule.nextRunMs(beforeSpring, 150, berlin))
    }

    @Test
    fun outcomesMapToTheNextStep() {
        val daily = 1_000_000L
        assertEquals(WorkStep.RunAt(5_000L), WorkPlanner.plan(RunResult.Outcome(ProcessOutcome.Retry(5_000L, emptyList(), "d")), 0, daily))
        assertEquals(
            WorkStep.RunAt(daily),
            WorkPlanner.plan(RunResult.Outcome(ProcessOutcome.Retry(daily + 1, emptyList(), null)), 0, daily),
        )
        assertEquals(
            WorkStep.RunAt(daily),
            WorkPlanner.plan(RunResult.Outcome(ProcessOutcome.Evaluated("d", NightStatus.ALL_WITHIN, false)), 0, daily),
        )
        assertEquals(WorkStep.RunAt(daily), WorkPlanner.plan(RunResult.Outcome(ProcessOutcome.NothingNew()), 0, daily))
        assertEquals(
            WorkStep.RunAt(daily),
            WorkPlanner.plan(RunResult.Outcome(ProcessOutcome.Blocked(BlockReason.PERMISSION_MISSING)), 0, daily),
        )
        assertEquals(WorkStep.Backoff, WorkPlanner.plan(RunResult.Failure(transient = true), WorkPlanner.MAX_TRANSIENT_RETRIES - 1, daily))
        assertEquals(WorkStep.RunAt(daily), WorkPlanner.plan(RunResult.Failure(transient = true), WorkPlanner.MAX_TRANSIENT_RETRIES, daily))
        assertEquals(WorkStep.Backoff, WorkPlanner.plan(RunResult.Failure(transient = false), 0, daily))
        assertEquals(
            WorkStep.RunAt(daily),
            WorkPlanner.plan(RunResult.Failure(transient = false), WorkPlanner.MAX_UNEXPECTED_RETRIES, daily),
        )
    }

    @Test
    fun theCheckRequestCarriesItsDelay() {
        val request = NightWorkRequests.check(90_000L)
        assertEquals(90_000L, request.workSpec.initialDelay)
        assertEquals(0L, NightWorkRequests.check(-5).workSpec.initialDelay)
        assertEquals(true, NightWorkRequests.TAG in request.tags)
    }

    @Test
    fun nightDateExtraIsValidated() {
        assertEquals("2026-11-20", NightIntents.nightDateOf("2026-11-20"))
        assertNull(NightIntents.nightDateOf("2026-13-40"))
        assertNull(NightIntents.nightDateOf("../etc"))
        assertNull(NightIntents.nightDateOf(null))
        assertEquals("nightbaseline://night/2026-11-20", NightIntents.nightUri("2026-11-20"))
        assertEquals("nightbaseline://fix/permission", NightIntents.fixUri("permission"))
    }
}
