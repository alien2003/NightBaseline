package io.github.alien2003.nightbaseline.algo

import kotlinx.serialization.Serializable

/**
 * When is a night complete in the source? Sources publish a night in several uploads and some metrics arrive late
 * (on OHealth SpO2 about an hour, respiratory rate about half an hour after the samples' time), so evaluating right
 * after the session ends would use partial data. Rule and evidence: docs/research/metrics-matrix.md §3.
 *
 * The night is complete when all of these hold:
 * 1. the person is up: a step record of at least [CompletenessParams.upStepsPerMinute] steps/min starting at least
 *    [CompletenessParams.upAfterEndMinutes] after the visible end exists; sources without such step records fall back
 *    to "ended at least [CompletenessParams.fallbackEndedMinutes] ago";
 * 2. heart rate reaches `end − hrLag`, respiratory rate `end − rrLag`, SpO2 `end − spo2Lag` (only channels the source
 *    writes at all);
 * 3. nothing new was written by the source for [CompletenessParams.quietMinutes];
 * or the hard cap [CompletenessParams.capHours] after the end has passed (evaluate with what is there).
 */
object Completeness {
    /** Decides for the visible main night. */
    fun judge(
        input: CompletenessInput,
        params: CompletenessParams = CompletenessParams(),
    ): CompletenessVerdict {
        val sinceEndMin = (input.nowMs - input.nightEndMs) / MS_PER_MIN
        if (sinceEndMin >= params.capHours * MINUTES_PER_HOUR) return CompletenessVerdict(Readiness.CAP_REACHED, emptyList())
        val waiting = mutableListOf<WaitReason>()
        val up =
            if (input.sourceWritesSteps) {
                input.firstBriskStepAfterEndMs?.let { (it - input.nightEndMs) / MS_PER_MIN >= params.upAfterEndMinutes } ?: false
            } else {
                sinceEndMin >= params.fallbackEndedMinutes
            }
        if (!up) waiting += WaitReason.NOT_UP_YET

        fun reaches(
            channel: ChannelId,
            lagMin: Double,
        ) {
            if (channel !in input.providedChannels) return
            val last = input.lastSampleMs[channel]
            if (last == null || last < input.nightEndMs - (lagMin * MS_PER_MIN).toLong()) waiting += WaitReason.CHANNEL_INCOMPLETE
        }
        reaches(ChannelId.HEART_RATE, params.hrLagMinutes)
        reaches(ChannelId.RESPIRATORY_RATE, params.rrLagMinutes)
        reaches(ChannelId.SPO2, params.spo2LagMinutes)
        val quietMin = (input.nowMs - input.lastWriteMs) / MS_PER_MIN
        if (quietMin < params.quietMinutes) waiting += WaitReason.SOURCE_STILL_WRITING
        val reasons = waiting.distinct()
        return CompletenessVerdict(if (reasons.isEmpty()) Readiness.COMPLETE else Readiness.WAIT, reasons)
    }

    private const val MINUTES_PER_HOUR = 60.0
}

/** What the worker knows about the night at check time. */
data class CompletenessInput(
    /** Check time, epoch ms. */
    val nowMs: Long,
    /** Visible end of the main night, epoch ms. */
    val nightEndMs: Long,
    /** Channels the source writes at all. */
    val providedChannels: Set<ChannelId>,
    /** Newest sample time per channel inside the night. */
    val lastSampleMs: Map<ChannelId, Long>,
    /** Whether the source writes fine-grained step records. */
    val sourceWritesSteps: Boolean,
    /** Start of the first step record after the night end at or above the brisk threshold, if any. */
    val firstBriskStepAfterEndMs: Long?,
    /** Newest lastModified of any record of the source within the night window. */
    val lastWriteMs: Long,
)

/** Thresholds of [Completeness]. */
@Serializable
data class CompletenessParams(
    /** Steps per minute that show the person is up. */
    val upStepsPerMinute: Double = 5.0,
    /** The brisk step record must start at least this long after the visible end. */
    val upAfterEndMinutes: Double = 2.0,
    /** Without step records: the night must have ended this long ago. */
    val fallbackEndedMinutes: Double = 180.0,
    /** Heart rate must reach the end minus this lag. */
    val hrLagMinutes: Double = 5.0,
    /** Respiratory rate must reach the end minus this lag. */
    val rrLagMinutes: Double = 30.0,
    /** SpO2 must reach the end minus this lag. */
    val spo2LagMinutes: Double = 60.0,
    /** No new writes from the source for this long. */
    val quietMinutes: Double = 15.0,
    /** After this many hours from the end, evaluate with whatever is there. */
    val capHours: Double = 12.0,
)

/** Result of [Completeness.judge]. */
@Serializable
enum class Readiness {
    /** Evaluate now. */
    COMPLETE,

    /** Check again later. */
    WAIT,

    /** Give up waiting; evaluate with what is there and mark missing metrics. */
    CAP_REACHED,
}

/** Why the night is not complete yet. */
@Serializable
enum class WaitReason {
    /** No sign that the person got up. */
    NOT_UP_YET,

    /** A channel does not reach the end of the night yet. */
    CHANNEL_INCOMPLETE,

    /** The source wrote something within the quiet period. */
    SOURCE_STILL_WRITING,
}

/** Verdict with reasons. */
@Serializable
data class CompletenessVerdict(
    /** Readiness. */
    val readiness: Readiness,
    /** Reasons when waiting. */
    val reasons: List<WaitReason>,
)
