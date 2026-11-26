package io.github.alien2003.nightbaseline.app

import io.github.alien2003.nightbaseline.algo.AlgoParams
import io.github.alien2003.nightbaseline.algo.Evaluator
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.algo.NightStatus
import io.github.alien2003.nightbaseline.data.hc.HcAvailability
import io.github.alien2003.nightbaseline.data.hc.HcPermissions
import io.github.alien2003.nightbaseline.data.local.NightRecord
import io.github.alien2003.nightbaseline.data.local.RunLogKind
import io.github.alien2003.nightbaseline.data.local.RunLogLine
import io.github.alien2003.nightbaseline.data.local.settings.AppSettings
import io.github.alien2003.nightbaseline.ui.model.BannerKind
import io.github.alien2003.nightbaseline.ui.model.ChartPointUi
import io.github.alien2003.nightbaseline.ui.model.MetricSeriesUi
import io.github.alien2003.nightbaseline.ui.model.MetricState
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.model.NightUi
import io.github.alien2003.nightbaseline.ui.model.NightUiMapper
import io.github.alien2003.nightbaseline.ui.settings.NotificationAccess
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Pure mapping from what is stored and what the platform reports to the UI model of the screens (`states.md` 1, 3):
 * the night state by priority, the banner by priority, the per-night UI and the chart series with the range each
 * night had. No Android types, so every rule is unit-tested.
 */
object NightPresenter {
    /** Health Connect permission of each metric's record type (sleep duration comes from the sessions). */
    val METRIC_PERMISSIONS: Map<MetricId, String> =
        mapOf(
            MetricId.SLEEPING_HR to HcPermissions.READ_HEART_RATE,
            MetricId.RESPIRATORY_RATE to HcPermissions.READ_RESPIRATORY_RATE,
            MetricId.SPO2 to HcPermissions.READ_OXYGEN_SATURATION,
            MetricId.SLEEP_DURATION to HcPermissions.READ_SLEEP,
            MetricId.HRV to HcPermissions.READ_HEART_RATE_VARIABILITY,
            MetricId.SKIN_TEMPERATURE to HcPermissions.READ_SKIN_TEMPERATURE,
        )

    /** Before this local time with no night for the expected wake date: the previous night and `B-WAIT`. */
    val WAIT_UNTIL: LocalTime = LocalTime.of(WAIT_HOUR, 0)

    /** From this local time a missing night becomes `E-SYNC` / `E-NODATA`. */
    val MISSING_FROM: LocalTime = LocalTime.of(MISSING_HOUR, 0)

    /** The wake date of "last night" changes at this local time. */
    val DAY_STARTS: LocalTime = LocalTime.of(DAY_START_HOUR, 0)

    private const val WAIT_HOUR = 15
    private const val MISSING_HOUR = 18
    private const val DAY_START_HOUR = 3

    /** `B-SOURCE-QUIET` after this many days without a new night. */
    const val QUIET_DAYS: Int = 3

    /** `B-NOTIF-OFF` stays hidden this long after a dismiss. */
    const val NOTIF_BANNER_SNOOZE_MS: Long = 30L * 24 * 60 * 60 * 1000

    /** `B-RANGE-READY` is offered while the learning period ended at most this many nights ago. */
    const val RANGE_READY_NIGHTS: Int = 3

    /** Calendar nights of the longest chart period. */
    const val SERIES_NIGHTS: Int = 90

    /** "Expected wake date" (`states.md` 1): today from 03:00 local, otherwise yesterday. */
    fun expectedWakeDate(
        now: Instant,
        zone: ZoneId,
    ): LocalDate {
        val local = now.atZone(zone)
        return if (local.toLocalTime() >= DAY_STARTS) local.toLocalDate() else local.toLocalDate().minusDays(1)
    }

    /** States 1-4 of `states.md` 1 (`E-HC-NONE`, `E-HC-UPDATE`, `E-PERM`, `E-SOURCE-GONE`), or null. */
    fun problemState(access: AccessStatus?): NightState? =
        when {
            access == null -> null
            access.healthConnect == HcAvailability.NOT_SUPPORTED -> NightState.HealthConnectNone
            access.healthConnect == HcAvailability.UPDATE_REQUIRED -> NightState.HealthConnectUpdate
            HcPermissions.missingRequired(access.granted).isNotEmpty() -> NightState.Permission
            access.sourceLabel == null -> NightState.SourceGone
            else -> null
        }

    /** Metrics whose record permission is off while the required ones are granted (`M-PERM`). */
    fun accessOff(access: AccessStatus?): Set<MetricId> {
        val granted = access?.granted.orEmpty()
        val usable = access?.healthConnect == HcAvailability.AVAILABLE && HcPermissions.missingRequired(granted).isEmpty()
        return if (usable) METRIC_PERMISSIONS.filterValues { it !in granted }.keys else emptySet()
    }

    /** The UI nights of [records] (any order), newest first, mapped with [params] in [zone]. */
    fun nights(
        records: List<NightRecord>,
        params: AlgoParams,
        zone: ZoneId,
        access: AccessStatus?,
    ): List<NightUi> {
        val off = accessOff(access)
        val history = records.map { it.summary }
        return records.sortedByDescending { it.date }.map { r ->
            val offset = zone.rules.getOffset(Instant.ofEpochMilli(r.summary.startMs))
            NightUiMapper.night(r.summary, r.evaluation, params, history, off, offset)
        }
    }

    /** A night without stored data (state cards, `L0`, `LOADING`) for [wakeDate]. */
    fun placeholder(
        wakeDate: LocalDate,
        state: NightState,
    ): NightUi = NightUi(wakeDate = wakeDate, startDate = wakeDate.minusDays(1), state = state, metrics = emptyList())

    /**
     * Stored nights of one source per date, preferring [preferred]'s night where two sources stored the same date
     * (History keeps an earlier source's nights after a source change).
     */
    fun mergeSources(
        records: List<NightRecord>,
        preferred: String,
    ): List<NightRecord> =
        records
            .groupBy { it.date }
            .map { (_, sameDate) -> sameDate.firstOrNull { it.sourcePackage == preferred } ?: sameDate.first() }
            .sortedByDescending { it.date }

    /** What Home shows for one set of inputs. */
    data class HomeResolution(
        /** The night of the summary / learning / state card. */
        val night: NightUi,
        /** The newest stored night shown under "Last result", if the night state has one. */
        val lastResult: NightUi?,
        /** Banner. */
        val banner: BannerKind?,
        /** Days without a new night (`B-SOURCE-QUIET`). */
        val quietDays: Int,
        /** The learning presentation (`screens.md` 7.6) applies. */
        val learning: Boolean,
        /** Learning, and the newest night was short (`state_learning_short_note`). */
        val lastNightShort: Boolean,
    )

    /**
     * Home's night state (`states.md` 1, first match wins) and banner (`B-BG` > `B-READ-FAILED` > `B-SOURCE-QUIET` >
     * `B-WAIT` > `B-NOTIF-OFF` > `B-RANGE-READY`). [nights] are the configured source's UI nights, newest first.
     */
    @Suppress("LongParameterList", "CyclomaticComplexMethod") // One decision table over every input of states.md 1.
    fun resolveHome(
        now: Instant,
        zone: ZoneId,
        settings: AppSettings,
        access: AccessStatus?,
        nights: List<NightUi>,
        running: Boolean,
        runLog: List<RunLogLine>,
    ): HomeResolution {
        val expected = expectedWakeDate(now, zone)
        val localTime = now.atZone(zone).toLocalTime()
        val today = now.atZone(zone).toLocalDate()
        val problem = problemState(access)
        val newest = nights.firstOrNull()
        val hasExpected = newest != null && !newest.wakeDate.isBefore(expected)
        val quietDays = newest?.let { ChronoUnit.DAYS.between(it.wakeDate, today).toInt() } ?: 0
        // The newest night, unless the source has not delivered last night yet and the day is over.
        val missing = newest != null && !hasExpected && localTime >= MISSING_FROM
        val night =
            when {
                problem != null -> placeholder(expected, problem)

                newest == null -> placeholder(expected, if (running) NightState.Loading else NightState.LearningStart)

                missing && running -> newest

                // Without a sync time from Health Connect: the source counts as silent when the night before last
                // is missing too, otherwise it synced and simply has no session for last night.
                missing -> placeholder(expected, if (quietDays >= 2) NightState.NotSynced else NightState.NoData)

                else -> learningView(newest, settings)
            }
        val lastResult = newest?.takeIf { night.state in LAST_RESULT_STATES }
        val learning = problem == null && (night.state == NightState.Learning || night.state == NightState.LearningStart)
        val banner =
            banner(now, settings, access, newest, hasExpected, localTime, quietDays, runLog, nights).takeIf {
                // E-SYNC already says what B-SOURCE-QUIET and B-WAIT would say.
                !(night.state == NightState.NotSynced && (it == BannerKind.SourceQuiet || it == BannerKind.Wait))
            }
        return HomeResolution(
            night = night,
            lastResult = lastResult,
            banner = banner,
            quietDays = quietDays,
            learning = learning,
            lastNightShort = learning && newest?.state == NightState.Short,
        )
    }

    /** A short night during the learning period keeps the learning presentation (priority 7 before 10). */
    private fun learningView(
        newest: NightUi,
        settings: AppSettings,
    ): NightUi {
        val params = settings.algoParams()
        val stillLearning = newest.historyNights < params.minNights
        return if (newest.state == NightState.Short && stillLearning) {
            newest.copy(state = if (newest.historyNights == 0) NightState.LearningStart else NightState.Learning)
        } else {
            newest
        }
    }

    @Suppress("LongParameterList", "ReturnCount", "CyclomaticComplexMethod") // The banner order of states.md 3.
    private fun banner(
        now: Instant,
        settings: AppSettings,
        access: AccessStatus?,
        newest: NightUi?,
        hasExpected: Boolean,
        localTime: LocalTime,
        quietDays: Int,
        runLog: List<RunLogLine>,
        nights: List<NightUi>,
    ): BannerKind? {
        val granted = access?.granted.orEmpty()
        val coreOk = access != null && HcPermissions.missingRequired(granted).isEmpty()
        if (coreOk && access.backgroundFeature && HcPermissions.READ_HEALTH_DATA_IN_BACKGROUND !in granted) return BannerKind.Background
        val lastRun = runLog.firstOrNull { it.kind != RunLogKind.NOTIFIED && it.kind != RunLogKind.RECOMPUTED }
        if (lastRun?.kind == RunLogKind.ERROR) return BannerKind.ReadFailed
        if (newest != null && quietDays >= QUIET_DAYS) return BannerKind.SourceQuiet
        if (newest != null && !hasExpected && localTime < WAIT_UNTIL) return BannerKind.Wait
        val snoozed = settings.notificationBannerDismissedAtMs?.let { now.toEpochMilli() - it < NOTIF_BANNER_SNOOZE_MS } ?: false
        if (access?.notifications == NotificationAccess.Blocked && settings.notificationsEnabled && !snoozed) {
            return BannerKind.NotificationsOff
        }
        // Right after the learning period: the newest nights are results and a learning night is just before them.
        val sinceLearning = nights.indexOfFirst { it.state == NightState.Learning || it.state == NightState.LearningStart }
        val ready = newest != null && newest.state.hasSummary && sinceLearning in 1..RANGE_READY_NIGHTS
        if (ready && !settings.rangeReadySeen) return BannerKind.RangeReady
        return null
    }

    /** Night states that show the newest stored night under "Last result" (`states.md` 1 rules). */
    private val LAST_RESULT_STATES =
        setOf(NightState.HealthConnectUpdate, NightState.Permission, NightState.SourceGone, NightState.NotSynced)

    /**
     * The chart series of [metric] for the [nights] calendar nights up to [anchor] (oldest first, one point per
     * night, gaps included). Ranges are recomputed per night from the stored summaries with [params], so the band
     * follows the baseline as it was on each night under the current sensitivity; nights of another source keep the
     * band they had when stored.
     */
    fun series(
        metric: MetricId,
        records: List<NightRecord>,
        anchor: LocalDate,
        params: AlgoParams,
        currentSource: String,
        nights: Int = SERIES_NIGHTS,
    ): MetricSeriesUi {
        val byDate = records.associateBy { LocalDate.parse(it.date) }
        val bySource = records.groupBy { it.sourcePackage }.mapValues { (_, list) -> list.map { it.summary } }
        val points =
            (nights - 1 downTo 0).map { back ->
                val date = anchor.minusDays(back.toLong())
                val record = byDate[date]
                if (record == null) {
                    ChartPointUi(date, null, null, MetricState.NoData)
                } else {
                    val evaluation =
                        if (record.sourcePackage == currentSource) {
                            Evaluator.evaluate(record.summary, bySource[currentSource].orEmpty(), params)
                        } else {
                            record.evaluation
                        }
                    val e = evaluation.metrics.firstOrNull { it.metric == metric }
                    val short = record.summary.short || record.summary.ambiguous
                    val state = e?.let { NightUiMapper.metricState(it, short) } ?: MetricState.NotProvided
                    ChartPointUi(date, e?.value, e?.baseline?.let(NightUiMapper::range), state)
                }
            }
        return MetricSeriesUi(metric, points)
    }

    /** Calendar nights from the first stored value of [metric] up to [anchor] (`D-PERIOD-SHORT`). */
    fun storedNights(series: MetricSeriesUi): Int {
        val first = series.points.indexOfFirst { it.value != null }
        return if (first < 0) 0 else series.points.size - first
    }

    /** Whether [evaluation status][NightStatus] counts as a result (not learning, not a gap). */
    fun isResult(status: NightStatus): Boolean =
        status == NightStatus.ALL_WITHIN || status == NightStatus.SOME_DEVIATIONS || status == NightStatus.DEVIATIONS
}
