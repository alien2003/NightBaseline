package io.github.alien2003.nightbaseline.ui.model

import io.github.alien2003.nightbaseline.algo.AlgoParams
import io.github.alien2003.nightbaseline.algo.Baseline
import io.github.alien2003.nightbaseline.algo.DefaultMetrics
import io.github.alien2003.nightbaseline.algo.Evaluator
import io.github.alien2003.nightbaseline.algo.MetricEvaluation
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.algo.MetricSpec
import io.github.alien2003.nightbaseline.algo.MetricStatus
import io.github.alien2003.nightbaseline.algo.MissingReason
import io.github.alien2003.nightbaseline.algo.NightEvaluation
import io.github.alien2003.nightbaseline.algo.NightStatus
import io.github.alien2003.nightbaseline.algo.NightSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Maps core-algo results ([NightSummary] + [NightEvaluation]) to the UI model. Pure: the screens and the synthetic
 * [io.github.alien2003.nightbaseline.ui.sample.SampleData] use the same mapping.
 */
object NightUiMapper {
    /** The core metrics keep their tile even when the source does not record them (`states.md` 4.1). */
    val CORE_METRICS: Set<MetricId> =
        setOf(MetricId.SLEEPING_HR, MetricId.RESPIRATORY_RATE, MetricId.SPO2, MetricId.SLEEP_DURATION)

    /**
     * The UI night for [evaluation] of [summary]; [history] are the stored summaries (used for per-metric learning
     * progress), [accessOff] the metrics whose Health Connect permission is not granted, [zone] the person's zone for
     * the night's start date.
     */
    fun night(
        summary: NightSummary,
        evaluation: NightEvaluation,
        params: AlgoParams = AlgoParams(),
        history: List<NightSummary> = emptyList(),
        accessOff: Set<MetricId> = emptySet(),
        zone: ZoneOffset = ZoneOffset.UTC,
    ): NightUi {
        val evaluated = evaluation.metrics.associateBy { it.metric }
        val metrics = mutableListOf<MetricUi>()
        val notRecorded = mutableListOf<MetricId>()
        params.metrics.forEach { spec ->
            val id = spec.id
            val metricEvaluation = evaluated[id]
            when {
                id in accessOff -> {
                    metrics += MetricUi(id, MetricState.AccessOff)
                }

                metricEvaluation != null -> {
                    metrics += metric(spec, metricEvaluation, summary, params, history)
                }

                summary.values[id]?.missing == MissingReason.NOT_PROVIDED -> {
                    if (id in CORE_METRICS) metrics += MetricUi(id, MetricState.NotProvided) else notRecorded += id
                }
            }
        }
        val windowNights = evaluation.metrics.mapNotNull { it.baseline?.nights }.maxOrNull() ?: 0
        return NightUi(
            wakeDate = LocalDate.parse(evaluation.date),
            startDate = Instant.ofEpochMilli(summary.startMs).atOffset(zone).toLocalDate(),
            state = nightState(evaluation),
            metrics = metrics,
            notRecorded = notRecorded,
            deviating = evaluation.deviating,
            historyNights = evaluation.historyNights,
            windowNights = windowNights,
            asleepMinutes = summary.asleepMinutes,
            threshold = params.minDeviatingMetrics,
        )
    }

    /** Night state of an evaluated night. Problem states (permission, sync, Health Connect) come from the data layer. */
    fun nightState(evaluation: NightEvaluation): NightState =
        when (evaluation.status) {
            NightStatus.ALL_WITHIN -> NightState.AllWithin
            NightStatus.SOME_DEVIATIONS -> NightState.SomeMoved
            NightStatus.DEVIATIONS -> NightState.Moved
            NightStatus.LEARNING -> if (evaluation.historyNights == 0) NightState.LearningStart else NightState.Learning
            NightStatus.SHORT_NIGHT -> NightState.Short
            NightStatus.AMBIGUOUS_NIGHT -> NightState.Ambiguous
            NightStatus.NOT_EVALUATED -> NightState.NotEvaluated
        }

    /** Metric state of one evaluation; [short] marks a short night (values shown, not compared). */
    fun metricState(
        evaluation: MetricEvaluation,
        short: Boolean,
    ): MetricState =
        when {
            evaluation.status == MetricStatus.MISSING -> {
                when (evaluation.missing) {
                    MissingReason.TOO_FEW_SAMPLES -> MetricState.TooFew
                    MissingReason.LOW_COVERAGE -> MetricState.LowCoverage
                    MissingReason.NOT_PROVIDED -> MetricState.NotProvided
                    MissingReason.NO_DATA, null -> MetricState.NoData
                }
            }

            short -> {
                MetricState.NotCompared
            }

            evaluation.status == MetricStatus.LEARNING -> {
                MetricState.Learning
            }

            evaluation.status == MetricStatus.ABOVE -> {
                if (evaluation.deviates) MetricState.Higher else MetricState.SideIgnoredHigher
            }

            evaluation.status == MetricStatus.BELOW -> {
                if (evaluation.deviates) MetricState.Lower else MetricState.SideIgnoredLower
            }

            else -> {
                MetricState.Within
            }
        }

    /** The range of a core-algo [Baseline]. */
    fun range(baseline: Baseline): RangeUi = RangeUi(baseline.low, baseline.high, baseline.median, baseline.nights)

    private fun metric(
        spec: MetricSpec,
        evaluation: MetricEvaluation,
        summary: NightSummary,
        params: AlgoParams,
        history: List<NightSummary>,
    ): MetricUi {
        val state = metricState(evaluation, summary.short || summary.ambiguous)
        val value = summary.values[spec.id]
        val learned =
            if (state == MetricState.Learning) {
                Evaluator
                    .window(summary.date, history, params)
                    .count { it.values[spec.id]?.value != null }
                    .coerceAtMost(params.minNights - 1)
            } else {
                0
            }
        return MetricUi(
            metric = spec.id,
            state = state,
            value = evaluation.value,
            range = evaluation.baseline?.let(::range),
            learnedNights = learned,
            samples = value?.samples,
            minSamples = spec.minSamples,
            coverage = value?.coverage,
            minCoverage = spec.minCoverage,
        )
    }

    /** The registry order of metrics (`DefaultMetrics.ALL`). */
    val REGISTRY_ORDER: List<MetricId> = DefaultMetrics.ALL.map { it.id }
}
