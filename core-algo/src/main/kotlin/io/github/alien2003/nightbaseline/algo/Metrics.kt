package io.github.alien2003.nightbaseline.algo

import kotlinx.serialization.Serializable

/**
 * A raw data channel read from Health Connect (or a night file). Open set: a new source type only needs a new
 * [ChannelId] and a [MetricSpec] that reads it, nothing else changes.
 */
@Serializable
@JvmInline
value class ChannelId(
    /** Stable key, also used in night files, e.g. `heart_rate`. */
    val key: String,
) {
    /** Built-in channels. */
    companion object {
        /** Heart-rate samples, bpm. */
        val HEART_RATE = ChannelId("heart_rate")

        /** Oxygen saturation, percent. */
        val SPO2 = ChannelId("spo2")

        /** Respiratory rate, breaths per minute. */
        val RESPIRATORY_RATE = ChannelId("respiratory_rate")

        /** Heart-rate variability (RMSSD), milliseconds. */
        val HRV_RMSSD = ChannelId("hrv_rmssd")

        /** Skin temperature, degrees Celsius relative to the source's baseline (delta) or absolute. */
        val SKIN_TEMPERATURE = ChannelId("skin_temperature")
    }
}

/** A nightly metric that gets a personal baseline. */
@Serializable
@JvmInline
value class MetricId(
    /** Stable key, e.g. `sleeping_hr`. */
    val key: String,
) {
    /** Built-in metrics. */
    companion object {
        /** Heart rate during sleep, computed from the samples inside the main sleep session. */
        val SLEEPING_HR = MetricId("sleeping_hr")

        /** Respiratory rate during sleep. */
        val RESPIRATORY_RATE = MetricId("respiratory_rate")

        /** Blood oxygen during sleep. */
        val SPO2 = MetricId("spo2")

        /** Sleep duration (minutes asleep; the session length when the source writes no stages). */
        val SLEEP_DURATION = MetricId("sleep_duration")

        /** HRV (RMSSD) during sleep. */
        val HRV = MetricId("hrv")

        /** Skin temperature during sleep. */
        val SKIN_TEMPERATURE = MetricId("skin_temperature")
    }
}

/** Which side of the baseline counts as a deviation. Both sides are always shown. */
@Serializable
enum class Sensitivity {
    /** Higher and lower values both count. */
    BOTH,

    /** Only values above the range count (lower is reported as within range). */
    HIGH,

    /** Only values below the range count. */
    LOW,
}

/** How a metric value is computed from the night. */
@Serializable
enum class Aggregation {
    /** Median of the channel's samples inside the session. */
    MEDIAN,

    /** Mean of the channel's samples inside the session. */
    MEAN,

    /** Mean after dropping the lowest and highest 10 % of samples. */
    TRIMMED_MEAN,

    /** 10th percentile: the low, settled level (sleeping heart rate). */
    P10,

    /** Asleep minutes (no channel). */
    SLEEP_MINUTES,
}

/**
 * Definition and tuning of one metric.
 *
 * A night value deviates when it is outside `median ± zThreshold · spread` **and** at least [minAbsDeviation]
 * away from the median, on a side allowed by [sensitivity]. [minSpread] floors the robust spread, so that a very
 * stable history does not make tiny changes look like deviations.
 */
@Serializable
data class MetricSpec(
    /** Metric id. */
    val id: MetricId,
    /** Channel the value is computed from; null for [Aggregation.SLEEP_MINUTES]. */
    val channel: ChannelId?,
    /** How the nightly value is computed. */
    val aggregation: Aggregation,
    /** Deviation side. */
    val sensitivity: Sensitivity,
    /** Lower bound of the robust spread (metric units). */
    val minSpread: Double,
    /** Minimum absolute distance from the median for a deviation (metric units). */
    val minAbsDeviation: Double,
    /** Samples outside this range are dropped as implausible. */
    val plausibleMin: Double,
    /** See [plausibleMin]. */
    val plausibleMax: Double,
    /** Minimum number of samples inside the session. */
    val minSamples: Int,
    /** Bin width (minutes) for the coverage check. */
    val coverageBinMin: Int,
    /** Minimum share of session bins that contain at least one sample. */
    val minCoverage: Double,
    /** Samples in the first minutes after the night starts are ignored (settling, late first readings). */
    val skipFirstMinutes: Int = 0,
    /**
     * Physiological metrics count towards the notification on their own; non-physiological ones (sleep duration)
     * count only when at least one physiological metric deviates in the same night.
     */
    val physiological: Boolean = true,
)
