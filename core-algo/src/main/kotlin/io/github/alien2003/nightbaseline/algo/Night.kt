package io.github.alien2003.nightbaseline.algo

import kotlinx.serialization.Serializable

/** One timestamped value of a channel. */
@Serializable
data class Sample(
    /** Epoch milliseconds. */
    val timeMs: Long,
    /** Value in the channel's unit. */
    val value: Double,
)

/** Sleep stage of a session interval, as far as the source writes stages. */
@Serializable
enum class StageKind {
    /** Awake within the session (not counted as sleep). */
    AWAKE,

    /** Any sleep stage (light, deep, REM, generic sleep). */
    ASLEEP,

    /** Out of bed or unknown: not counted as sleep. */
    OTHER,
}

/** A staged interval of a session. */
@Serializable
data class StageInterval(
    /** Start, epoch ms. */
    val startMs: Long,
    /** End, epoch ms. */
    val endMs: Long,
    /** Stage. */
    val kind: StageKind,
)

/** A sleep session as written by the source. */
@Serializable
data class SleepSession(
    /** Source record id. */
    val id: String,
    /** Start, epoch ms. */
    val startMs: Long,
    /** End, epoch ms. */
    val endMs: Long,
    /** UTC offset at the end of the session, seconds (local wake date). */
    val endOffsetS: Int,
    /** Stages, empty when the source writes none. */
    val stages: List<StageInterval> = emptyList(),
) {
    /** Session length in minutes. */
    val lengthMin: Double get() = (endMs - startMs) / MS_PER_MIN
}

/**
 * The main night of one wake date, assembled from one or more sessions of the selected source, with every channel's
 * samples that fall inside `[startMs, endMs]`.
 */
@Serializable
data class NightInput(
    /** Local wake date, ISO `yyyy-MM-dd`; the night's key. */
    val date: String,
    /** Sessions forming the night, ordered by start. */
    val sessions: List<SleepSession>,
    /** Samples per channel (only channels the source writes). */
    val channels: Map<ChannelId, List<Sample>>,
) {
    init {
        require(sessions.isNotEmpty()) { "a night needs at least one session" }
    }

    /** First session start. */
    val startMs: Long get() = sessions.first().startMs

    /** Last session end. */
    val endMs: Long get() = sessions.maxOf { it.endMs }

    /** Asleep minutes: staged sleep where stages exist, otherwise the whole session length. */
    val asleepMinutes: Double
        get() =
            sessions.sumOf { s ->
                if (s.stages.isEmpty()) {
                    s.lengthMin
                } else {
                    s.stages.filter { it.kind == StageKind.ASLEEP }.sumOf { (it.endMs - it.startMs) / MS_PER_MIN }
                }
            }
}

internal const val MS_PER_MIN = 60_000.0
