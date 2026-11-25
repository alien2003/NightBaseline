package io.github.alien2003.nightbaseline.algo

import java.time.Instant
import java.time.ZoneOffset

/**
 * Picks the main night of each wake date from one source's sessions.
 *
 * Sessions closer than [AlgoParams.mergeGapMinutes] are merged into a cluster (a night split by a short wake-up).
 * A cluster belongs to the local date on which it ends; only clusters ending inside the wake window
 * `[wakeWindowStartHour, wakeWindowEndHour)` qualify, which excludes afternoon and evening naps. When several
 * clusters of one date qualify, the longest wins; the others are treated as naps and never evaluated.
 */
object NightSelector {
    /** The main nights, ordered by date. */
    fun select(
        sessions: List<SleepSession>,
        params: AlgoParams,
    ): List<NightCandidate> {
        val clusters = cluster(sessions.filter { it.endMs > it.startMs }.sortedBy { it.startMs }, params.mergeGapMinutes)
        return clusters
            .mapNotNull { c ->
                val last = c.maxBy { it.endMs }
                val local = Instant.ofEpochMilli(last.endMs).atOffset(ZoneOffset.ofTotalSeconds(last.endOffsetS))
                if (local.hour in params.wakeWindowStartHour until params.wakeWindowEndHour) {
                    NightCandidate(local.toLocalDate().toString(), c)
                } else {
                    null
                }
            }.groupBy { it.date }
            .map { (_, cands) -> cands.maxBy { it.totalMinutes } }
            .sortedBy { it.date }
    }

    private fun cluster(
        sorted: List<SleepSession>,
        gapMin: Double,
    ): List<List<SleepSession>> {
        val out = mutableListOf<MutableList<SleepSession>>()
        var curEnd = Long.MIN_VALUE
        for (s in sorted) {
            if (out.isNotEmpty() && (s.startMs - curEnd) / MS_PER_MIN <= gapMin) {
                out.last() += s
                curEnd = maxOf(curEnd, s.endMs)
            } else {
                out += mutableListOf(s)
                curEnd = s.endMs
            }
        }
        return out
    }
}

/** A selected main night before its samples are attached. */
data class NightCandidate(
    /** Local wake date. */
    val date: String,
    /** Sessions of the night. */
    val sessions: List<SleepSession>,
) {
    /** Sum of session lengths, minutes. */
    val totalMinutes: Double get() = sessions.sumOf { it.lengthMin }

    /** Attaches the samples of every channel inside the night. */
    fun withSamples(all: Map<ChannelId, List<Sample>>): NightInput {
        val start = sessions.minOf { it.startMs }
        val end = sessions.maxOf { it.endMs }
        return NightInput(date, sessions, all.mapValues { (_, s) -> s.filter { it.timeMs in start..end } })
    }
}
