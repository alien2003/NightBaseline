package io.github.alien2003.nightbaseline.ui.metric

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.alien2003.nightbaseline.algo.AlgoParams
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.algo.Sensitivity
import io.github.alien2003.nightbaseline.app.AccessStatus
import io.github.alien2003.nightbaseline.app.AppData
import io.github.alien2003.nightbaseline.app.NightPresenter
import io.github.alien2003.nightbaseline.data.local.NightRecord
import io.github.alien2003.nightbaseline.data.local.RunLogLine
import io.github.alien2003.nightbaseline.data.local.settings.AppSettings
import io.github.alien2003.nightbaseline.ui.model.ChartPeriod
import io.github.alien2003.nightbaseline.ui.model.MetricState
import io.github.alien2003.nightbaseline.ui.model.MetricUi
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.settings.SourceNames
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Metric detail (`metric/{metricId}?date`): the metric on the anchor night (default: the newest stored night), the
 * series of the last 90 calendar nights with the range each night had (recomputed from the stored summaries with the
 * current sensitivity), the counted side, the screen-level notice and the last sync. The period is saved per screen
 * in the [SavedStateHandle] (`layouts.md` 8.1); the chart selection and the explainer belong to the route.
 */
@HiltViewModel
class MetricDetailViewModel
    @Inject
    constructor(
        private val handle: SavedStateHandle,
        private val data: AppData,
    ) : ViewModel() {
        private val metric = MetricId(handle.get<String>(KEY_METRIC) ?: MetricId.SLEEPING_HR.key)
        private val anchor = handle.get<String>(KEY_DATE)?.let(LocalDate::parse)
        private val period = MutableStateFlow(handle.get<String>(KEY_PERIOD)?.let { p -> ChartPeriod.entries.firstOrNull { it.name == p } })

        /** The screen state without the route's selection and explainer; null until the first read. */
        val ui: StateFlow<MetricDetailState?> =
            combine(period, data.settings, data.access.status, data.allNights, data.recentRuns) { p, settings, access, all, runs ->
                build(p, settings, access, all, runs)
            }.flowOn(data.compute)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

        /** A period segment was chosen (saved). */
        fun setPeriod(value: ChartPeriod) {
            period.value = value
            handle[KEY_PERIOD] = value.name
        }

        @Suppress("LongParameterList")
        private fun build(
            chosen: ChartPeriod?,
            settings: AppSettings,
            access: AccessStatus?,
            all: List<NightRecord>,
            runs: List<RunLogLine>,
        ): MetricDetailState = build(metric, anchor, chosen, settings, access, all, runs, data.now(), data.zoneId(), data.lastRead(runs))

        /** Saved-state keys (the route's argument names, plus the period). */
        companion object {
            /** `MetricRoute.metric`. */
            const val KEY_METRIC: String = "metric"

            /** `MetricRoute.date`. */
            const val KEY_DATE: String = "date"

            /** The chosen period. */
            const val KEY_PERIOD: String = "period"

            private const val STOP_TIMEOUT_MS = 5_000L

            /** The pure mapping (shared with the two-pane Home detail pane and the tests). */
            @Suppress("LongParameterList")
            fun build(
                metric: MetricId,
                anchorDate: LocalDate?,
                chosen: ChartPeriod?,
                settings: AppSettings,
                access: AccessStatus?,
                all: List<NightRecord>,
                runs: List<RunLogLine>,
                now: Instant,
                zone: ZoneId,
                lastSync: String?,
            ): MetricDetailState {
                val params = settings.algoParams()
                val records = NightPresenter.mergeSources(all, settings.sourcePackage)
                val nights = NightPresenter.nights(records, params, zone, access)
                val anchorNight = anchorDate?.let { d -> nights.firstOrNull { it.wakeDate == d } } ?: nights.firstOrNull()
                val anchor = anchorNight?.wakeDate ?: anchorDate ?: NightPresenter.expectedWakeDate(now, zone)
                val metricUi = anchorNight?.metrics?.firstOrNull { it.metric == metric } ?: MetricUi(metric, MetricState.NotProvided)
                val series =
                    NightPresenter.series(metric, records, anchor, params, settings.sourcePackage).takeIf { s ->
                        s.points.any {
                            it.value !=
                                null
                        }
                    }
                val stored = series?.let(NightPresenter::storedNights) ?: 0
                val period = chosen ?: if (stored < ChartPeriod.Month.nights) ChartPeriod.Week else ChartPeriod.Month
                val home = NightPresenter.resolveHome(now, zone, settings, access, nights, false, runs)
                return MetricDetailState(
                    metric = metricUi,
                    anchorDate = anchor,
                    series = series,
                    source = SourceNames.display(access?.sourceLabel, settings.sourcePackage),
                    period = period,
                    countedSide = countedSide(metric, params),
                    notice = noticeOf(home.night.state),
                    lastSync = lastSync,
                    notProvidedSince =
                        series
                            ?.takeIf { metricUi.state == MetricState.NotProvided }
                            ?.points
                            ?.lastOrNull { it.value != null }
                            ?.date,
                    storedNights = stored,
                )
            }

            private fun countedSide(
                metric: MetricId,
                params: AlgoParams,
            ): Sensitivity = params.metrics.firstOrNull { it.id == metric }?.sensitivity ?: Sensitivity.BOTH

            private fun noticeOf(state: NightState): MetricNotice? =
                when (state) {
                    NightState.NotSynced -> MetricNotice.WaitingSync
                    NightState.SourceGone -> MetricNotice.SourceGone
                    NightState.Permission -> MetricNotice.AccessOff
                    NightState.HealthConnectUpdate -> MetricNotice.HealthConnectUpdate
                    else -> null
                }
        }
    }
