package io.github.alien2003.nightbaseline.algo

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** Personal baseline (range) of one metric. */
@Serializable
data class Baseline(
    /** Metric. */
    val metric: MetricId,
    /** Median of the window. */
    val median: Double,
    /** Robust spread (MAD · 1.4826) floored at [MetricSpec.minSpread]. */
    val spread: Double,
    /** Nights in the window. */
    val nights: Int,
    /** Lower edge of the range: `median − z · spread`, at most `median − minAbsDeviation`. */
    val low: Double,
    /** Upper edge of the range. */
    val high: Double,
)

/** Status of one metric in one night. */
@Serializable
enum class MetricStatus {
    /** Inside the personal range. */
    WITHIN,

    /** Above the range. */
    ABOVE,

    /** Below the range. */
    BELOW,

    /** Not enough nights yet for this metric's range. */
    LEARNING,

    /** No value this night (see [MetricEvaluation.missing]). */
    MISSING,
}

/** One metric of one night against its baseline. */
@Serializable
data class MetricEvaluation(
    /** Metric. */
    val metric: MetricId,
    /** Night value, if any. */
    val value: Double?,
    /** Baseline, if enough nights. */
    val baseline: Baseline?,
    /** Robust z of the value, if both exist. */
    val z: Double?,
    /** Status. */
    val status: MetricStatus,
    /** True when the status counts towards [AlgoParams.minDeviatingMetrics] (respects [Sensitivity]). */
    val deviates: Boolean,
    /** Why the value is missing. */
    val missing: MissingReason? = null,
)

/** Overall result for one night. */
@Serializable
enum class NightStatus {
    /** Every evaluated metric is within range. */
    ALL_WITHIN,

    /** Some metrics deviate, fewer than [AlgoParams.minDeviatingMetrics]: shown, no notification. */
    SOME_DEVIATIONS,

    /** At least [AlgoParams.minDeviatingMetrics] metrics deviate: notification. */
    DEVIATIONS,

    /** Fewer than [AlgoParams.minNights] valid nights so far. */
    LEARNING,

    /** The night is shorter than [AlgoParams.minNightMinutes]. */
    SHORT_NIGHT,

    /** No metric could be evaluated (all missing). */
    NOT_EVALUATED,
}

/** Evaluation of one night. */
@Serializable
data class NightEvaluation(
    /** Wake date. */
    val date: String,
    /** Overall status. */
    val status: NightStatus,
    /** Per-metric results (metrics the source does not provide are omitted). */
    val metrics: List<MetricEvaluation>,
    /** Metrics counting as deviations. */
    val deviating: Int,
    /** Valid nights before this one inside the lookback (learning progress numerator). */
    val historyNights: Int,
) {
    /** Whether a notification is due. */
    val notify: Boolean get() = status == NightStatus.DEVIATIONS
}

/** Builds baselines and evaluates nights. Pure and deterministic. */
object Evaluator {
    /**
     * Baseline of [spec] for the night of [date] from [history] (any order; nights on or after [date], short nights
     * and missing values are ignored). Null when fewer than [AlgoParams.minNights] values exist.
     */
    fun baseline(spec: MetricSpec, date: String, history: List<NightSummary>, params: AlgoParams): Baseline? {
        val values = window(date, history, params).mapNotNull { it.values[spec.id]?.value }.take(params.windowNights)
        if (values.size < params.minNights) return null
        val median = Stats.median(values)
        val spread = maxOf(Stats.robustSd(values), spec.minSpread)
        val half = maxOf(params.zThreshold * spread, spec.minAbsDeviation)
        return Baseline(spec.id, median, spread, values.size, median - half, median + half)
    }

    /** Valid nights before [date] within the lookback, newest first. */
    fun window(date: String, history: List<NightSummary>, params: AlgoParams): List<NightSummary> {
        val d = LocalDate.parse(date)
        return history
            .filter { n ->
                val nd = LocalDate.parse(n.date)
                !n.short && nd.isBefore(d) && ChronoUnit.DAYS.between(nd, d) <= params.lookbackDays
            }.sortedByDescending { it.date }
    }

    /** Evaluates [night] against the nights in [history]. */
    fun evaluate(night: NightSummary, history: List<NightSummary>, params: AlgoParams): NightEvaluation {
        val historyNights = window(night.date, history, params).size
        val metrics =
            params.metrics
                .filter { night.values[it.id]?.missing != MissingReason.NOT_PROVIDED }
                .map { spec -> evaluateMetric(spec, night, history, params) }
        val deviating = metrics.count { it.deviates }
        val status =
            when {
                night.short -> NightStatus.SHORT_NIGHT
                historyNights < params.minNights -> NightStatus.LEARNING
                metrics.none { it.status in EVALUATED } -> NightStatus.NOT_EVALUATED
                deviating >= params.minDeviatingMetrics -> NightStatus.DEVIATIONS
                deviating > 0 -> NightStatus.SOME_DEVIATIONS
                else -> NightStatus.ALL_WITHIN
            }
        return NightEvaluation(night.date, status, metrics, if (status in NO_COUNT) 0 else deviating, historyNights)
    }

    private fun evaluateMetric(spec: MetricSpec, night: NightSummary, history: List<NightSummary>, params: AlgoParams): MetricEvaluation {
        val mv = night.values[spec.id]
        val value = mv?.value
        val base = baseline(spec, night.date, history, params)
        if (value == null) return MetricEvaluation(spec.id, null, base, null, MetricStatus.MISSING, false, mv?.missing ?: MissingReason.NO_DATA)
        if (base == null) return MetricEvaluation(spec.id, value, null, null, MetricStatus.LEARNING, false)
        val z = (value - base.median) / base.spread
        val status =
            when {
                value > base.high && abs(value - base.median) >= spec.minAbsDeviation -> MetricStatus.ABOVE
                value < base.low && abs(value - base.median) >= spec.minAbsDeviation -> MetricStatus.BELOW
                else -> MetricStatus.WITHIN
            }
        val deviates =
            when (spec.sensitivity) {
                Sensitivity.BOTH -> status == MetricStatus.ABOVE || status == MetricStatus.BELOW
                Sensitivity.HIGH -> status == MetricStatus.ABOVE
                Sensitivity.LOW -> status == MetricStatus.BELOW
            }
        return MetricEvaluation(spec.id, value, base, z, status, deviates && !night.short)
    }

    private val EVALUATED = setOf(MetricStatus.WITHIN, MetricStatus.ABOVE, MetricStatus.BELOW)
    private val NO_COUNT = setOf(NightStatus.SHORT_NIGHT, NightStatus.LEARNING, NightStatus.NOT_EVALUATED)
}
