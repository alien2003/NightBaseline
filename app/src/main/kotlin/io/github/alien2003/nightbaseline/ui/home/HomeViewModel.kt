package io.github.alien2003.nightbaseline.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.alien2003.nightbaseline.app.AccessStatus
import io.github.alien2003.nightbaseline.app.AppData
import io.github.alien2003.nightbaseline.app.ManualCheck
import io.github.alien2003.nightbaseline.app.NightPresenter
import io.github.alien2003.nightbaseline.data.local.NightRecord
import io.github.alien2003.nightbaseline.data.local.RunLogLine
import io.github.alien2003.nightbaseline.data.local.settings.AppSettings
import io.github.alien2003.nightbaseline.ui.learning.LearningState
import io.github.alien2003.nightbaseline.ui.model.BannerKind
import io.github.alien2003.nightbaseline.ui.model.ManualCheckUi
import io.github.alien2003.nightbaseline.ui.settings.SourceNames
import io.github.alien2003.nightbaseline.ui.shell.PerfProbe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject

/** What the Home route shows. */
sealed interface HomeUi {
    /** Nothing loaded yet (the first database read; never shown as a skeleton, it takes milliseconds). */
    data object Pending : HomeUi

    /** Home with a result, a state card or `LOADING`. */
    data class Ready(
        /** The screen state. */
        val state: HomeState,
    ) : HomeUi

    /** The learning period presentation (`screens.md` 7.6). */
    data class Learning(
        /** The screen state. */
        val state: LearningState,
    ) : HomeUi
}

/**
 * Home ("Last night"): the newest stored night of the configured source, the night state and banner of `states.md`
 * 1 and 3, the last 7 nights and the chart series of the detail pane. Selection, period and scroll are UI state of
 * the route (saved there), so a fold or resize never reloads anything here.
 */
@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val data: AppData,
    ) : ViewModel() {
        /** The screen state. */
        @OptIn(ExperimentalCoroutinesApi::class)
        val ui: StateFlow<HomeUi> =
            combine(
                data.settings,
                data.access.status,
                data.currentNights,
                combine(data.checks.running(), data.manualCheck, data.healthConnectStillMissing, ::Triple),
                data.recentRuns,
            ) { settings, access, records, (running, check, hcMissing), runs ->
                Inputs(settings, access, records, running, runs, check, hcMissing)
            }.mapLatest(::build)
                .flowOn(data.compute)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUi.Pending)

        private data class Inputs(
            val settings: AppSettings,
            val access: AccessStatus?,
            val records: List<NightRecord>,
            val running: Boolean,
            val runs: List<RunLogLine>,
            val check: ManualCheck,
            val healthConnectStillMissing: Boolean = false,
        )

        private fun build(inputs: Inputs): HomeUi {
            PerfProbe.hit(MAP_PROBE)
            val settings = inputs.settings
            val access = inputs.access
            val records = inputs.records
            val params = settings.algoParams()
            val zone = data.zoneId()
            val nights = NightPresenter.nights(records, params, zone, access)
            val r = NightPresenter.resolveHome(data.now(), zone, settings, access, nights, inputs.running, inputs.runs)
            val source = SourceNames.display(access?.sourceLabel, settings.sourcePackage)
            val recent = nights.take(HomeState.STRIP).reversed()
            if (r.learning) return HomeUi.Learning(LearningState(r.night, recent, source, r.lastNightShort))
            val metricsNight = r.lastResult ?: r.night
            val series =
                metricsNight.metrics.associate { m ->
                    m.metric to NightPresenter.series(m.metric, records, metricsNight.wakeDate, params, settings.sourcePackage)
                }
            return HomeUi.Ready(
                HomeState(
                    night = r.night,
                    recent = recent,
                    source = source,
                    banner = r.banner,
                    series = series,
                    lastResult = r.lastResult,
                    lastNightShort = r.lastNightShort,
                    quietDays = r.quietDays,
                    check =
                        if (inputs.healthConnectStillMissing) {
                            ManualCheckUi.HealthConnectStillMissing
                        } else {
                            checkUi(inputs.check, records, zone)
                        },
                ),
            )
        }

        /** "Checking…" while a manual check runs; "Checked at ..." when it ended without storing a night. */
        private fun checkUi(
            check: ManualCheck,
            records: List<NightRecord>,
            zone: ZoneId,
        ): ManualCheckUi =
            when (check) {
                ManualCheck.None -> {
                    ManualCheckUi.None
                }

                ManualCheck.Checking -> {
                    ManualCheckUi.Checking
                }

                is ManualCheck.Done -> {
                    // A stored or re-read night changes the screen by itself; only "nothing new" needs words.
                    if (records.any { it.computedAtMs >= check.requestedAtMs }) {
                        ManualCheckUi.None
                    } else {
                        val time =
                            DateTimeFormatter
                                .ofLocalizedTime(FormatStyle.SHORT)
                                .withLocale(Locale.getDefault())
                                .format(Instant.ofEpochMilli(check.finishedAtMs).atZone(zone))
                        ManualCheckUi.NothingNew(time)
                    }
                }
            }

        /** "Check now" (`E-SYNC`, `E-NODATA`, `B-WAIT`, `B-READ-FAILED`). */
        fun checkNow() = data.checkNow()

        /** Resume: re-reads the platform status. */
        fun refresh() {
            viewModelScope.launch { data.refresh() }
        }

        /** "Check again" on the Health Connect update card: re-reads it and says so when it is still missing. */
        fun recheckHealthConnect() {
            viewModelScope.launch { data.recheckHealthConnect() }
        }

        /** The dismiss button of `B-NOTIF-OFF` (30 days) and `B-RANGE-READY` (once). */
        fun dismissBanner(kind: BannerKind) {
            viewModelScope.launch {
                when (kind) {
                    BannerKind.NotificationsOff -> data.settingsRepository.setNotificationBannerDismissedAt(data.now().toEpochMilli())
                    BannerKind.RangeReady -> data.settingsRepository.setRangeReadySeen(true)
                    else -> Unit
                }
            }
        }

        /** Constants. */
        companion object {
            private const val STOP_TIMEOUT_MS = 5_000L

            /** [PerfProbe] name of one mapping of the inputs. */
            const val MAP_PROBE: String = "HomeViewModel.build"
        }
    }
