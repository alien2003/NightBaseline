// Synthetic sample states: hand-picked counts and offsets on top of SampleData (no Health Connect data).
@file:Suppress("MagicNumber")

package io.github.alien2003.nightbaseline.ui.home

import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.ui.model.BannerKind
import io.github.alien2003.nightbaseline.ui.model.ChartPeriod
import io.github.alien2003.nightbaseline.ui.model.MetricState
import io.github.alien2003.nightbaseline.ui.model.MetricUi
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.model.NightUi
import io.github.alien2003.nightbaseline.ui.sample.SampleData

/** Every Home state of `states.md` 1-5 that the screenshot tests and previews render; [id] names the files. */
enum class HomeSample(
    /** File-name id (the `states.md` id in lower case). */
    val id: String,
) {
    Loading("loading"),
    L0("l0"),
    L1("l1"),
    L2("l2"),
    L3("l3"),
    L3Short("l3-short"),
    L4("l4"),
    L5("l5"),
    L6("l6"),
    AllWithin("s-all"),
    AllWithinPartial("s-all-not-provided"),
    SomeOne("s-some"),
    SomeMany("s-some-many"),
    Deviations("s-dev"),
    Short("e-short"),
    Ambiguous("e-ambiguous"),
    NotEvaluated("e-noteval"),
    NoData("e-nodata"),
    Sync("e-sync"),
    Permission("e-perm"),
    HcNone("e-hc-none"),
    HcUpdate("e-hc-update"),
    SourceGone("e-source-gone"),
    BannerWait("b-wait"),
    BannerBackground("b-bg"),
    BannerReadFailed("b-read-failed"),
    BannerSourceQuiet("b-source-quiet"),
    BannerNotificationsOff("b-notif-off"),
    BannerRangeReady("b-range-ready"),
}

/**
 * Builders of [HomeState] from the synthetic [SampleData] for every [HomeSample]. Variants the generator does not
 * produce are derived from generated nights by changing states (still synthetic).
 */
object HomeSamples {
    /** Home in [sample]. */
    @Suppress("CyclomaticComplexMethod") // One line per state of states.md; splitting it would hide the table.
    fun state(sample: HomeSample): HomeState =
        when (sample) {
            HomeSample.Loading -> base(problem(NightState.Loading), recent = emptyList())
            HomeSample.L0 -> learning(0)
            HomeSample.L1 -> learning(1)
            HomeSample.L2 -> learning(2)
            HomeSample.L3 -> learning(3)
            HomeSample.L3Short -> learning(3, short = true)
            HomeSample.L4 -> learning(4)
            HomeSample.L5 -> learning(5)
            HomeSample.L6 -> learning(6)
            HomeSample.AllWithin -> base(SampleData.night(NightState.AllWithin))
            HomeSample.AllWithinPartial -> base(partial())
            HomeSample.SomeOne -> base(someMixed())
            HomeSample.SomeMany -> base(someMany())
            HomeSample.Deviations -> base(deviations())
            HomeSample.Short -> base(SampleData.night(NightState.Short))
            HomeSample.Ambiguous -> base(problem(NightState.Ambiguous))
            HomeSample.NotEvaluated -> base(notEvaluated())
            HomeSample.NoData -> base(problem(NightState.NoData))
            HomeSample.Sync -> withLastResult(NightState.NotSynced)
            HomeSample.Permission -> withLastResult(NightState.Permission)
            HomeSample.HcNone -> base(problem(NightState.HealthConnectNone), recent = emptyList())
            HomeSample.HcUpdate -> withLastResult(NightState.HealthConnectUpdate)
            HomeSample.SourceGone -> withLastResult(NightState.SourceGone)
            HomeSample.BannerWait -> base(SampleData.night(NightState.AllWithin), BannerKind.Wait)
            HomeSample.BannerBackground -> base(SampleData.night(NightState.AllWithin), BannerKind.Background)
            HomeSample.BannerReadFailed -> base(SampleData.night(NightState.AllWithin), BannerKind.ReadFailed)
            HomeSample.BannerSourceQuiet -> base(SampleData.night(NightState.AllWithin), BannerKind.SourceQuiet).copy(quietDays = 4)
            HomeSample.BannerNotificationsOff -> base(deviations(), BannerKind.NotificationsOff)
            HomeSample.BannerRangeReady -> base(SampleData.night(NightState.AllWithin), BannerKind.RangeReady)
        }

    /** Home on [night] with the last 14 sample nights and 90-night series of its metrics. */
    fun base(
        night: NightUi,
        banner: BannerKind? = null,
        recent: List<NightUi> = SampleData.recent(STRIP_WIDE),
    ): HomeState =
        HomeState(
            night = night,
            recent = recent,
            source = SampleData.SOURCE,
            banner = banner,
            series = night.metrics.associate { it.metric to SampleData.series(it.metric, ChartPeriod.Quarter.nights) },
        )

    /** `S-ALL` where SpO2 is not provided (collapsed core tile), HRV is still learning and skin temperature is listed. */
    fun partial(): NightUi {
        val night = SampleData.night(NightState.AllWithin)
        return night.copy(
            metrics =
                night.metrics
                    .filter { it.metric != MetricId.SKIN_TEMPERATURE }
                    .map {
                        when (it.metric) {
                            MetricId.SPO2 -> metric(it.metric, MetricState.NotProvided)
                            MetricId.HRV -> metric(it.metric, MetricState.Learning).copy(learnedNights = 3)
                            else -> it
                        }
                    },
            notRecorded = listOf(MetricId.SKIN_TEMPERATURE),
        )
    }

    /** `S-SOME` with one moved metric and the other metric states on the same night (access off, side ignored, gaps). */
    fun someMixed(): NightUi {
        val night = SampleData.night(NightState.SomeMoved)
        val states =
            mapOf(
                MetricId.SLEEPING_HR to MetricState.Higher,
                MetricId.RESPIRATORY_RATE to MetricState.AccessOff,
                MetricId.SPO2 to MetricState.SideIgnoredHigher,
                MetricId.SLEEP_DURATION to MetricState.Within,
                MetricId.HRV to MetricState.TooFew,
                MetricId.SKIN_TEMPERATURE to MetricState.LowCoverage,
            )
        return night.copy(metrics = night.metrics.map { m -> states[m.metric]?.let { metric(m.metric, it) } ?: m }, deviating = 1)
    }

    /** `S-SOME` with two moved metrics under the "Fewer notes" threshold of 3. */
    fun someMany(): NightUi =
        withStates(
            SampleData.night(NightState.SomeMoved),
            mapOf(MetricId.SLEEPING_HR to MetricState.Higher, MetricId.HRV to MetricState.Lower),
        ).copy(threshold = 3)

    /** `S-DEV` with three moved metrics, one of them lower. */
    fun deviations(): NightUi =
        withStates(
            SampleData.night(NightState.Moved),
            mapOf(
                MetricId.SLEEPING_HR to MetricState.Higher,
                MetricId.RESPIRATORY_RATE to MetricState.Higher,
                MetricId.HRV to MetricState.Lower,
            ),
        )

    /** `E-NOTEVAL`: the session exists, but every measurement lacks readings. */
    fun notEvaluated(): NightUi {
        val night = problem(NightState.NotEvaluated)
        val states =
            mapOf(
                MetricId.SLEEPING_HR to MetricState.TooFew,
                MetricId.RESPIRATORY_RATE to MetricState.NoData,
                MetricId.SPO2 to MetricState.LowCoverage,
                MetricId.SLEEP_DURATION to MetricState.NotCompared,
                MetricId.HRV to MetricState.NoData,
                MetricId.SKIN_TEMPERATURE to MetricState.NoData,
            )
        return night.copy(metrics = SampleData.metrics().map { m -> metric(m.metric, states.getValue(m.metric)) })
    }

    /** A night in a problem [state] for the newest wake date (no result). */
    fun problem(state: NightState): NightUi = SampleData.night(state).copy(deviating = 0)

    /** `L0` (n = 0) or `L1`..`L6`: values without ranges; the strip shows the counted (and the short) nights. */
    fun learning(
        nights: Int,
        short: Boolean = false,
    ): HomeState {
        val night =
            SampleData
                .night(if (nights == 0) NightState.LearningStart else NightState.Learning)
                .let { n -> n.copy(historyNights = nights, metrics = n.metrics.map { it.copy(learnedNights = nights) }) }
        val recent =
            SampleData.recent(nights + if (short) 1 else 0).mapIndexed { i, n ->
                val isShort = short && i == nights
                n.copy(state = if (isShort) NightState.Short else NightState.Learning, deviating = 0)
            }
        val series = night.metrics.associate { it.metric to SampleData.learningSeries(it.metric, nights.coerceAtLeast(1)) }
        return HomeState(
            night = night,
            recent = if (nights == 0) emptyList() else recent,
            source = SampleData.SOURCE,
            series = series,
            lastNightShort = short,
        )
    }

    /** A data-access or sync state for the night after the newest sample night, with that night as "Last result". */
    private fun withLastResult(state: NightState): HomeState {
        val last = deviations()
        val night = last.copy(state = state, wakeDate = last.wakeDate.plusDays(1), startDate = last.startDate.plusDays(1), deviating = 0)
        return base(last).copy(night = night, lastResult = last)
    }

    private fun withStates(
        night: NightUi,
        states: Map<MetricId, MetricState>,
    ): NightUi {
        val metrics: List<MetricUi> =
            night.metrics.map { m -> metric(m.metric, states[m.metric] ?: MetricState.Within) }
        return night.copy(metrics = metrics, deviating = metrics.count { it.state.moved })
    }

    /** [SampleData.metric] with the thresholds the missing-reason sentences quote (`AlgoParams`-like values). */
    private fun metric(
        id: MetricId,
        state: MetricState,
    ): MetricUi =
        SampleData.metric(id, state).let {
            when (state) {
                MetricState.TooFew -> it.copy(minSamples = MIN_SAMPLES)
                MetricState.LowCoverage -> it.copy(minCoverage = MIN_COVERAGE)
                else -> it
            }
        }

    private const val MIN_SAMPLES = 20
    private const val MIN_COVERAGE = 0.6
}
