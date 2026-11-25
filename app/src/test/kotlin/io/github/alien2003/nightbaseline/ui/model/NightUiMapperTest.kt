package io.github.alien2003.nightbaseline.ui.model

import io.github.alien2003.nightbaseline.algo.Baseline
import io.github.alien2003.nightbaseline.algo.MetricEvaluation
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.algo.MetricStatus
import io.github.alien2003.nightbaseline.algo.MissingReason
import io.github.alien2003.nightbaseline.algo.NightEvaluation
import io.github.alien2003.nightbaseline.algo.NightStatus
import io.github.alien2003.nightbaseline.ui.sample.SampleData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Mapping of core-algo results to the UI model, and the synthetic sample data built on it. */
class NightUiMapperTest {
    private val baseline = Baseline(MetricId.SLEEPING_HR, 56.0, 1.6, 28, 52.0, 60.0)

    private fun eval(
        status: MetricStatus,
        deviates: Boolean = false,
        missing: MissingReason? = null,
    ) = MetricEvaluation(MetricId.SLEEPING_HR, if (missing == null) 61.0 else null, baseline, 3.0, status, deviates, missing)

    @Test
    fun `metric states follow status, side and missing reason`() {
        assertEquals(MetricState.Higher, NightUiMapper.metricState(eval(MetricStatus.ABOVE, deviates = true), short = false))
        assertEquals(MetricState.SideIgnoredHigher, NightUiMapper.metricState(eval(MetricStatus.ABOVE), short = false))
        assertEquals(MetricState.Lower, NightUiMapper.metricState(eval(MetricStatus.BELOW, deviates = true), short = false))
        assertEquals(MetricState.SideIgnoredLower, NightUiMapper.metricState(eval(MetricStatus.BELOW), short = false))
        assertEquals(MetricState.Within, NightUiMapper.metricState(eval(MetricStatus.WITHIN), short = false))
        assertEquals(MetricState.NotCompared, NightUiMapper.metricState(eval(MetricStatus.WITHIN), short = true))
        assertEquals(
            MetricState.TooFew,
            NightUiMapper.metricState(eval(MetricStatus.MISSING, missing = MissingReason.TOO_FEW_SAMPLES), false),
        )
        assertEquals(
            MetricState.LowCoverage,
            NightUiMapper.metricState(eval(MetricStatus.MISSING, missing = MissingReason.LOW_COVERAGE), false),
        )
        assertEquals(MetricState.NoData, NightUiMapper.metricState(eval(MetricStatus.MISSING, missing = MissingReason.NO_DATA), false))
    }

    @Test
    fun `night states follow the night status`() {
        fun night(
            status: NightStatus,
            history: Int = 10,
        ) = NightUiMapper.nightState(NightEvaluation("2026-11-25", status, emptyList(), 0, history))
        assertEquals(NightState.AllWithin, night(NightStatus.ALL_WITHIN))
        assertEquals(NightState.SomeMoved, night(NightStatus.SOME_DEVIATIONS))
        assertEquals(NightState.Moved, night(NightStatus.DEVIATIONS))
        assertEquals(NightState.LearningStart, night(NightStatus.LEARNING, history = 0))
        assertEquals(NightState.Learning, night(NightStatus.LEARNING, history = 3))
        assertEquals(NightState.Short, night(NightStatus.SHORT_NIGHT))
        assertEquals(NightState.Ambiguous, night(NightStatus.AMBIGUOUS_NIGHT))
        assertEquals(NightState.NotEvaluated, night(NightStatus.NOT_EVALUATED))
    }

    @Test
    fun `sample data comes from the synthetic pipeline and keeps registry order`() {
        val nights = SampleData.nights
        assertTrue(nights.size > 60, "nights: ${nights.size}")
        val order = NightUiMapper.REGISTRY_ORDER
        nights.forEach { night ->
            val ids = night.metrics.map { it.metric }
            assertEquals(ids, order.filter { it in ids }, "order on ${night.wakeDate}")
        }
        assertEquals(SampleData.LAST_WAKE, SampleData.lastNight.wakeDate)
        assertTrue(nights.any { it.state == NightState.AllWithin })
        assertTrue(nights.any { it.state == NightState.Learning || it.state == NightState.LearningStart })
    }

    @Test
    fun `derived variants are in the state they claim`() {
        NightState.entries.forEach { state -> assertEquals(state, SampleData.night(state).state) }
        MetricState.entries.forEach { state -> assertEquals(state, SampleData.metric(MetricId.SPO2, state).state) }
        assertEquals(2, SampleData.night(NightState.Moved).movedMetrics.size)
        assertEquals(1, SampleData.night(NightState.SomeMoved).movedMetrics.size)
        assertTrue(SampleData.metric(MetricId.SLEEPING_HR, MetricState.Higher).delta!! > 0)
    }

    @Test
    fun `series have one point per calendar night, gaps included`() {
        val series = SampleData.series(MetricId.SLEEPING_HR, 90)
        assertEquals(90, series.points.size)
        assertEquals(SampleData.LAST_WAKE, series.points.last().date)
        assertEquals(
            89L,
            java.time.temporal.ChronoUnit.DAYS
                .between(series.points.first().date, series.points.last().date),
        )
    }
}
