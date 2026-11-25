// The synthetic person model is a set of hand-picked physiological numbers; naming each one adds no clarity.
@file:Suppress("MagicNumber")

package io.github.alien2003.nightbaseline.algo

import java.time.LocalDate
import java.time.ZoneOffset
import java.util.SplittableRandom
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Deterministic synthetic nights for tests, screenshots and the CLI. Never used to choose parameters for a real
 * person, only to check that the algorithm behaves as intended on data with known ground truth.
 *
 * A synthetic person has a personal level per metric, night-to-night noise and within-night noise. An "episode"
 * shifts several metrics at once for a few nights (like a cold or a hard training block), which is what the
 * algorithm should report.
 */
class SyntheticNights(
    private val config: Config = Config(),
) {
    /** Generator settings. Sampling intervals mirror the default source (OHealth): HR 2 min, SpO2 30 min, RR ~8 min. */
    data class Config(
        /** Random seed; the same seed always gives the same nights. */
        val seed: Long = 1,
        /** Number of nights to generate. */
        val nights: Int = 60,
        /** Local wake date of the first night. */
        val firstWakeDate: LocalDate = LocalDate.of(2026, 1, 1),
        /** Zone offset of the synthetic person. */
        val offset: ZoneOffset = ZoneOffset.ofHours(3),
        /** Personal sleeping heart rate level, bpm. */
        val hrLevel: Double = 56.0,
        /** Night-to-night standard deviation of the heart rate level, bpm. */
        val hrNightSd: Double = 1.6,
        /** Personal respiratory rate level, breaths per minute. */
        val rrLevel: Double = 14.5,
        /** Night-to-night standard deviation of the respiratory rate level. */
        val rrNightSd: Double = 0.35,
        /** Personal SpO2 level, percent. */
        val spo2Level: Double = 96.0,
        /** Night-to-night standard deviation of the SpO2 level, percent. */
        val spo2NightSd: Double = 0.5,
        /** Typical asleep minutes per night. */
        val sleepMinLevel: Double = 440.0,
        /** Night-to-night standard deviation of asleep minutes. */
        val sleepMinSd: Double = 35.0,
        /** Personal HRV (RMSSD) level, ms; null when the source does not write HRV. */
        val hrvLevel: Double? = null,
        /** Personal skin temperature level, degrees Celsius; null when the source does not write it. */
        val skinTempLevel: Double? = null,
        /** Probability that a night is missing (watch not worn). */
        val missingNightP: Double = 0.04,
        /** Probability of a short night (< 3 h). */
        val shortNightP: Double = 0.03,
        /** Probability of an extra afternoon nap session. */
        val napP: Double = 0.1,
        /** Probability that a night is split into two sessions by a wake-up. */
        val splitP: Double = 0.1,
        /** Episodes: start night index (0-based) to length in nights. */
        val episodes: Map<Int, Int> = emptyMap(),
        /** Heart rate shift during an episode, bpm. */
        val episodeHrShift: Double = 6.0,
        /** Respiratory rate shift during an episode, breaths per minute. */
        val episodeRrShift: Double = 1.6,
        /** SpO2 shift during an episode, percent. */
        val episodeSpo2Shift: Double = -1.8,
    )

    /** One generated night with its ground truth. */
    data class Generated(
        /** Sleep sessions of the night (including naps and split parts). */
        val sessions: List<SleepSession>,
        /** Samples per channel. */
        val channels: Map<ChannelId, List<Sample>>,
        /** Local wake date (ISO). */
        val wakeDate: String,
        /** Ground truth: the night belongs to an episode. */
        val inEpisode: Boolean,
        /** The night has no data (watch not worn). */
        val missing: Boolean,
        /** The night is shorter than 3 h. */
        val short: Boolean,
    )

    /** Generates the configured nights. */
    fun generate(): List<Generated> {
        val rnd = SplittableRandom(config.seed)
        val episodeNights = config.episodes.flatMap { (s, len) -> (s until s + len) }.toSet()
        return (0 until config.nights).map { i -> night(i, rnd, i in episodeNights) }
    }

    /** All sessions and samples of [generated], flattened per channel, as a source would expose them. */
    fun flatten(generated: List<Generated>): Pair<List<SleepSession>, Map<ChannelId, List<Sample>>> {
        val sessions = generated.flatMap { it.sessions }
        val channels =
            generated
                .flatMap { it.channels.entries }
                .groupBy({ it.key }, { it.value })
                .mapValues { (_, lists) -> lists.flatten().sortedBy { it.timeMs } }
        return sessions to channels
    }

    private fun night(
        i: Int,
        rnd: SplittableRandom,
        episode: Boolean,
    ): Generated {
        val c = config
        val wake = c.firstWakeDate.plusDays(i.toLong())
        val missing = rnd.nextDouble() < c.missingNightP
        val short = !missing && rnd.nextDouble() < c.shortNightP
        val asleep = if (short) 100.0 + rnd.nextDouble() * 60 else max(200.0, c.sleepMinLevel + gauss(rnd) * c.sleepMinSd)
        val wakeMinuteOfDay = 7 * 60 + (gauss(rnd) * 40).toInt()
        val endMs = wake.atStartOfDay().toInstant(c.offset).toEpochMilli() + wakeMinuteOfDay * MS
        val startMs = endMs - (asleep * MS).toLong()
        if (missing) return Generated(emptyList(), emptyMap(), wake.toString(), episode, missing = true, short = false)

        val sessions = mutableListOf<SleepSession>()
        if (!short && rnd.nextDouble() < c.splitP) {
            val cut = startMs + ((endMs - startMs) * (0.3 + rnd.nextDouble() * 0.4)).toLong()
            sessions += SleepSession("s$i-a", startMs, cut, c.offset.totalSeconds)
            sessions += SleepSession("s$i-b", cut + (20 + rnd.nextInt(30)) * MS, endMs, c.offset.totalSeconds)
        } else {
            sessions += SleepSession("s$i", startMs, endMs, c.offset.totalSeconds)
        }
        val shift = if (episode) 1.0 else 0.0
        val hrNight = c.hrLevel + gauss(rnd) * c.hrNightSd + shift * c.episodeHrShift
        val rrNight = c.rrLevel + gauss(rnd) * c.rrNightSd + shift * c.episodeRrShift
        val spo2Night = c.spo2Level + gauss(rnd) * c.spo2NightSd + shift * c.episodeSpo2Shift
        val channels = mutableMapOf<ChannelId, List<Sample>>()
        channels[ChannelId.HEART_RATE] =
            series(rnd, (startMs - 60 * MS)..(endMs + 60 * MS), 2.0, 0.0) { t ->
                // Sleep dip in the first half, small noise; the margin outside the session is daytime-like.
                val inSleep = t in startMs..endMs
                val phase = (t - startMs).toDouble() / max(1, endMs - startMs)
                if (inSleep) hrNight + 4 * (phase - 0.5) * (phase - 0.5) * 4 - 2 + gauss(rnd) * 3 else hrNight + 18 + gauss(rnd) * 5
            }.map { it.copy(value = round(it.value)) }
        channels[ChannelId.RESPIRATORY_RATE] = series(rnd, startMs..endMs, 8.0, 3.0) { rrNight + gauss(rnd) * 0.8 }
        channels[ChannelId.SPO2] = series(rnd, startMs..endMs, 30.0, 0.0) { minOf(100.0, round(spo2Night + gauss(rnd) * 1.0)) }
        c.hrvLevel?.let { h ->
            channels[ChannelId.HRV_RMSSD] =
                series(rnd, startMs..endMs, 60.0, 0.0) { max(5.0, h + gauss(rnd) * 8 - shift * 10) }
        }
        c.skinTempLevel?.let { s ->
            channels[ChannelId.SKIN_TEMPERATURE] =
                series(rnd, startMs..endMs, 60.0, 0.0) { s + gauss(rnd) * 0.15 + shift * 0.5 }
        }
        if (rnd.nextDouble() < c.napP) {
            val napStart = wake.atStartOfDay().toInstant(c.offset).toEpochMilli() + (14 * 60 + rnd.nextInt(120)) * MS
            sessions += SleepSession("nap$i", napStart, napStart + (20 + rnd.nextInt(60)) * MS, c.offset.totalSeconds)
        }
        return Generated(sessions, channels, wake.toString(), episode, missing = false, short = short)
    }

    private fun series(
        rnd: SplittableRandom,
        span: LongRange,
        everyMin: Double,
        jitterMin: Double,
        f: (Long) -> Double,
    ): List<Sample> {
        val out = mutableListOf<Sample>()
        var t = span.first + (rnd.nextDouble() * everyMin * MS).toLong()
        while (t <= span.last) {
            out += Sample(t, f(t))
            t += ((everyMin + (rnd.nextDouble() * 2 - 1) * jitterMin) * MS).toLong().coerceAtLeast(MS)
        }
        return out
    }

    private companion object {
        const val MS = 60_000L

        /** Standard normal via Box-Muller (SplittableRandom has no nextGaussian on Java 17). */
        fun gauss(rnd: SplittableRandom): Double {
            val u1 = rnd.nextDouble().coerceAtLeast(1e-12)
            val u2 = rnd.nextDouble()
            return sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2 * Math.PI * u2)
        }
    }
}

/** Convenience pipeline used by the CLI, tests and the app: sessions + samples → summaries → evaluations. */
object Pipeline {
    /** Selects main nights, summarises and evaluates each night against the previous ones. */
    fun run(
        sessions: List<SleepSession>,
        channels: Map<ChannelId, List<Sample>>,
        params: AlgoParams = AlgoParams(),
    ): List<Pair<NightSummary, NightEvaluation>> {
        val provided = channels.filterValues { it.isNotEmpty() }.keys
        val summaries = NightSelector.select(sessions, params).map { MetricExtractor.summarize(it.withSamples(channels), provided, params) }
        return summaries.map { s -> s to Evaluator.evaluate(s, summaries, params) }
    }
}
