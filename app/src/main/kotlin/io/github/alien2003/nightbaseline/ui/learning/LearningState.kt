package io.github.alien2003.nightbaseline.ui.learning

import androidx.compose.runtime.Immutable
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.model.NightUi

/** What the learning-period presentation of Home shows (`screens.md` 7.6, states `L0`, `L1`..`L6`). */
@Immutable
data class LearningState(
    /** The newest night, in [NightState.LearningStart] or [NightState.Learning]; `historyNights` is the progress. */
    val night: NightUi,
    /** The last 7 nights, newest last (learning glyphs for counted nights, short-night glyphs for skipped ones). */
    val recent: List<NightUi>,
    /** Source app label. */
    val source: String,
    /** Last night was under 3 hours, so it did not count (`state_learning_short_note`, half-filled segment). */
    val lastNightShort: Boolean = false,
) {
    /** Valid nights collected so far, 0..7. */
    val collected: Int get() = night.historyNights.coerceIn(0, LEARNING_NIGHTS)

    /** No value has arrived yet (`L0`): the "Already visible" block shows a note instead of tiles. */
    val nothingYet: Boolean get() = night.state == NightState.LearningStart || night.metrics.all { it.value == null }

    /** Constants. */
    companion object {
        /** Nights of the learning period. */
        const val LEARNING_NIGHTS: Int = 7
    }
}

/** Callbacks of [LearningScreen]. */
@Immutable
data class LearningActions(
    /** "How it works" -> range explainer. */
    val onHowItWorks: () -> Unit = {},
    /** A metric tile -> Metric detail. */
    val onMetric: (MetricId) -> Unit = {},
    /** A night of the strip -> Night detail. */
    val onNight: (NightUi) -> Unit = {},
    /** "See all" -> History. */
    val onSeeAll: () -> Unit = {},
    /** Disclaimer footer "About". */
    val onAbout: () -> Unit = {},
    /** Glance "More". */
    val onMore: () -> Unit = {},
)
