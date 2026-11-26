package io.github.alien2003.nightbaseline.app

import io.github.alien2003.nightbaseline.data.hc.HealthDataReader
import io.github.alien2003.nightbaseline.data.local.NightRecord
import io.github.alien2003.nightbaseline.data.local.NightRepository
import io.github.alien2003.nightbaseline.data.local.RunLog
import io.github.alien2003.nightbaseline.data.local.RunLogKind
import io.github.alien2003.nightbaseline.data.local.RunLogLine
import io.github.alien2003.nightbaseline.data.local.ZoneSource
import io.github.alien2003.nightbaseline.data.local.settings.AppSettings
import io.github.alien2003.nightbaseline.data.local.settings.SettingsRepository
import io.github.alien2003.nightbaseline.work.process.HistoryBackfill
import io.github.alien2003.nightbaseline.work.process.NightReevaluator
import io.github.alien2003.nightbaseline.work.schedule.NightScheduler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything the ViewModels read and do, in one injectable bundle: the stored nights and settings (Room, DataStore),
 * the run log, the platform status, the night-check chain and the first-run history read. ViewModels map it to the
 * UI state classes off the main thread ([compute]); tests build it from the data-local / data-hc fakes.
 */
@Suppress("LongParameterList") // One bundle instead of the same eleven parameters on every ViewModel.
@Singleton
class AppData(
    /** Settings. */
    val settingsRepository: SettingsRepository,
    /** Stored nights. */
    val nightRepository: NightRepository,
    /** Background run log (value-free). */
    val runLog: RunLog,
    /** Health Connect, source and notification status. */
    val access: AccessMonitor,
    /** Whether a check runs. */
    val checks: CheckState,
    /** The daily chain and "Check now". */
    val scheduler: NightScheduler,
    /** Wall clock. */
    val clock: Clock,
    /** The person's zone. */
    val zone: ZoneSource,
    /** Package manager and notification facts. */
    val platform: AppPlatform,
    /** Health Connect (source discovery during onboarding and in the source picker only). */
    val reader: HealthDataReader,
    /** First-run history read. */
    val backfill: HistoryBackfill,
    /** Re-evaluation of stored nights when the algorithm or its parameters change. */
    val reevaluator: NightReevaluator,
    /** Where the mapping to UI state runs. */
    val compute: CoroutineDispatcher,
    /** Where Health Connect and the database are called from (onboarding, source picker). */
    val io: CoroutineDispatcher,
) {
    /** Hilt constructor: the production dispatchers. */
    @Suppress("LongParameterList")
    @Inject
    constructor(
        settingsRepository: SettingsRepository,
        nightRepository: NightRepository,
        runLog: RunLog,
        access: AccessMonitor,
        checks: CheckState,
        scheduler: NightScheduler,
        clock: Clock,
        zone: ZoneSource,
        platform: AppPlatform,
        reader: HealthDataReader,
        backfill: HistoryBackfill,
        reevaluator: NightReevaluator,
    ) : this(
        settingsRepository,
        nightRepository,
        runLog,
        access,
        checks,
        scheduler,
        clock,
        zone,
        platform,
        reader,
        backfill,
        reevaluator,
        Dispatchers.Default,
        Dispatchers.IO,
    )

    private val tick = MutableStateFlow(0)

    /** Settings, distinct. */
    val settings: Flow<AppSettings> get() = settingsRepository.settings.distinctUntilChanged()

    /** Nights of the configured source, newest first (follows a source change). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val currentNights: Flow<List<NightRecord>>
        get() = settings.map { it.sourcePackage }.distinctUntilChanged().flatMapLatest { nightRepository.observe(it) }

    /** Nights of every source, newest first. */
    val allNights: Flow<List<NightRecord>> get() = nightRepository.observeAll()

    /** The newest run-log lines, read again whenever the nights change or [refresh] runs. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val recentRuns: Flow<List<RunLogLine>>
        get() = combine(nightRepository.observeAll(), tick) { _, t -> t }.mapLatest { runLog.recent(RUN_LINES) }

    /** Now. */
    fun now(): Instant = clock.instant()

    /** The current zone. */
    fun zoneId(): ZoneId = zone.zone()

    /** Re-checks the platform status and the run log (resume, permission result, "Check again"). */
    suspend fun refresh() {
        access.refresh()
        tick.update { it + 1 }
    }

    /**
     * Keeps the stored evaluations in step with the current parameters for as long as the caller collects: on start
     * and on every change of the effective [AlgoParams][io.github.alien2003.nightbaseline.algo.AlgoParams] (the
     * sensitivity), nights evaluated with another algorithm version or other parameters are re-evaluated silently.
     */
    suspend fun keepEvaluationsCurrent() {
        settings.map { it.algoParams() }.distinctUntilChanged().collect { reevaluator.reevaluateIfNeeded(it) }
    }

    /** "Check now": one night check right away. */
    fun checkNow() = scheduler.runNow()

    /** Time of the last successful read (evaluation or history read), formatted for [locale]; null when never. */
    fun lastRead(
        runs: List<RunLogLine>,
        locale: Locale = Locale.getDefault(),
    ): String? =
        runs
            .firstOrNull { it.kind == RunLogKind.EVALUATED || it.kind == RunLogKind.BACKFILL }
            ?.let { line ->
                DateTimeFormatter
                    .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                    .withLocale(locale)
                    .format(Instant.ofEpochMilli(line.timeMs).atZone(zoneId()))
            }

    /** Constants. */
    companion object {
        /** Run-log lines the banners look at. */
        const val RUN_LINES: Int = 20
    }
}
