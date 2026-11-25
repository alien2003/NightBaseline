package io.github.alien2003.nightbaseline.ui.home

import androidx.compose.runtime.Immutable
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.model.BannerKind
import io.github.alien2003.nightbaseline.ui.model.ChartPeriod
import io.github.alien2003.nightbaseline.ui.model.MetricSeriesUi
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.model.NightUi
import java.time.LocalDate

/**
 * What Home shows (`screens.md` 7.1, `states.md` 1-5). Pure UI state: the screen is a function of it and its
 * [HomeActions]; the data layer builds it later, the screenshot tests build it from synthetic data ([HomeSamples]).
 */
@Immutable
data class HomeState(
    /** The night to show; its [NightUi.state] picks the summary, learning or state card. */
    val night: NightUi,
    /** The last 7 (or 14) nights, newest last, for the history strip. */
    val recent: List<NightUi>,
    /** Source app label. */
    val source: String,
    /** Banner, if any (first match of `states.md` 1). */
    val banner: BannerKind? = null,
    /** Chart series per metric for the detail pane (two-pane layouts). */
    val series: Map<MetricId, MetricSeriesUi> = emptyMap(),
    /**
     * The newest stored night, shown under "Last result: {night label}" below a data-access or sync state card
     * (`E-PERM`, `E-HC-UPDATE`, `E-SOURCE-GONE`, `E-SYNC`); null when nothing is stored.
     */
    val lastResult: NightUi? = null,
    /** Learning: last night was short and did not count (`state_learning_short_note`, a half-filled segment). */
    val lastNightShort: Boolean = false,
    /** Days without data from the source (`B-SOURCE-QUIET`). */
    val quietDays: Int = 3,
    /** Metric shown in the detail pane of two-pane layouts; null picks the first that moved, else the first one. */
    val selectedMetric: MetricId? = null,
    /** Chart period of the detail pane. */
    val period: ChartPeriod = ChartPeriod.Month,
    /** Selected chart point of the detail pane (index into the shown points), or null. */
    val chartSelection: Int? = null,
) {
    /** The night whose metrics fill the tiles and the detail pane: [lastResult] below a state card, else [night]. */
    val metricsNight: NightUi?
        get() =
            when {
                night.state in LAST_RESULT_STATES -> lastResult
                night.state == NightState.HealthConnectNone || night.state == NightState.Loading -> null
                night.state == NightState.NoData || night.state == NightState.Ambiguous -> null
                night.state == NightState.LearningStart -> null
                else -> night
            }

    /** The metric of the detail pane: [selectedMetric], else the first that moved, else the first one. */
    val detailMetric: MetricId?
        get() {
            val metrics = metricsNight?.metrics.orEmpty()
            return selectedMetric?.takeIf { id -> metrics.any { it.metric == id } }
                ?: (metrics.firstOrNull { it.state.moved } ?: metrics.firstOrNull())?.metric
        }

    /** The states that show the newest stored night under "Last result" (`states.md` 1 rules, 5). */
    companion object {
        /** States 1-4 except `E-HC-NONE` (nothing is stored there), plus `E-SYNC`. */
        val LAST_RESULT_STATES: Set<NightState> =
            setOf(NightState.HealthConnectUpdate, NightState.Permission, NightState.SourceGone, NightState.NotSynced)

        /** Nights in the history strip. */
        const val STRIP: Int = 7
    }
}

/**
 * Everything Home can ask for. Navigation and system intents are decided by the caller; the screen only reports the
 * person's intent.
 */
@Immutable
data class HomeActions(
    /** A metric tile was tapped: Metric detail (single pane) or selection in the detail pane (two panes). */
    val onMetric: (MetricId) -> Unit = {},
    /** A night of the history strip or the supporting pane was tapped: Night detail. */
    val onNight: (LocalDate) -> Unit = {},
    /** "See all" or the single-target strip: History. */
    val onSeeAll: () -> Unit = {},
    /** The primary action of the summary, learning or state card of [NightState] (`states.md` 2). */
    val onStateAction: (NightState) -> Unit = {},
    /** The secondary action of a state card ("Check now", "Check again"). */
    val onStateSecondary: (NightState) -> Unit = {},
    /** "How it works" / "Learn more": the range explainer. */
    val onHowItWorks: () -> Unit = {},
    /** The banner's action. */
    val onBannerAction: (BannerKind) -> Unit = {},
    /** The banner's dismiss button (only `B-NOTIF-OFF`, `B-RANGE-READY`). */
    val onBannerDismiss: (BannerKind) -> Unit = {},
    /** The info button of the "Not recorded by" row: the not-provided sheet. */
    val onNotRecordedInfo: () -> Unit = {},
    /** The disclaimer's About link. */
    val onAbout: () -> Unit = {},
    /** "More" in Glance windows: the sheet with History and Settings. */
    val onMore: () -> Unit = {},
    /** Period of the detail pane chart. */
    val onPeriod: (ChartPeriod) -> Unit = {},
    /** Selected point of the detail pane chart. */
    val onChartSelect: (Int?) -> Unit = {},
    /** Navigation bar or rail. */
    val onSelectTab: (TopLevel) -> Unit = {},
)
