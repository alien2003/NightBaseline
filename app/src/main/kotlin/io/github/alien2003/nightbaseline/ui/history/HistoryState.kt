package io.github.alien2003.nightbaseline.ui.history

import androidx.compose.runtime.Immutable
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.model.NightUi
import java.time.LocalDate
import java.time.YearMonth

/** The two History views (`screens.md` 7.4, toggle `history_view_list` / `history_view_calendar`). */
enum class HistoryView {
    /** Feed of nights, newest first, grouped by month. */
    List,

    /** Month grid. */
    Calendar,
}

/** Filter chips of the list view (`history_filter_*`). */
enum class HistoryFilter {
    /** Every stored night. */
    All,

    /** Nights where measurements moved (`S-SOME` and `S-DEV`). */
    Moved,

    /** Learning nights and nights without a comparison (short, two sleeps, no data, too few readings). */
    Gaps,
    ;

    /** True when [night] passes this filter. */
    fun matches(night: NightUi): Boolean =
        when (this) {
            All -> true
            Moved -> night.state == NightState.SomeMoved || night.state == NightState.Moved
            Gaps -> night.state in GAP_STATES
        }

    private companion object {
        val GAP_STATES =
            setOf(
                NightState.Learning,
                NightState.LearningStart,
                NightState.Short,
                NightState.Ambiguous,
                NightState.NoData,
                NightState.NotEvaluated,
            )
    }
}

/**
 * Data-access problems that History shows as a banner above the still-listed nights (`states.md` 5: `E-PERM`,
 * `E-HC-UPDATE`). `E-SOURCE-GONE` keeps the rows without a banner; `E-HC-NONE` shows `H-EMPTY`.
 */
enum class HistoryAccess {
    /** `E-PERM`: `banner_perm_history`, action "Turn on access". */
    Permission,

    /** `E-HC-UPDATE`: `banner_hc_history`, action "Install or update". */
    HealthConnectUpdate,
}

/**
 * Everything the History screen shows. Stateless input: the route (later a ViewModel plus `rememberSaveable` for
 * [view], [month], [filter] and [selected], `layouts.md` 8.1) builds it; the screenshot tests build it from
 * [io.github.alien2003.nightbaseline.ui.sample.SampleData] through [HistorySamples].
 */
@Immutable
data class HistoryUiState(
    /** Stored nights, newest first. Empty with [loading] false is `H-EMPTY`. */
    val nights: List<NightUi>,
    /** Source app label (for night copy in the preview). */
    val source: String,
    /** The local date today (outlined in the calendar; no month after it). */
    val today: LocalDate,
    /** Selected view. */
    val view: HistoryView = HistoryView.List,
    /** Selected filter of the list view. */
    val filter: HistoryFilter = HistoryFilter.All,
    /** Month shown in the calendar view. */
    val month: YearMonth = YearMonth.from(today),
    /** Selected night (wide layouts, tabletop, calendar reflow); null selects the newest night. */
    val selected: LocalDate? = null,
    /** First page query still running after 300 ms (`H-LOADING`). */
    val loading: Boolean = false,
    /** Data-access problem shown as a banner, if any. */
    val access: HistoryAccess? = null,
) {
    /** The nights of the list view after [filter]. */
    val visibleNights: List<NightUi> get() = nights.filter(filter::matches)

    /** Nights by wake date (calendar cells). */
    val byDate: Map<LocalDate, NightUi> get() = nights.associateBy { it.wakeDate }

    /** The selected night, or the newest one. */
    val selectedNight: NightUi? get() = selected?.let { date -> nights.firstOrNull { it.wakeDate == date } } ?: nights.firstOrNull()

    /** Oldest month with a stored night (no "previous month" before it). */
    val firstMonth: YearMonth get() = nights.minOfOrNull { YearMonth.from(it.wakeDate) } ?: YearMonth.from(today)

    /** True for `H-EMPTY`. */
    val empty: Boolean get() = !loading && nights.isEmpty()
}

/** What the History screen reports back; every callback has a no-op default so screenshots need none. */
@Immutable
data class HistoryActions(
    /** The List / Calendar toggle changed. */
    val onView: (HistoryView) -> Unit = {},
    /** A filter chip was picked ("Show all nights" picks [HistoryFilter.All]). */
    val onFilter: (HistoryFilter) -> Unit = {},
    /** The calendar moved to another month (buttons, swipe, Page Up / Page Down, arrow keys across the edge). */
    val onMonth: (YearMonth) -> Unit = {},
    /** A night was selected where History shows it in place (detail pane, tabletop top half, calendar reflow). */
    val onSelect: (LocalDate) -> Unit = {},
    /** Open Night detail (`night/{date}`) for a night. */
    val onOpenNight: (LocalDate) -> Unit = {},
    /** The banner action of [HistoryUiState.access]. */
    val onFixAccess: (HistoryAccess) -> Unit = {},
    /** The Glance "More" button (sheet with History and Settings). */
    val onMore: () -> Unit = {},
)
