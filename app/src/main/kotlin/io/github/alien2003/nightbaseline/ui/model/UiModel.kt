package io.github.alien2003.nightbaseline.ui.model

import androidx.compose.runtime.Immutable
import io.github.alien2003.nightbaseline.algo.MetricId
import java.time.LocalDate

/** Colour family of a status (`tokens.md` 2.3). Never the only carrier: every status also has a glyph and a label. */
enum class StatusTone {
    /** `color.status.within`. */
    Within,

    /** `color.status.moved` (higher and lower share it). */
    Moved,

    /** `color.status.neutral`: learning, missing, short, not provided. */
    Neutral,

    /** `color.status.info`: data-access problems. */
    Info,
}

/** Shape carriers of metric states (`screens.md` 6, `tokens.md` 8). */
enum class MetricGlyph {
    /** Filled circle: within. */
    FilledCircle,

    /** Upward triangle: higher than usual. */
    TriangleUp,

    /** Downward triangle: lower than usual. */
    TriangleDown,

    /** Dotted ring: learning. */
    DottedRing,

    /** Short dash: no value. */
    Dash,

    /** Slashed circle: not recorded by the source. */
    SlashedCircle,

    /** Key outline: access off. */
    Key,
}

/** Shape carriers of night states (`screens.md` 6, `states.md` 2). */
enum class NightGlyph {
    /** Circle with check: all within. */
    CheckCircle,

    /** Rounded square with one triangle: some moved, below the notification threshold. */
    MovedOne,

    /** Rounded square with two triangles: moved, notified. */
    MovedTwo,

    /** Dotted ring: learning. */
    DottedRing,

    /** Half-filled circle: short night. */
    HalfCircle,

    /** Empty circle with dash: no data, too few readings. */
    DashCircle,

    /** Two overlapping half circles: two long sleeps on one day. */
    TwoHalves,

    /** Slashed circle: Health Connect not available. */
    SlashedCircle,

    /** Key outline: permission. */
    Key,

    /** Cloud with clock: not synced, source gone. */
    CloudClock,

    /** Download arrow: Health Connect update. */
    Download,

    /** No glyph (loading). */
    None,
}

/** Metric states of `states.md` 4 (the `M-*` ids). */
enum class MetricState(
    /** Shape. */
    val glyph: MetricGlyph,
    /** Colour family. */
    val tone: StatusTone,
) {
    /** `M-WITHIN`. */
    Within(MetricGlyph.FilledCircle, StatusTone.Within),

    /** `M-HIGH`: above the range on a side that counts. */
    Higher(MetricGlyph.TriangleUp, StatusTone.Moved),

    /** `M-LOW`. */
    Lower(MetricGlyph.TriangleDown, StatusTone.Moved),

    /** `M-SIDE-IGNORED`, above: shown outside the band, labelled "Usual", does not count. */
    SideIgnoredHigher(MetricGlyph.FilledCircle, StatusTone.Within),

    /** `M-SIDE-IGNORED`, below. */
    SideIgnoredLower(MetricGlyph.FilledCircle, StatusTone.Within),

    /** `M-LEARN`: fewer than 7 values for this metric. */
    Learning(MetricGlyph.DottedRing, StatusTone.Neutral),

    /** `M-NODATA`. */
    NoData(MetricGlyph.Dash, StatusTone.Neutral),

    /** `M-FEW`: too few readings. */
    TooFew(MetricGlyph.Dash, StatusTone.Neutral),

    /** `M-COVERAGE`: partial night. */
    LowCoverage(MetricGlyph.Dash, StatusTone.Neutral),

    /** `M-NOTCOMPARED`: the night is short. */
    NotCompared(MetricGlyph.Dash, StatusTone.Neutral),

    /** `M-NOTPROVIDED`: the source never writes this channel. */
    NotProvided(MetricGlyph.SlashedCircle, StatusTone.Neutral),

    /** `M-PERM`: access to this record type is off. */
    AccessOff(MetricGlyph.Key, StatusTone.Info),
    ;

    /** True for the states that were compared with a range. */
    val compared: Boolean get() = this in setOf(Within, Higher, Lower, SideIgnoredHigher, SideIgnoredLower)

    /** True when the value moved outside the range on a side that counts. */
    val moved: Boolean get() = this == Higher || this == Lower
}

/** Night states of `states.md` 1-2 (ids in brackets). */
enum class NightState(
    /** Shape. */
    val glyph: NightGlyph,
    /** Colour family. */
    val tone: StatusTone,
) {
    /** `LOADING`. */
    Loading(NightGlyph.None, StatusTone.Neutral),

    /** `L0`: learning, no valid night yet. */
    LearningStart(NightGlyph.DottedRing, StatusTone.Neutral),

    /** `L1`..`L6`. */
    Learning(NightGlyph.DottedRing, StatusTone.Neutral),

    /** `S-ALL`. */
    AllWithin(NightGlyph.CheckCircle, StatusTone.Within),

    /** `S-SOME`: moved below the notification threshold. */
    SomeMoved(NightGlyph.MovedOne, StatusTone.Moved),

    /** `S-DEV`: moved, notified. */
    Moved(NightGlyph.MovedTwo, StatusTone.Moved),

    /** `E-SYNC`. */
    NotSynced(NightGlyph.CloudClock, StatusTone.Info),

    /** `E-NODATA`. */
    NoData(NightGlyph.DashCircle, StatusTone.Neutral),

    /** `E-SHORT`. */
    Short(NightGlyph.HalfCircle, StatusTone.Neutral),

    /** `E-AMBIGUOUS`. */
    Ambiguous(NightGlyph.TwoHalves, StatusTone.Neutral),

    /** `E-NOTEVAL`. */
    NotEvaluated(NightGlyph.DashCircle, StatusTone.Neutral),

    /** `E-PERM`. */
    Permission(NightGlyph.Key, StatusTone.Info),

    /** `E-HC-UPDATE`. */
    HealthConnectUpdate(NightGlyph.Download, StatusTone.Info),

    /** `E-SOURCE-GONE`. */
    SourceGone(NightGlyph.CloudClock, StatusTone.Info),

    /** `E-HC-NONE`. */
    HealthConnectNone(NightGlyph.SlashedCircle, StatusTone.Neutral),
    ;

    /** True when the night has a result card (summary) rather than a state card. */
    val hasSummary: Boolean get() = this == AllWithin || this == SomeMoved || this == Moved
}

/** Banners of `states.md` 3, in priority order (first match wins). */
enum class BannerKind {
    /** `B-BG`. */
    Background,

    /** `B-READ-FAILED`. */
    ReadFailed,

    /** `B-SOURCE-QUIET`. */
    SourceQuiet,

    /** `B-WAIT`. */
    Wait,

    /** `B-NOTIF-OFF`. */
    NotificationsOff,

    /** `B-RANGE-READY`. */
    RangeReady,
}

/** A personal usual range in the metric's unit. */
@Immutable
data class RangeUi(
    /** Lower edge. */
    val low: Double,
    /** Upper edge. */
    val high: Double,
    /** Middle value (median). */
    val median: Double,
    /** Valid nights the range uses. */
    val nights: Int = 0,
)

/** One metric of one night, ready for a tile, the Metric detail header or a notification list. */
@Immutable
data class MetricUi(
    /** Metric. */
    val metric: MetricId,
    /** State. */
    val state: MetricState,
    /** Night value in the metric's unit (minutes for sleep duration); null when missing. */
    val value: Double? = null,
    /** Usual range on this night; null while learning or when the metric is not provided. */
    val range: RangeUi? = null,
    /** Values collected for this metric so far (learning progress, 0..7). */
    val learnedNights: Int = 0,
    /** Readings during sleep (too-few state). */
    val samples: Int? = null,
    /** Readings needed (too-few state). */
    val minSamples: Int? = null,
    /** Covered share of the night, 0..1 (partial state). */
    val coverage: Double? = null,
    /** Coverage needed, 0..1 (partial state). */
    val minCoverage: Double? = null,
) {
    /** Distance outside the range in the metric's unit (positive), or null when inside or unknown. */
    val delta: Double?
        get() {
            val v = value ?: return null
            val r = range ?: return null
            return when {
                v > r.high -> v - r.high
                v < r.low -> r.low - v
                else -> null
            }
        }
}

/** One night, ready for Home, Night detail, History rows and the calendar. */
@Immutable
data class NightUi(
    /** Local wake date (the night's key). */
    val wakeDate: LocalDate,
    /** Local date the night started on (the evening before, normally). */
    val startDate: LocalDate,
    /** Night state. */
    val state: NightState,
    /** Metrics in registry order; core metrics that are not provided stay in place as [MetricState.NotProvided]. */
    val metrics: List<MetricUi>,
    /** Optional metrics the source does not record (one "Not recorded by" row instead of tiles). */
    val notRecorded: List<MetricId> = emptyList(),
    /** Metrics that count as moved. */
    val deviating: Int = 0,
    /** Valid nights before this one (learning progress, 0..7). */
    val historyNights: Int = 0,
    /** Nights the ranges use (for "compared with your last {w} nights"). */
    val windowNights: Int = 0,
    /** Asleep minutes. */
    val asleepMinutes: Double = 0.0,
    /** Notification threshold of the current sensitivity (`minDeviatingMetrics`). */
    val threshold: Int = 2,
) {
    /** Metrics compared with a range this night. */
    val compared: Int get() = metrics.count { it.state.compared }

    /** Metrics that moved, in registry order. */
    val movedMetrics: List<MetricUi> get() = metrics.filter { it.state.moved }
}

/** One night on a metric chart. */
@Immutable
data class ChartPointUi(
    /** Wake date. */
    val date: LocalDate,
    /** Value; null leaves a gap (never interpolated). */
    val value: Double?,
    /** The range as it was on this night (the band follows the baseline over time); null while learning. */
    val range: RangeUi?,
    /** State of the value on that night. */
    val state: MetricState,
)

/** The nights of one metric for a chart, oldest first. */
@Immutable
data class MetricSeriesUi(
    /** Metric. */
    val metric: MetricId,
    /** Points, oldest first, one per calendar night (gaps included). */
    val points: List<ChartPointUi>,
)

/** Selectable chart periods (`screens.md` 7.3). */
enum class ChartPeriod(
    /** Nights shown. */
    val nights: Int,
) {
    /** 7 nights. */
    Week(7),

    /** 30 nights (default). */
    Month(30),

    /** 90 nights. */
    Quarter(90),
}
