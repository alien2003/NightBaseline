package io.github.alien2003.nightbaseline.algo

import kotlinx.serialization.Serializable

/** Why a metric has no value for a night. */
@Serializable
enum class MissingReason {
    /** The source does not write this channel at all. */
    NOT_PROVIDED,

    /** The channel exists, but this night has no samples. */
    NO_DATA,

    /** Fewer samples than [MetricSpec.minSamples]. */
    TOO_FEW_SAMPLES,

    /** Samples cover less of the night than [MetricSpec.minCoverage]. */
    LOW_COVERAGE,
}

/** The nightly value of one metric, or why there is none. */
@Serializable
data class MetricValue(
    /** Metric. */
    val metric: MetricId,
    /** Value in the metric's unit; null when [missing] is set. */
    val value: Double?,
    /** Samples used. */
    val samples: Int,
    /** Share of coverage bins with data (1.0 for duration). */
    val coverage: Double,
    /** Reason for a missing value. */
    val missing: MissingReason? = null,
)

/** One night reduced to metric values: the unit stored in the app and used for baselines. */
@Serializable
data class NightSummary(
    /** Local wake date. */
    val date: String,
    /** Night start, epoch ms. */
    val startMs: Long,
    /** Night end, epoch ms. */
    val endMs: Long,
    /** Asleep minutes. */
    val asleepMinutes: Double,
    /** True when shorter than [AlgoParams.minNightMinutes]: not evaluated, not used for baselines. */
    val short: Boolean,
    /** Values of every configured metric. */
    val values: Map<MetricId, MetricValue>,
)

/** Computes metric values from a night. */
object MetricExtractor {
    /**
     * Summarises [night]. [providedChannels] lists the channels the source writes at all (a channel absent there
     * gives [MissingReason.NOT_PROVIDED] rather than [MissingReason.NO_DATA]).
     */
    fun summarize(night: NightInput, providedChannels: Set<ChannelId>, params: AlgoParams): NightSummary {
        val values = params.metrics.associate { spec -> spec.id to extract(spec, night, providedChannels) }
        return NightSummary(
            date = night.date,
            startMs = night.startMs,
            endMs = night.endMs,
            asleepMinutes = night.asleepMinutes,
            short = night.asleepMinutes < params.minNightMinutes,
            values = values,
        )
    }

    private fun extract(spec: MetricSpec, night: NightInput, provided: Set<ChannelId>): MetricValue {
        if (spec.aggregation == Aggregation.SLEEP_MINUTES) {
            return MetricValue(spec.id, night.asleepMinutes, 0, 1.0)
        }
        val channel = checkNotNull(spec.channel)
        if (channel !in provided) return MetricValue(spec.id, null, 0, 0.0, MissingReason.NOT_PROVIDED)
        val samples =
            night.channels[channel].orEmpty().filter { it.value.isFinite() && it.value in spec.plausibleMin..spec.plausibleMax }
        if (samples.isEmpty()) return MetricValue(spec.id, null, 0, 0.0, MissingReason.NO_DATA)
        val coverage = coverage(samples, night, spec.coverageBinMin)
        val missing =
            when {
                samples.size < spec.minSamples -> MissingReason.TOO_FEW_SAMPLES
                coverage < spec.minCoverage -> MissingReason.LOW_COVERAGE
                else -> null
            }
        if (missing != null) return MetricValue(spec.id, null, samples.size, coverage, missing)
        val v = samples.map { it.value }
        val value =
            when (spec.aggregation) {
                Aggregation.MEDIAN -> Stats.median(v)
                Aggregation.MEAN -> Stats.mean(v)
                Aggregation.SLEEP_MINUTES -> error("handled above")
            }
        return MetricValue(spec.id, value, samples.size, coverage)
    }

    /** Share of [binMin]-minute bins of the night's sessions that contain at least one sample. */
    internal fun coverage(samples: List<Sample>, night: NightInput, binMin: Int): Double {
        val binMs = binMin * 60_000L
        var total = 0
        var covered = 0
        for (s in night.sessions) {
            val n = maxOf(1, ((s.endMs - s.startMs + binMs - 1) / binMs).toInt())
            val hit = BooleanArray(n)
            samples.forEach { p ->
                if (p.timeMs in s.startMs..s.endMs) hit[minOf(n - 1, ((p.timeMs - s.startMs) / binMs).toInt())] = true
            }
            total += n
            covered += hit.count { it }
        }
        return if (total == 0) 0.0 else covered.toDouble() / total
    }
}
