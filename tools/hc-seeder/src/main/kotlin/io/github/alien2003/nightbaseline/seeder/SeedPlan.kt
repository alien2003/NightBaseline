// Scenario shapes are a set of hand-picked offsets (minutes, step counts); naming each one adds no clarity.
@file:Suppress("MagicNumber")

package io.github.alien2003.nightbaseline.seeder

import io.github.alien2003.nightbaseline.algo.ChannelId
import io.github.alien2003.nightbaseline.algo.Sample
import io.github.alien2003.nightbaseline.algo.SleepSession
import io.github.alien2003.nightbaseline.algo.SyntheticNights
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** The seeder's scenarios (tools/emu/README.md). [defaultNights] is used when no `nights` extra is given. */
enum class Scenario(
    val key: String,
    val defaultNights: Int,
) {
    /** 60 regular nights, the last one within the usual range. */
    NORMAL("normal", 60),

    /** Like [NORMAL], but last night's heart rate, respiratory rate and SpO2 moved outside the usual range. */
    DEVIATIONS("deviations", 60),

    /** Only 3 nights: the learning period. */
    LEARNING("learning", 3),

    /** 6 nights: one night before the first evaluation. */
    LEARNING6("learning6", 6),

    /** Last night lasted only 2 h. */
    SHORT_LAST("short_last", 60),

    /** Last night has a competing long sleep the same date (a long day sleep before the night). */
    AMBIGUOUS("ambiguous", 60),

    /** The source writes only sleep sessions and heart rate (no SpO2, respiratory rate or steps). */
    NO_SPO2_RR("no_spo2_rr", 60),

    /** No last night (the watch was not worn). */
    GAP("gap", 60),

    /** Also HRV (RMSSD) and skin temperature. */
    HRV_SKIN("hrv_skin", 60),

    /** Last night's respiratory rate and SpO2 arrive later (`action append_tail`). */
    LATE_UPLOAD("late_upload", 60),
    ;

    /** Lookup by [key]. */
    companion object {
        /** The scenario named [key], or null. */
        fun of(key: String): Scenario? = entries.firstOrNull { it.key == key }
    }
}

/** A step record. */
data class StepBlock(
    val startMs: Long,
    val endMs: Long,
    val count: Long,
)

/**
 * Everything one scenario writes. [channels] is written by `seed`, [tail] only by `append_tail` (late upload). All
 * timestamps are at or before the plan's `now`.
 */
data class SeedPlan(
    val scenario: Scenario,
    val offsetSeconds: Int,
    val sessions: List<SleepSession>,
    val channels: Map<ChannelId, List<Sample>>,
    val steps: List<StepBlock>,
    val tail: Map<ChannelId, List<Sample>>,
    /** Local wake date of the newest generated night (also for [Scenario.GAP], where that night is left out). */
    val lastWakeDate: LocalDate,
)

/** Builds [SeedPlan]s from :core-algo's [SyntheticNights], relative to a given "now". */
object SeedPlanner {
    /** The same seed for every scenario, so the histories of `normal` and `deviations` are identical. */
    private const val SEED = 7L
    private const val MIN_MS = 60_000L

    /** Before this local time the newest night is yesterday's (today's may still be going on). */
    private val TODAY_AFTER: LocalTime = LocalTime.of(10, 0)

    /**
     * The plan of [scenario] with [nights] nights, the newest one waking on the last wake date before [nowMs] in
     * [zone], moved [endDaysAgo] days further back.
     */
    fun plan(
        scenario: Scenario,
        nights: Int,
        nowMs: Long,
        zone: ZoneId,
        endDaysAgo: Int = 0,
    ): SeedPlan {
        require(nights in 1..365) { "nights must be in 1..365" }
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val offset = now.offset
        val newest = if (now.toLocalTime() >= TODAY_AFTER) now.toLocalDate() else now.toLocalDate().minusDays(1)
        val lastWake = newest.minusDays(endDaysAgo.toLong())
        val config = config(scenario, nights, lastWake, offset)
        val generated = SyntheticNights(config).generate().toMutableList()
        if (scenario == Scenario.GAP) generated.removeAt(generated.lastIndex)

        val sessions = generated.flatMap { it.sessions }.toMutableList()
        val channels =
            generated
                .flatMap { it.channels.entries }
                .groupBy({ it.key }, { it.value })
                .mapValues { (_, lists) -> lists.flatten().sortedBy { it.timeMs } }
                .toMutableMap()
        val tail = mutableMapOf<ChannelId, List<Sample>>()
        val last = sessions.lastOrNull()
        if (last != null) {
            when (scenario) {
                Scenario.SHORT_LAST -> {
                    shortenLast(sessions, channels, last)
                }

                Scenario.AMBIGUOUS -> {
                    addDaySleep(sessions, channels, last, offset.totalSeconds)
                }

                Scenario.NO_SPO2_RR -> {
                    channels.remove(ChannelId.SPO2)
                    channels.remove(ChannelId.RESPIRATORY_RATE)
                }

                Scenario.LATE_UPLOAD -> {
                    listOf(ChannelId.SPO2, ChannelId.RESPIRATORY_RATE).forEach { id ->
                        val all = channels[id].orEmpty()
                        tail[id] = all.filter { it.timeMs >= last.startMs }
                        channels[id] = all.filter { it.timeMs < last.startMs }
                    }
                }

                else -> {}
            }
        }
        val steps = if (scenario == Scenario.NO_SPO2_RR) emptyList() else stepsAfter(sessions.filter { it.id.startsWith("s") })
        val cut = nowMs - MIN_MS
        return SeedPlan(
            scenario = scenario,
            offsetSeconds = offset.totalSeconds,
            sessions = sessions.filter { it.endMs <= cut }.sortedBy { it.startMs },
            channels = channels.mapValues { (_, v) -> v.filter { it.timeMs <= cut } }.filterValues { it.isNotEmpty() },
            steps = steps.filter { it.endMs <= cut },
            tail = tail.mapValues { (_, v) -> v.filter { it.timeMs <= cut } }.filterValues { it.isNotEmpty() },
            lastWakeDate = lastWake,
        )
    }

    /** Generator settings: regular nights only (no missing, short, split or nap nights); scenario extras on top. */
    private fun config(
        scenario: Scenario,
        nights: Int,
        lastWake: LocalDate,
        offset: ZoneOffset,
    ): SyntheticNights.Config {
        val withExtras = scenario == Scenario.HRV_SKIN
        return SyntheticNights.Config(
            seed = SEED,
            nights = nights,
            firstWakeDate = lastWake.minusDays((nights - 1).toLong()),
            offset = offset,
            hrvLevel = if (withExtras) 42.0 else null,
            skinTempLevel = if (withExtras) 0.0 else null,
            missingNightP = 0.0,
            shortNightP = 0.0,
            napP = 0.0,
            splitP = 0.0,
            episodes = if (scenario == Scenario.DEVIATIONS) mapOf(nights - 1 to 1) else emptyMap(),
            episodeHrShift = 8.0,
            episodeRrShift = 2.0,
            episodeSpo2Shift = -2.5,
        )
    }

    /** The last night becomes its final 2 h; samples before that (except the heart-rate margin) are dropped. */
    private fun shortenLast(
        sessions: MutableList<SleepSession>,
        channels: MutableMap<ChannelId, List<Sample>>,
        last: SleepSession,
    ) {
        val start = last.endMs - 120 * MIN_MS
        sessions[sessions.lastIndex] = last.copy(startMs = start)
        channels.replaceAll { id, samples ->
            val from = if (id == ChannelId.HEART_RATE) start - 60 * MIN_MS else start
            samples.filter { it.timeMs < last.startMs - 60 * MIN_MS || it.timeMs >= from }
        }
    }

    /**
     * A 200-min sleep ending 2 h before the night starts (late afternoon to evening): far enough apart not to merge
     * with the night, close enough to be keyed to the same date, and long enough to compete with it.
     */
    private fun addDaySleep(
        sessions: MutableList<SleepSession>,
        channels: MutableMap<ChannelId, List<Sample>>,
        last: SleepSession,
        offsetS: Int,
    ) {
        val end = last.startMs - 120 * MIN_MS
        val start = end - 200 * MIN_MS
        sessions += SleepSession("day-${last.id}", start, end, offsetS)
        val hr = (start until end step 2 * MIN_MS).mapIndexed { i, t -> Sample(t, 62.0 + (i % 5)) }
        channels[ChannelId.HEART_RATE] = (channels[ChannelId.HEART_RATE].orEmpty() + hr).sortedBy { it.timeMs }
    }

    /** Brisk morning step records after each night (the "person is up" signal) and one around midday. */
    private fun stepsAfter(nights: List<SleepSession>): List<StepBlock> =
        nights.flatMap { s ->
            val wake = s.endMs
            listOf(
                StepBlock(wake + 5 * MIN_MS, wake + 15 * MIN_MS, 420),
                StepBlock(wake + 30 * MIN_MS, wake + 40 * MIN_MS, 610),
                StepBlock(wake + 300 * MIN_MS, wake + 315 * MIN_MS, 1250),
            )
        }
}
