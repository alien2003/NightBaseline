// Synthetic sample values for previews and screenshot tests.
@file:Suppress("MagicNumber")

package io.github.alien2003.nightbaseline.ui.learning

import io.github.alien2003.nightbaseline.ui.model.MetricState
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.model.NightUi
import io.github.alien2003.nightbaseline.ui.sample.SampleData

/**
 * Learning-period states built from [SampleData] (synthetic nights evaluated by the real pipeline, then moved into
 * the learning state): `L0`, `L1`..`L6` and the short-night variant, keyed by a stable id for screenshot names.
 */
object LearningSamples {
    /** `L0`: learning starts tonight, nothing collected. */
    val start: LearningState = state(0)

    /** `L3`: three nights collected. */
    val partway: LearningState = state(3)

    /** `L6` after a short night that did not count. */
    val almostShort: LearningState =
        state(6).let { s ->
            s.copy(lastNightShort = true, recent = s.recent.drop(1) + s.recent.last().copy(state = NightState.Short))
        }

    /** id -> state. */
    val all: Map<String, LearningState> = linkedMapOf("l0" to start, "l3" to partway, "l6-short" to almostShort)

    /** [collected] valid nights; the strip shows that many learning nights, the rest of the week without data. */
    fun state(collected: Int): LearningState {
        val base = SampleData.night(if (collected == 0) NightState.LearningStart else NightState.Learning)
        val night =
            base.copy(
                historyNights = collected,
                metrics = base.metrics.map { it.copy(learnedNights = collected, value = if (collected == 0) null else it.value) },
            )
        return LearningState(night = night, recent = strip(collected), source = SampleData.SOURCE)
    }

    private fun strip(collected: Int): List<NightUi> =
        SampleData.recent(STRIP_NIGHTS).mapIndexed { i, night ->
            val counted = i >= STRIP_NIGHTS - collected
            night.copy(
                state = if (counted) NightState.Learning else NightState.NoData,
                metrics = night.metrics.map { it.copy(state = if (counted) MetricState.Learning else MetricState.NoData, range = null) },
                deviating = 0,
                historyNights = if (counted) i - (STRIP_NIGHTS - collected) else 0,
            )
        }

    private const val STRIP_NIGHTS = 7
}
