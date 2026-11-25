package io.github.alien2003.nightbaseline.ui.metric

import androidx.compose.runtime.Immutable
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.algo.Sensitivity
import io.github.alien2003.nightbaseline.ui.model.ChartPeriod
import io.github.alien2003.nightbaseline.ui.model.ChartPointUi
import io.github.alien2003.nightbaseline.ui.model.MetricSeriesUi
import io.github.alien2003.nightbaseline.ui.model.MetricState
import io.github.alien2003.nightbaseline.ui.model.MetricUi
import java.time.LocalDate

/** Screen-level problems Metric detail shows next to still-valid stored nights (`states.md` 5, Metric detail column). */
enum class MetricNotice {
    /** `E-SYNC`: inline `detail_waiting_sync` under the header. */
    WaitingSync,

    /** `E-SOURCE-GONE`: a state card in place of the header sentence, stored chart below. */
    SourceGone,

    /** `E-PERM`: a state card in place of the chart, stored chart below. */
    AccessOff,

    /** `E-HC-UPDATE`: a state card, stored chart below. */
    HealthConnectUpdate,
}

/**
 * Everything Metric detail (`screens.md` 7.3) shows. Stateless: the screen draws this and reports intents through
 * [MetricDetailActions]; period, selection and the explainer sheet are owned by the caller (saved state).
 */
@Immutable
data class MetricDetailState(
    /** The metric on the anchor night (header card). */
    val metric: MetricUi,
    /** Wake date of the anchor night (default: last night). */
    val anchorDate: LocalDate,
    /** Stored nights of this metric up to the anchor night, oldest first, one per calendar night (gaps included). */
    val series: MetricSeriesUi?,
    /** Source app label. */
    val source: String,
    /** Selected chart period. */
    val period: ChartPeriod = ChartPeriod.Month,
    /** Selected night as an index into [shownPoints], or null. */
    val selected: Int? = null,
    /** Which side of the range counts for this metric (SpO2: only lower values). */
    val countedSide: Sensitivity = Sensitivity.BOTH,
    /** A data-access problem shown above the stored nights. */
    val notice: MetricNotice? = null,
    /** Last sync time, already formatted for the locale; null when unknown. */
    val lastSync: String? = null,
    /** `M-NOTPROVIDED` with stored nights from an earlier source: the date of the last value. */
    val notProvidedSince: LocalDate? = null,
    /** Nights stored for this metric (calendar nights since the first value); drives `D-PERIOD-SHORT`. */
    val storedNights: Int = series?.points?.size ?: 0,
    /** `LOADING`: skeleton header and chart frame. */
    val loading: Boolean = false,
    /** The "How your usual range works" explainer is open (sheet, dialog or end pane per layout). */
    val explainerOpen: Boolean = false,
) {
    /** The nights of the selected period (fewer when less is stored). */
    val shownPoints: List<ChartPointUi>
        get() = series?.points?.takeLast(minOf(period.nights, storedNights)).orEmpty()

    /** The series of the selected period. */
    val shownSeries: MetricSeriesUi?
        get() = series?.copy(points = shownPoints)

    /** `D-PERIOD-SHORT`: the period asks for more nights than are stored. */
    val periodShort: Boolean
        get() = series != null && storedNights in 1 until period.nights

    /** `D-BAND-CHANGED`: the first night in the period on which the median moved more than one spread. */
    val bandShiftDate: LocalDate?
        get() = bandShift(shownPoints)

    /** True while the metric has no range yet (learning). */
    val learning: Boolean
        get() = metric.state == MetricState.Learning

    /** True when there is something to chart (any stored value or range in the period). */
    val hasChart: Boolean
        get() = shownPoints.any { it.value != null || it.range != null } || (learning && series != null)

    /**
     * Sleep duration whose usual range spans more than [WIDE_SLEEP_RANGE_MINUTES]: the person's sleep length varies a
     * lot, so only big changes are noticed (`detail_sleep_range_wide`).
     */
    val wideSleepRange: Boolean
        get() {
            if (metric.metric != MetricId.SLEEP_DURATION) return false
            val range = metric.range ?: series?.points?.lastOrNull { it.range != null }?.range ?: return false
            return range.high - range.low > WIDE_SLEEP_RANGE_MINUTES
        }
}

/** From this width (6 h) a sleep duration range counts as very wide. */
const val WIDE_SLEEP_RANGE_MINUTES: Double = 360.0

/** Robust spread of a range: the band is the median plus or minus about two spreads (`AlgoParams` z = 2). */
private const val BAND_SPREADS = 4.0

/** The first date in [points] where the median moved more than one spread from the night before, or null. */
fun bandShift(points: List<ChartPointUi>): LocalDate? {
    val ranged = points.filter { it.range != null }
    return ranged
        .zipWithNext()
        .firstOrNull { (before, after) ->
            val a = checkNotNull(before.range)
            val b = checkNotNull(after.range)
            val spread = (a.high - a.low) / BAND_SPREADS
            spread > 0 && kotlin.math.abs(b.median - a.median) > spread
        }?.second
        ?.date
}

/** What the person can do on Metric detail; every callback defaults to doing nothing (screenshots, previews). */
@Immutable
data class MetricDetailActions(
    /** Back to the parent. */
    val onBack: () -> Unit = {},
    /** A period segment was chosen. */
    val onPeriod: (ChartPeriod) -> Unit = {},
    /** Chart selection changed (index into the shown points, or null). */
    val onSelect: (Int?) -> Unit = {},
    /** Double tap on a night: open Night detail. */
    val onOpenNight: (LocalDate) -> Unit = {},
    /** Open the range explainer. */
    val onOpenExplainer: () -> Unit = {},
    /** Close the range explainer. */
    val onCloseExplainer: () -> Unit = {},
    /** Choose another source (source picker). */
    val onChooseSource: () -> Unit = {},
    /** Open Health Connect permissions. */
    val onOpenPermissions: () -> Unit = {},
    /** Install or update Health Connect. */
    val onOpenStore: () -> Unit = {},
    /** Open About (disclaimer footer). */
    val onAbout: () -> Unit = {},
)

/** `M-NOTPROVIDED` with nothing stored: a state card takes the chart's place. */
internal val MetricDetailState.notProvidedNone: Boolean
    get() = metric.state == MetricState.NotProvided && series == null
