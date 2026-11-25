package io.github.alien2003.nightbaseline.algo

import kotlinx.serialization.Serializable

/**
 * Every tunable of the algorithm. Defaults and their justification: ALGORITHM.md. Values are validated by
 * [validate]; the app's sensitivity setting maps onto [zThreshold] and [minDeviatingMetrics].
 */
@Serializable
data class AlgoParams(
    /** Number of most recent valid nights the baseline uses. */
    val windowNights: Int = 28,
    /** Valid nights needed before the first evaluation (learning period). */
    val minNights: Int = 7,
    /** Nights older than this many days before the evaluated night are never used. */
    val lookbackDays: Int = 60,
    /** Robust z beyond which a metric deviates. */
    val zThreshold: Double = 2.0,
    /** How many metrics must deviate in one night for a notification. */
    val minDeviatingMetrics: Int = 2,
    /** Nights shorter than this (asleep minutes) are not evaluated and not used for the baseline. */
    val minNightMinutes: Double = 180.0,
    /** Sessions of the source closer than this are merged into one night. */
    val mergeGapMinutes: Double = 90.0,
    /** A night's last session must end within [wakeWindowStartHour, wakeWindowEndHour) local time (naps excluded). */
    val wakeWindowStartHour: Int = 3,
    /** See [wakeWindowStartHour]. */
    val wakeWindowEndHour: Int = 15,
    /** Metric definitions; a metric is evaluated only when the source provides its channel. */
    val metrics: List<MetricSpec> = DefaultMetrics.ALL,
) {
    /** Problems with these parameters; empty when valid. */
    fun validate(): List<String> =
        buildList {
            if (windowNights !in 7..90) add("windowNights must be in 7..90")
            if (minNights !in 3..windowNights) add("minNights must be in 3..windowNights")
            if (lookbackDays < windowNights) add("lookbackDays must be >= windowNights")
            if (zThreshold !in 1.0..5.0) add("zThreshold must be in 1.0..5.0")
            if (minDeviatingMetrics !in 1..6) add("minDeviatingMetrics must be in 1..6")
            if (minNightMinutes !in 0.0..600.0) add("minNightMinutes must be in 0..600")
            if (mergeGapMinutes !in 0.0..240.0) add("mergeGapMinutes must be in 0..240")
            if (wakeWindowStartHour !in 0..23 || wakeWindowEndHour !in 1..24 || wakeWindowStartHour >= wakeWindowEndHour) {
                add("wake window must satisfy 0 <= start < end <= 24")
            }
            if (metrics.map { it.id }.toSet().size != metrics.size) add("metric ids must be unique")
            metrics.forEach { m ->
                if (m.minSpread <= 0.0) add("${m.id.key}: minSpread must be > 0")
                if (m.minAbsDeviation < 0.0) add("${m.id.key}: minAbsDeviation must be >= 0")
                if (m.plausibleMin >= m.plausibleMax) add("${m.id.key}: plausible range is empty")
                if (m.minCoverage !in 0.0..1.0) add("${m.id.key}: minCoverage must be in 0..1")
                if (m.coverageBinMin <= 0) add("${m.id.key}: coverageBinMin must be > 0")
                if ((m.aggregation == Aggregation.SLEEP_MINUTES) != (m.channel == null)) {
                    add("${m.id.key}: only SLEEP_MINUTES has no channel")
                }
            }
        }
}

/** Built-in metric definitions (initial values; see ALGORITHM.md for how they were chosen). */
object DefaultMetrics {
    /** Sleeping heart rate. */
    val SLEEPING_HR =
        MetricSpec(
            id = MetricId.SLEEPING_HR, channel = ChannelId.HEART_RATE, aggregation = Aggregation.MEDIAN,
            sensitivity = Sensitivity.BOTH, minSpread = 1.0, minAbsDeviation = 3.0,
            plausibleMin = 25.0, plausibleMax = 200.0, minSamples = 30, coverageBinMin = 10, minCoverage = 0.6,
        )

    /** Respiratory rate. */
    val RESPIRATORY_RATE =
        MetricSpec(
            id = MetricId.RESPIRATORY_RATE, channel = ChannelId.RESPIRATORY_RATE, aggregation = Aggregation.MEDIAN,
            sensitivity = Sensitivity.BOTH, minSpread = 0.3, minAbsDeviation = 1.0,
            plausibleMin = 4.0, plausibleMax = 40.0, minSamples = 6, coverageBinMin = 30, minCoverage = 0.5,
        )

    /** Blood oxygen: only low values count as deviations. */
    val SPO2 =
        MetricSpec(
            id = MetricId.SPO2, channel = ChannelId.SPO2, aggregation = Aggregation.MEDIAN,
            sensitivity = Sensitivity.LOW, minSpread = 0.5, minAbsDeviation = 1.5,
            plausibleMin = 70.0, plausibleMax = 100.0, minSamples = 4, coverageBinMin = 60, minCoverage = 0.5,
        )

    /** Sleep duration in minutes. */
    val SLEEP_DURATION =
        MetricSpec(
            id = MetricId.SLEEP_DURATION, channel = null, aggregation = Aggregation.SLEEP_MINUTES,
            sensitivity = Sensitivity.BOTH, minSpread = 20.0, minAbsDeviation = 60.0,
            plausibleMin = 0.0, plausibleMax = 1440.0, minSamples = 0, coverageBinMin = 60, minCoverage = 0.0,
        )

    /** HRV (RMSSD), used when the source writes it. */
    val HRV =
        MetricSpec(
            id = MetricId.HRV, channel = ChannelId.HRV_RMSSD, aggregation = Aggregation.MEDIAN,
            sensitivity = Sensitivity.BOTH, minSpread = 3.0, minAbsDeviation = 8.0,
            plausibleMin = 1.0, plausibleMax = 300.0, minSamples = 3, coverageBinMin = 60, minCoverage = 0.3,
        )

    /** Skin temperature, used when the source writes it. */
    val SKIN_TEMPERATURE =
        MetricSpec(
            id = MetricId.SKIN_TEMPERATURE, channel = ChannelId.SKIN_TEMPERATURE, aggregation = Aggregation.MEAN,
            sensitivity = Sensitivity.BOTH, minSpread = 0.1, minAbsDeviation = 0.3,
            plausibleMin = -10.0, plausibleMax = 45.0, minSamples = 1, coverageBinMin = 60, minCoverage = 0.0,
        )

    /** All built-in metrics in display order. */
    val ALL: List<MetricSpec> = listOf(SLEEPING_HR, RESPIRATORY_RATE, SPO2, SLEEP_DURATION, HRV, SKIN_TEMPERATURE)
}
