package io.github.alien2003.nightbaseline.ui.night

import androidx.compose.runtime.Immutable
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.model.BannerKind
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.model.NightUi
import java.time.LocalDate

/** What Night detail (`night/{date}`, `screens.md` 7.2) shows: one night, its neighbours and an optional banner. */
@Immutable
data class NightDetailState(
    /** The night. */
    val night: NightUi,
    /** Source app label. */
    val source: String,
    /** Wake date of the previous stored night, or null at the oldest. */
    val previous: LocalDate? = null,
    /** Wake date of the next stored night, or null at the newest. */
    val next: LocalDate? = null,
    /** Banner (`states.md` 5: only `B-BG`, `B-READ-FAILED`, `B-NOTIF-OFF` appear here; others are ignored). */
    val banner: BannerKind? = null,
    /** Learning: the night was short and did not count. */
    val lastNightShort: Boolean = false,
    /** The top-level section the screen was opened from (selected in the bar or rail). */
    val section: TopLevel = TopLevel.History,
) {
    /** The banner if Night detail shows it. */
    val shownBanner: BannerKind? get() = banner?.takeIf { it in NIGHT_BANNERS }

    /** Whether the night has tiles: results, learning values, short nights and nights with missing readings. */
    val hasTiles: Boolean get() = night.metrics.isNotEmpty() && night.state in TILE_STATES

    /** Banners and tile states of Night detail. */
    companion object {
        /** `states.md` 5, last row. */
        val NIGHT_BANNERS: Set<BannerKind> = setOf(BannerKind.Background, BannerKind.ReadFailed, BannerKind.NotificationsOff)

        /** Night states that keep their metric tiles. */
        val TILE_STATES: Set<NightState> =
            setOf(
                NightState.AllWithin,
                NightState.SomeMoved,
                NightState.Moved,
                NightState.Learning,
                NightState.Short,
                NightState.NotEvaluated,
            )
    }
}

/** Everything Night detail can ask for; navigation is decided by the caller. */
@Immutable
data class NightDetailActions(
    /** Back (top bar, Esc, system back). */
    val onBack: () -> Unit = {},
    /** Previous night (chevron, Left key, swipe right on the summary). */
    val onPrevious: (LocalDate) -> Unit = {},
    /** Next night (chevron, Right key, swipe left on the summary). */
    val onNext: (LocalDate) -> Unit = {},
    /** A metric tile: Metric detail anchored at this night. */
    val onMetric: (MetricId) -> Unit = {},
    /** Primary action of the summary or state card. */
    val onStateAction: (NightState) -> Unit = {},
    /** Secondary action of a state card. */
    val onStateSecondary: (NightState) -> Unit = {},
    /** "How it works": the range explainer. */
    val onHowItWorks: () -> Unit = {},
    /** Banner action. */
    val onBannerAction: (BannerKind) -> Unit = {},
    /** Banner dismiss. */
    val onBannerDismiss: (BannerKind) -> Unit = {},
    /** "Not recorded by" info. */
    val onNotRecordedInfo: () -> Unit = {},
    /** Disclaimer About link. */
    val onAbout: () -> Unit = {},
    /** "More" in Glance windows. */
    val onMore: () -> Unit = {},
    /** Navigation bar or rail. */
    val onSelectTab: (TopLevel) -> Unit = {},
)
