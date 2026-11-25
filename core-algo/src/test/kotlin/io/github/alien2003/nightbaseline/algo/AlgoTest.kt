package io.github.alien2003.nightbaseline.algo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatsTest {
    @Test
    fun `median quantile and mad`() {
        assertEquals(2.0, Stats.median(listOf(3.0, 1.0, 2.0)))
        assertEquals(2.5, Stats.median(listOf(4.0, 1.0, 2.0, 3.0)))
        assertEquals(1.75, Stats.quantile(listOf(1.0, 2.0, 3.0, 4.0), 0.25))
        assertEquals(1.0, Stats.mad(listOf(1.0, 2.0, 3.0, 4.0, 100.0)))
        assertTrue(Stats.median(emptyList()).isNaN())
    }
}

class NightSelectorTest {
    private val off = ZoneOffset.ofHours(3).totalSeconds
    private val p = AlgoParams()

    private fun at(date: String, h: Int, m: Int = 0): Long =
        LocalDate.parse(date).atTime(h, m).toInstant(ZoneOffset.ofTotalSeconds(off)).toEpochMilli()

    @Test
    fun `split night is merged and nap is dropped`() {
        val sessions =
            listOf(
                SleepSession("a", at("2026-01-01", 23), at("2026-01-02", 3), off),
                SleepSession("b", at("2026-01-02", 3, 40), at("2026-01-02", 7), off),
                SleepSession("nap", at("2026-01-02", 15), at("2026-01-02", 16), off),
            )
        val nights = NightSelector.select(sessions, p)
        assertEquals(1, nights.size)
        assertEquals("2026-01-02", nights[0].date)
        assertEquals(listOf("a", "b"), nights[0].sessions.map { it.id })
    }

    @Test
    fun `longest qualifying cluster wins and late wake-ups count`() {
        val sessions =
            listOf(
                SleepSession("early", at("2026-01-02", 3), at("2026-01-02", 4), off),
                SleepSession("main", at("2026-01-02", 6), at("2026-01-02", 13), off),
            )
        val nights = NightSelector.select(sessions, p)
        assertEquals(listOf("main"), nights.single().sessions.map { it.id })
    }
}

class ExtractionTest {
    private val p = AlgoParams()
    private val start = 0L
    private val end = 8 * 3_600_000L

    private fun night(channels: Map<ChannelId, List<Sample>>) = NightInput("2026-01-02", listOf(SleepSession("s", start, end, 0)), channels)

    @Test
    fun `missing reasons are distinguished`() {
        val hr = (0 until 240).map { Sample(it * 120_000L, 55.0) }
        val n = night(mapOf(ChannelId.HEART_RATE to hr))
        val s = MetricExtractor.summarize(n, setOf(ChannelId.HEART_RATE, ChannelId.SPO2), p)
        assertEquals(55.0, s.values.getValue(MetricId.SLEEPING_HR).value)
        assertEquals(MissingReason.NO_DATA, s.values.getValue(MetricId.SPO2).missing)
        assertEquals(MissingReason.NOT_PROVIDED, s.values.getValue(MetricId.RESPIRATORY_RATE).missing)
        assertEquals(480.0, s.values.getValue(MetricId.SLEEP_DURATION).value)
    }

    @Test
    fun `low coverage and implausible samples are rejected`() {
        val firstHourOnly = (0 until 60).map { Sample(it * 60_000L, 55.0) }
        val s = MetricExtractor.summarize(night(mapOf(ChannelId.HEART_RATE to firstHourOnly)), setOf(ChannelId.HEART_RATE), p)
        assertEquals(MissingReason.LOW_COVERAGE, s.values.getValue(MetricId.SLEEPING_HR).missing)
        val junk = (0 until 240).map { Sample(it * 120_000L, 0.0) }
        val s2 = MetricExtractor.summarize(night(mapOf(ChannelId.HEART_RATE to junk)), setOf(ChannelId.HEART_RATE), p)
        assertEquals(MissingReason.NO_DATA, s2.values.getValue(MetricId.SLEEPING_HR).missing)
    }

    @Test
    fun `staged sessions count only sleep`() {
        val stages = listOf(StageInterval(0, 3_600_000, StageKind.AWAKE), StageInterval(3_600_000, end, StageKind.ASLEEP))
        val n = NightInput("2026-01-02", listOf(SleepSession("s", start, end, 0, stages)), emptyMap())
        assertEquals(420.0, n.asleepMinutes)
    }
}

class EvaluatorTest {
    private val p = AlgoParams()

    private fun summary(date: LocalDate, hr: Double?, rr: Double?, spo2: Double?, minutes: Double = 450.0) =
        NightSummary(
            date.toString(), 0, 0, minutes, minutes < p.minNightMinutes,
            mapOf(
                MetricId.SLEEPING_HR to MetricValue(MetricId.SLEEPING_HR, hr, 200, 1.0, if (hr == null) MissingReason.NO_DATA else null),
                MetricId.RESPIRATORY_RATE to MetricValue(MetricId.RESPIRATORY_RATE, rr, 50, 1.0, if (rr == null) MissingReason.NO_DATA else null),
                MetricId.SPO2 to MetricValue(MetricId.SPO2, spo2, 16, 1.0, if (spo2 == null) MissingReason.NO_DATA else null),
                MetricId.SLEEP_DURATION to MetricValue(MetricId.SLEEP_DURATION, minutes, 0, 1.0),
                MetricId.HRV to MetricValue(MetricId.HRV, null, 0, 0.0, MissingReason.NOT_PROVIDED),
                MetricId.SKIN_TEMPERATURE to MetricValue(MetricId.SKIN_TEMPERATURE, null, 0, 0.0, MissingReason.NOT_PROVIDED),
            ),
        )

    private val d0 = LocalDate.of(2026, 1, 1)
    private val history = (0 until 20).map { summary(d0.plusDays(it.toLong()), 55.0 + (it % 3), 14.0 + (it % 2) * 0.4, 96.0 + (it % 2)) }

    @Test
    fun `learning until min nights`() {
        val e = Evaluator.evaluate(history[5], history, p)
        assertEquals(NightStatus.LEARNING, e.status)
        assertEquals(5, e.historyNights)
        assertTrue(!e.notify)
    }

    @Test
    fun `two deviating metrics notify, one does not`() {
        val day = d0.plusDays(20)
        val both = Evaluator.evaluate(summary(day, 64.0, 17.0, 96.0), history, p)
        assertEquals(NightStatus.DEVIATIONS, both.status)
        assertEquals(2, both.deviating)
        assertTrue(both.notify)
        val one = Evaluator.evaluate(summary(day, 64.0, 14.2, 96.0), history, p)
        assertEquals(NightStatus.SOME_DEVIATIONS, one.status)
        val none = Evaluator.evaluate(summary(day, 56.0, 14.2, 96.5), history, p)
        assertEquals(NightStatus.ALL_WITHIN, none.status)
        assertEquals(4, none.metrics.size, "metrics not provided by the source are omitted")
    }

    @Test
    fun `high spo2 is shown but never counts`() {
        val e = Evaluator.evaluate(summary(d0.plusDays(20), 64.0, 14.2, 100.0), history, p)
        val spo2 = e.metrics.first { it.metric == MetricId.SPO2 }
        assertTrue(!spo2.deviates)
        assertEquals(NightStatus.SOME_DEVIATIONS, e.status)
    }

    @Test
    fun `short night is not evaluated and not used`() {
        val e = Evaluator.evaluate(summary(d0.plusDays(20), 70.0, 18.0, 90.0, minutes = 120.0), history, p)
        assertEquals(NightStatus.SHORT_NIGHT, e.status)
        assertEquals(0, e.deviating)
    }

    @Test
    fun `missing metric does not break the night`() {
        val e = Evaluator.evaluate(summary(d0.plusDays(20), 56.0, null, 96.0), history, p)
        assertEquals(MetricStatus.MISSING, e.metrics.first { it.metric == MetricId.RESPIRATORY_RATE }.status)
        assertEquals(NightStatus.ALL_WITHIN, e.status)
    }

    @Test
    fun `constant history uses the spread floor`() {
        val flat = (0 until 20).map { summary(d0.plusDays(it.toLong()), 55.0, 14.0, 96.0) }
        val b = Evaluator.baseline(DefaultMetrics.SLEEPING_HR, d0.plusDays(20).toString(), flat, p)!!
        assertEquals(1.0, b.spread)
        assertEquals(52.0, b.low)
        assertEquals(58.0, b.high, "minAbsDeviation widens the band beyond z·spread")
    }

    @Test
    fun `defaults are valid`() {
        assertEquals(emptyList<String>(), p.validate())
        assertTrue(p.copy(minNights = 100).validate().isNotEmpty())
    }

    @Test
    fun `future and too old nights are ignored`() {
        val b = Evaluator.baseline(DefaultMetrics.SLEEPING_HR, d0.plusDays(3).toString(), history, p)
        assertNull(b)
    }
}

/**
 * Behaviour on synthetic people with known episodes. This is the check the defaults must pass; it is not a fit to
 * anybody's real data.
 */
class SyntheticBehaviourTest {
    @Test
    fun `few false alarms and most episodes found`() {
        var normalNights = 0
        var falseAlarms = 0
        var episodes = 0
        var found = 0
        for (seed in 1L..200L) {
            val start = 35 + (seed % 20).toInt()
            val gen = SyntheticNights(SyntheticNights.Config(seed = seed, nights = 90, episodes = mapOf(start to 3)))
            val nights = gen.generate()
            val (sessions, channels) = gen.flatten(nights)
            val results = Pipeline.run(sessions, channels).associateBy { it.first.date }
            val truth = nights.associateBy { it.wakeDate }
            results.values.forEach { (_, e) ->
                val t = truth.getValue(e.date)
                if (!t.inEpisode && e.status != NightStatus.LEARNING && e.status != NightStatus.SHORT_NIGHT) {
                    normalNights++
                    if (e.notify) falseAlarms++
                }
            }
            episodes++
            val episodeDates = nights.filter { it.inEpisode }.map { it.wakeDate }
            if (episodeDates.any { results[it]?.second?.notify == true }) found++
        }
        val falseRate = falseAlarms.toDouble() / normalNights
        val detection = found.toDouble() / episodes
        println("synthetic: normal nights=$normalNights false alarms=$falseAlarms (${"%.4f".format(falseRate)}), episodes found $found/$episodes")
        assertTrue(falseRate < 0.02, "false-alarm rate per normal night $falseRate")
        assertTrue(detection > 0.9, "episode detection $detection")
    }

    @Test
    fun `pipeline is deterministic`() {
        val gen = SyntheticNights(SyntheticNights.Config(seed = 7))
        val (s, c) = gen.flatten(gen.generate())
        assertEquals(Pipeline.run(s, c), Pipeline.run(s, c))
    }
}
