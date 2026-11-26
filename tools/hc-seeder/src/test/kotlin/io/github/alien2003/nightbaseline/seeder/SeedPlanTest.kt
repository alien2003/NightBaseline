package io.github.alien2003.nightbaseline.seeder

import io.github.alien2003.nightbaseline.algo.ChannelId
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.algo.MetricStatus
import io.github.alien2003.nightbaseline.algo.NightEvaluation
import io.github.alien2003.nightbaseline.algo.NightStatus
import io.github.alien2003.nightbaseline.algo.Pipeline
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

/** Each scenario, run through :core-algo's pipeline, gives the state the bench expects on screen. */
class SeedPlanTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val now = Instant.parse("2026-11-26T17:45:00Z").toEpochMilli()

    private fun plan(
        scenario: Scenario,
        nights: Int = scenario.defaultNights,
    ) = SeedPlanner.plan(scenario, nights, now, zone)

    private fun last(scenario: Scenario): NightEvaluation {
        val p = plan(scenario)
        return Pipeline.run(p.sessions, p.channels + p.tail).last().second
    }

    @Test
    fun `normal ends within the usual range`() {
        val e = last(Scenario.NORMAL)
        assertEquals(NightStatus.ALL_WITHIN, e.status)
        assertEquals("2026-11-26", e.date)
    }

    @Test
    fun `deviations moves three metrics`() {
        val e = last(Scenario.DEVIATIONS)
        assertEquals(NightStatus.DEVIATIONS, e.status)
        assertTrue(e.deviating >= 3, "deviating ${e.deviating}")
    }

    @Test
    fun `normal and deviations share the history`() {
        val a = plan(Scenario.NORMAL)
        val b = plan(Scenario.DEVIATIONS)
        assertEquals(a.sessions, b.sessions)
        val cut = a.sessions.last().startMs
        val spo2 = { p: SeedPlan -> p.channels.getValue(ChannelId.SPO2).filter { it.timeMs < cut } }
        assertEquals(spo2(a), spo2(b))
    }

    @Test
    fun `one more night a day later keeps the earlier nights`() {
        val before = SeedPlanner.plan(Scenario.NORMAL, 60, now, zone, endDaysAgo = 1)
        val after = SeedPlanner.plan(Scenario.DEVIATIONS, 61, now, zone)
        assertEquals(before.sessions, after.sessions.dropLast(1))
        assertClearDeviations(Pipeline.run(after.sessions, after.channels).last().second, "61 nights (bench path)")
    }

    @Test
    fun `deviations clears the range edges for any night count`() {
        // The last night is a fresh noise draw for every night count, so a thin margin would pass for one count and
        // fail for the next (the bench seeds 61 nights after 60). Check a wide band of counts.
        (30..120).forEach { n ->
            val p = SeedPlanner.plan(Scenario.DEVIATIONS, n, now, zone)
            assertClearDeviations(Pipeline.run(p.sessions, p.channels).last().second, "$n nights")
        }
    }

    /** Heart rate and respiratory rate above, SpO2 below the range, each clearly beyond the edge (not by rounding). */
    private fun assertClearDeviations(
        e: NightEvaluation,
        label: String,
    ) {
        assertEquals(NightStatus.DEVIATIONS, e.status, label)
        assertTrue(e.deviating >= 3, "$label: deviating ${e.deviating}")
        val margins = mapOf(MetricId.SLEEPING_HR to 3.0, MetricId.RESPIRATORY_RATE to 0.5, MetricId.SPO2 to 1.0)
        margins.forEach { (id, minMargin) ->
            val m = e.metrics.first { it.metric == id }
            val value = checkNotNull(m.value) { "$label: no ${id.key}" }
            val base = checkNotNull(m.baseline) { "$label: no ${id.key} baseline" }
            val margin =
                if (id == MetricId.SPO2) {
                    assertEquals(MetricStatus.BELOW, m.status, "$label ${id.key}")
                    base.low - value
                } else {
                    assertEquals(MetricStatus.ABOVE, m.status, "$label ${id.key}")
                    value - base.high
                }
            assertTrue(margin >= minMargin, "$label ${id.key}: $value is only $margin beyond the edge")
        }
    }

    @Test
    fun `learning scenarios stay in the learning period`() {
        assertEquals(NightStatus.LEARNING, last(Scenario.LEARNING).status)
        assertEquals(NightStatus.LEARNING, last(Scenario.LEARNING6).status)
    }

    @Test
    fun `short, ambiguous and gap`() {
        assertEquals(NightStatus.SHORT_NIGHT, last(Scenario.SHORT_LAST).status)
        assertEquals(NightStatus.AMBIGUOUS_NIGHT, last(Scenario.AMBIGUOUS).status)
        assertEquals("2026-11-25", last(Scenario.GAP).date)
    }

    @Test
    fun `channel sets`() {
        assertEquals(setOf(ChannelId.HEART_RATE), plan(Scenario.NO_SPO2_RR).channels.keys)
        assertTrue(plan(Scenario.NO_SPO2_RR).steps.isEmpty())
        assertTrue(plan(Scenario.HRV_SKIN).channels.keys.containsAll(listOf(ChannelId.HRV_RMSSD, ChannelId.SKIN_TEMPERATURE)))
        val late = plan(Scenario.LATE_UPLOAD)
        assertEquals(setOf(ChannelId.SPO2, ChannelId.RESPIRATORY_RATE), late.tail.keys)
        assertTrue(late.channels.getValue(ChannelId.SPO2).all { it.timeMs < late.sessions.last().startMs })
    }

    @Test
    fun `nothing is in the future and sampling matches the default source`() {
        Scenario.entries.forEach { s ->
            val p = plan(s)
            assertTrue(p.sessions.all { it.endMs <= now }, s.key)
            assertTrue((p.channels + p.tail).values.flatten().all { it.timeMs <= now }, s.key)
            assertTrue(p.steps.all { it.endMs <= now }, s.key)
        }
        val hr = plan(Scenario.NORMAL).channels.getValue(ChannelId.HEART_RATE)
        val gaps = hr.zipWithNext { a, b -> b.timeMs - a.timeMs }.filter { it < 3_600_000 }
        assertEquals(120_000.0, gaps.average(), 5_000.0)
    }

    @Test
    fun `before 10 the newest night is yesterday`() {
        val morning = Instant.parse("2026-11-26T06:00:00Z").toEpochMilli()
        assertEquals("2026-11-25", SeedPlanner.plan(Scenario.NORMAL, 60, morning, zone).lastWakeDate.toString())
        assertEquals("2026-11-25", SeedPlanner.plan(Scenario.NORMAL, 60, now, zone, endDaysAgo = 1).lastWakeDate.toString())
    }
}
