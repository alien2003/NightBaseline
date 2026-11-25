package io.github.alien2003.nightbaseline.ui.metric

import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.ui.model.MetricState
import io.github.alien2003.nightbaseline.ui.model.MetricUi
import io.github.alien2003.nightbaseline.ui.model.RangeUi
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** Derived flags of [MetricDetailState]. */
class MetricDetailStateTest {
    private fun state(
        metric: MetricId,
        low: Double,
        high: Double,
    ) = MetricDetailState(
        metric = MetricUi(metric, MetricState.Within, value = (low + high) / 2, range = RangeUi(low, high, (low + high) / 2)),
        anchorDate = LocalDate.of(2026, 11, 25),
        series = null,
        source = "Demo watch",
    )

    @Test
    fun `a sleep duration range wider than 6 hours is wide`() {
        assertTrue(state(MetricId.SLEEP_DURATION, 180.0, 690.0).wideSleepRange)
        assertTrue(MetricDetailSamples.state(MetricSample.DurationWide).wideSleepRange)
    }

    @Test
    fun `usual sleep ranges and other metrics are not`() {
        assertFalse(state(MetricId.SLEEP_DURATION, 360.0, 540.0).wideSleepRange)
        assertFalse(state(MetricId.SLEEP_DURATION, 180.0, 540.0).wideSleepRange)
        assertFalse(state(MetricId.SLEEPING_HR, 0.0, 1000.0).wideSleepRange)
        assertFalse(MetricDetailSamples.state(MetricSample.Duration).wideSleepRange)
    }
}
