// Sample variants pick hand-chosen synthetic offsets and counts.
@file:Suppress("MagicNumber")

package io.github.alien2003.nightbaseline.ui.metric

import io.github.alien2003.nightbaseline.algo.DefaultMetrics
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.algo.Sensitivity
import io.github.alien2003.nightbaseline.ui.model.ChartPeriod
import io.github.alien2003.nightbaseline.ui.model.ChartPointUi
import io.github.alien2003.nightbaseline.ui.model.MetricSeriesUi
import io.github.alien2003.nightbaseline.ui.model.MetricState
import io.github.alien2003.nightbaseline.ui.model.MetricUi
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.model.RangeUi
import io.github.alien2003.nightbaseline.ui.sample.SampleData

/** Metric detail states of `states.md` 4, 5 and 7.1 that the screenshot tests and previews render. */
enum class MetricSample {
    /** `S-ALL`, header `M-WITHIN`. */
    Within,

    /** `S-DEV`, header `M-HIGH`, newest night selected (`D-SELECTED`). */
    Higher,

    /** `M-LOW` (SpO2 lower than usual, 90 nights). */
    Lower,

    /** `M-SIDE-IGNORED`: SpO2 above the range, which does not count. */
    SideIgnored,

    /** Sleep duration within its range (hour ticks). */
    Duration,

    /** Very variable sleep duration: the range starts at the 3 h short-night limit and spans more than 6 h. */
    DurationWide,

    /** `L0`: learning with no night yet. */
    LearningStart,

    /** `L1`..`L6` / `M-LEARN`: 4 of 7 nights, hollow points, no band, `D-PERIOD-SHORT`. */
    Learning,

    /** `M-NODATA` / `E-NODATA`: no value last night, a gap at the end of the chart. */
    NoData,

    /** `M-FEW` / `E-NOTEVAL`: too few readings. */
    TooFew,

    /** `M-COVERAGE`: partial night. */
    Partial,

    /** `M-NOTCOMPARED` / `E-SHORT`: short night, hollow point. */
    ShortNight,

    /** `M-NOTPROVIDED` with nights stored from an earlier source. */
    NotProvided,

    /** `M-NOTPROVIDED` with nothing stored: a state card replaces the chart. */
    NotProvidedNone,

    /** `M-PERM`: access to this metric's record type is off. */
    MetricAccessOff,

    /** `E-PERM`: all access off; card above the stored chart. */
    AccessOff,

    /** `E-SOURCE-GONE`. */
    SourceGone,

    /** `E-SYNC`: waiting for the source to sync. */
    WaitingSync,

    /** `E-HC-UPDATE`. */
    HealthConnectUpdate,

    /** `D-PERIOD-SHORT`: 30 nights asked, 19 stored. */
    PeriodShort,

    /** `D-BAND-CHANGED`: the range shifted within the period, a night selected. */
    BandChanged,

    /** `LOADING`. */
    Loading,

    /** The range explainer open over `Higher`. */
    Explainer,
}

/** Builds [MetricDetailState] from the synthetic [SampleData] (never Health Connect data). */
object MetricDetailSamples {
    private const val LAST_SYNC = "07:42"
    private val HR = MetricId.SLEEPING_HR
    private const val QUARTER = 90
    private const val WIDE_LOW = 180.0
    private const val WIDE_HIGH = 690.0
    private const val WIDE_MEDIAN = 420.0
    private val WIDE_VALUES = listOf(300.0, 420.0, 560.0, 250.0, 480.0, 650.0, 390.0)

    /** The state of [sample]. */
    @Suppress("CyclomaticComplexMethod", "LongMethod") // One line per state of the catalogue.
    fun state(sample: MetricSample): MetricDetailState =
        when (sample) {
            MetricSample.Within -> {
                base(SampleData.night(NightState.AllWithin).metrics.first { it.metric == HR })
            }

            MetricSample.Higher -> {
                val metric = SampleData.lastNight.metrics.first { it.metric == HR }
                base(metric).let { it.copy(selected = it.shownPoints.lastIndex) }
            }

            MetricSample.Lower -> {
                base(SampleData.lastNight.metrics.first { it.metric == MetricId.SPO2 }, period = ChartPeriod.Quarter)
            }

            MetricSample.SideIgnored -> {
                base(SampleData.metric(MetricId.SPO2, MetricState.SideIgnoredHigher))
            }

            MetricSample.Duration -> {
                base(SampleData.metric(MetricId.SLEEP_DURATION, MetricState.Within))
            }

            MetricSample.DurationWide -> {
                val wide = RangeUi(WIDE_LOW, WIDE_HIGH, WIDE_MEDIAN, nights = 28)
                val series = SampleData.series(MetricId.SLEEP_DURATION, QUARTER)
                val points =
                    series.points.mapIndexed { i, p ->
                        // The last night matches the header value.
                        val value = if (i == series.points.lastIndex) WIDE_MEDIAN else WIDE_VALUES[i % WIDE_VALUES.size]
                        p.copy(range = p.range?.let { wide }, value = p.value?.let { value })
                    }
                base(
                    SampleData.metric(MetricId.SLEEP_DURATION, MetricState.Within).copy(value = WIDE_MEDIAN, range = wide),
                    series.copy(points = points),
                )
            }

            MetricSample.LearningStart -> {
                base(MetricUi(HR, MetricState.Learning, learnedNights = 0), series = null, period = ChartPeriod.Week)
            }

            MetricSample.Learning -> {
                val series = SampleData.learningSeries(HR, 4)
                base(
                    SampleData.metric(HR, MetricState.Learning).copy(value = series.points.last().value, learnedNights = 4),
                    series = series,
                    period = ChartPeriod.Week,
                )
            }

            MetricSample.NoData -> {
                withLast(SampleData.metric(HR, MetricState.NoData))
            }

            MetricSample.TooFew -> {
                withLast(SampleData.metric(HR, MetricState.TooFew).copy(minSamples = 30))
            }

            MetricSample.Partial -> {
                withLast(SampleData.metric(MetricId.SPO2, MetricState.LowCoverage).copy(minCoverage = 0.5))
            }

            MetricSample.ShortNight -> {
                withLast(SampleData.metric(HR, MetricState.NotCompared))
            }

            MetricSample.NotProvided -> {
                val full = SampleData.series(MetricId.HRV, QUARTER)
                val cut = full.points.size - 21
                val series =
                    full.copy(
                        points =
                            full.points.mapIndexed { i, p ->
                                if (i >= cut) ChartPointUi(p.date, null, null, MetricState.NotProvided) else p
                            },
                    )
                base(
                    SampleData.metric(MetricId.HRV, MetricState.NotProvided),
                    series = series,
                    period = ChartPeriod.Quarter,
                ).copy(notProvidedSince = full.points[cut - 1].date)
            }

            MetricSample.NotProvidedNone -> {
                base(SampleData.metric(MetricId.SKIN_TEMPERATURE, MetricState.NotProvided), series = null)
            }

            MetricSample.MetricAccessOff -> {
                withLast(SampleData.metric(MetricId.RESPIRATORY_RATE, MetricState.AccessOff))
            }

            MetricSample.AccessOff -> {
                base(SampleData.night(NightState.AllWithin).metrics.first { it.metric == HR }).copy(notice = MetricNotice.AccessOff)
            }

            MetricSample.SourceGone -> {
                base(SampleData.night(NightState.AllWithin).metrics.first { it.metric == HR }).copy(notice = MetricNotice.SourceGone)
            }

            MetricSample.WaitingSync -> {
                base(SampleData.night(NightState.AllWithin).metrics.first { it.metric == HR }).copy(notice = MetricNotice.WaitingSync)
            }

            MetricSample.HealthConnectUpdate -> {
                base(SampleData.night(NightState.AllWithin).metrics.first { it.metric == HR })
                    .copy(notice = MetricNotice.HealthConnectUpdate)
            }

            MetricSample.PeriodShort -> {
                base(SampleData.night(NightState.AllWithin).metrics.first { it.metric == HR }).copy(storedNights = 19)
            }

            MetricSample.BandChanged -> {
                val metric = SampleData.metric(MetricId.RESPIRATORY_RATE, MetricState.Within)
                val series = shifted(SampleData.series(MetricId.RESPIRATORY_RATE, QUARTER), nights = 10)
                val last = series.points.last()
                base(metric.copy(state = last.state, value = last.value, range = last.range), series = series).let {
                    it.copy(selected = it.shownPoints.lastIndex - 3)
                }
            }

            MetricSample.Loading -> {
                base(SampleData.metric(HR, MetricState.Within)).copy(loading = true)
            }

            MetricSample.Explainer -> {
                state(MetricSample.Higher).copy(explainerOpen = true, selected = null)
            }
        }

    private fun base(
        metric: MetricUi,
        series: MetricSeriesUi? = SampleData.series(metric.metric, QUARTER),
        period: ChartPeriod = ChartPeriod.Month,
    ): MetricDetailState =
        MetricDetailState(
            metric = metric,
            anchorDate = SampleData.LAST_WAKE,
            series = series,
            source = SampleData.SOURCE,
            period = period,
            countedSide = DefaultMetrics.ALL.firstOrNull { it.id == metric.metric }?.sensitivity ?: Sensitivity.BOTH,
            lastSync = LAST_SYNC,
        )

    /** [metric] on last night, with the chart's last point following it (gap or hollow point). */
    private fun withLast(metric: MetricUi): MetricDetailState {
        val series = SampleData.series(metric.metric, QUARTER)
        val points = series.points.dropLast(1) + series.points.last().copy(value = metric.value, state = metric.state)
        return base(metric, series.copy(points = points))
    }

    /** Moves the range (and the values) of the last [nights] nights up by one band width, as after a source change. */
    private fun shifted(
        series: MetricSeriesUi,
        nights: Int,
    ): MetricSeriesUi {
        val from = series.points.size - nights
        val width =
            series.points
                .last { it.range != null }
                .range!!
                .let { it.high - it.low }
        return series.copy(
            points =
                series.points.mapIndexed { i, p ->
                    if (i < from) {
                        p
                    } else {
                        p.copy(
                            value = p.value?.plus(width * 0.8),
                            range =
                                p.range?.let { r ->
                                    r.copy(
                                        low = r.low + width * 0.8,
                                        high = r.high + width * 0.8,
                                        median =
                                            r.median + width * 0.8,
                                    )
                                },
                        )
                    }
                },
        )
    }
}
