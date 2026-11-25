package io.github.alien2003.nightbaseline.algo

import java.time.Instant
import java.time.ZoneOffset

/**
 * Picks the main night of each date from one source's sessions (rule and evidence: docs/research/metrics-matrix.md §2).
 *
 * 1. Sessions closer than [AlgoParams.mergeGapMinutes] are merged into a cluster (a night split by a wake-up).
 * 2. A cluster is keyed by the local date of its midpoint shifted by [AlgoParams.nightKeyShiftHours]; with the
 *    default 6 h, a midpoint in `[D−1 18:00, D 18:00)` belongs to date D. This needs no wake-time window, so late
 *    sleepers and shift workers keep their nights.
 * 3. The cluster with the largest summed session length wins; the others are naps and never evaluated.
 * 4. When the runner-up of a date also has at least [AlgoParams.ambiguousRunnerUpMinutes], the date is ambiguous
 *    (two long sleeps compete) and is not evaluated.
 */
object NightSelector {
    /** The main nights, ordered by date. */
    fun select(
        sessions: List<SleepSession>,
        params: AlgoParams,
    ): List<NightCandidate> {
        val clusters = cluster(sessions.filter { it.endMs > it.startMs }.sortedBy { it.startMs }, params.mergeGapMinutes)
        return clusters
            .groupBy { key(it, params) }
            .map { (date, cands) ->
                val ranked = cands.sortedByDescending { c -> c.sumOf { it.lengthMin } }
                val runnerUp = ranked.getOrNull(1)?.sumOf { it.lengthMin } ?: 0.0
                NightCandidate(date, ranked.first(), ambiguous = runnerUp >= params.ambiguousRunnerUpMinutes)
            }.sortedBy { it.date }
    }

    private fun key(
        cluster: List<SleepSession>,
        params: AlgoParams,
    ): String {
        val start = cluster.minOf { it.startMs }
        val last = cluster.maxBy { it.endMs }
        val mid = start + (last.endMs - start) / 2 + params.nightKeyShiftHours * MS_PER_HOUR
        return Instant
            .ofEpochMilli(mid)
            .atOffset(ZoneOffset.ofTotalSeconds(last.endOffsetS))
            .toLocalDate()
            .toString()
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

    private const val MS_PER_HOUR = 3_600_000L
}

/** A selected main night before its samples are attached. */
data class NightCandidate(
    /** Night date (see [NightSelector]). */
    val date: String,
    /** Sessions of the night. */
    val sessions: List<SleepSession>,
    /** Another long sleep competes for the same date; the night is not evaluated. */
    val ambiguous: Boolean = false,
) {
    /** Sum of session lengths, minutes. */
    val totalMinutes: Double get() = sessions.sumOf { it.lengthMin }

    /** Attaches the samples of every channel that fall inside one of the night's sessions. */
    fun withSamples(all: Map<ChannelId, List<Sample>>): NightInput =
        NightInput(
            date = date,
            sessions = sessions,
            channels = all.mapValues { (_, s) -> s.filter { p -> sessions.any { p.timeMs in it.startMs..it.endMs } } },
            ambiguous = ambiguous,
        )
}
