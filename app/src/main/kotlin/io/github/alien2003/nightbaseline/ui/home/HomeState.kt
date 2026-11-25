package io.github.alien2003.nightbaseline.ui.home

import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.ui.model.BannerKind
import io.github.alien2003.nightbaseline.ui.model.MetricSeriesUi
import io.github.alien2003.nightbaseline.ui.model.NightUi

/** What Home shows: the night, its recent history, the source label, an optional banner and the chart series. */
data class HomeState(
    /** The night to show. */
    val night: NightUi,
    /** The last 7 (or 14) nights, newest last. */
    val recent: List<NightUi>,
    /** Source app label. */
    val source: String,
    /** Banner, if any (first match of `states.md` 1). */
    val banner: BannerKind? = null,
    /** Chart series per metric for the detail pane (two-pane layouts). */
    val series: Map<MetricId, MetricSeriesUi> = emptyMap(),
)
