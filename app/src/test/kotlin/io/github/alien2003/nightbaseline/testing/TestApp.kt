package io.github.alien2003.nightbaseline.testing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import io.github.alien2003.nightbaseline.algo.AlgoFingerprint
import io.github.alien2003.nightbaseline.app.AccessMonitor
import io.github.alien2003.nightbaseline.app.AccessStatus
import io.github.alien2003.nightbaseline.app.AppData
import io.github.alien2003.nightbaseline.app.AppPlatform
import io.github.alien2003.nightbaseline.data.hc.HcAvailability
import io.github.alien2003.nightbaseline.data.hc.HcPermissions
import io.github.alien2003.nightbaseline.data.hc.HealthConnectReader
import io.github.alien2003.nightbaseline.data.hc.fake.FakeHealthConnectGateway
import io.github.alien2003.nightbaseline.data.local.NightRecord
import io.github.alien2003.nightbaseline.data.local.fake.FakeSettingsRepository
import io.github.alien2003.nightbaseline.data.local.fake.InMemoryNightRepository
import io.github.alien2003.nightbaseline.data.local.fake.InMemoryRunLog
import io.github.alien2003.nightbaseline.data.local.settings.AppSettings
import io.github.alien2003.nightbaseline.nav.AppRootViewModel
import io.github.alien2003.nightbaseline.ui.history.HistoryViewModel
import io.github.alien2003.nightbaseline.ui.home.HomeViewModel
import io.github.alien2003.nightbaseline.ui.metric.MetricDetailViewModel
import io.github.alien2003.nightbaseline.ui.night.NightDetailViewModel
import io.github.alien2003.nightbaseline.ui.onboarding.OnboardingViewModel
import io.github.alien2003.nightbaseline.ui.sample.SampleData
import io.github.alien2003.nightbaseline.ui.settings.NotificationAccess
import io.github.alien2003.nightbaseline.ui.settings.SettingsViewModel
import io.github.alien2003.nightbaseline.work.process.HistoryBackfill
import io.github.alien2003.nightbaseline.work.process.NightReevaluator
import io.github.alien2003.nightbaseline.work.schedule.NightScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** A [Clock] the test sets. */
class SettableClock(
    var now: Instant,
) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = now
}

/** [AccessMonitor] with a settable status. */
class FakeAccessMonitor(
    initial: AccessStatus?,
) : AccessMonitor {
    private val state = MutableStateFlow(initial)
    override val status: StateFlow<AccessStatus?> = state.asStateFlow()

    /** How often [refresh] ran. */
    var refreshes: Int = 0

    /** Sets the status. */
    fun set(value: AccessStatus?) {
        state.value = value
    }

    override suspend fun refresh() {
        refreshes++
    }
}

/** [AppPlatform] with fixed answers. */
class FakePlatform(
    var labels: Map<String, String> = mapOf(AppSettings.DEFAULT_SOURCE_PACKAGE to SampleData.SOURCE),
    var notifications: NotificationAccess = NotificationAccess.Allowed,
    override val sdkInt: Int = 36,
) : AppPlatform {
    /** Whether the notification prompt was marked as shown. */
    var asked: Boolean = false

    override fun appLabel(packageName: String): String? = labels[packageName]

    override fun notificationAccess(): NotificationAccess = notifications

    override val versionName: String = "0.1.0-test"

    override fun markNotificationsAsked() {
        asked = true
    }

    /** Time zone / clock changes the test announces. */
    val timeChange = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun timeChanges(): Flow<Unit> = timeChange
}

/** [NightScheduler] that records its calls. */
class FakeScheduler : NightScheduler {
    /** "Check now" calls. */
    var runNow: Int = 0

    /** Ensure-scheduled calls. */
    var ensured: Int = 0

    override val dailyTimeMinutes: Int = 8 * 60

    override fun ensureScheduled() {
        ensured++
    }

    override fun scheduleNext(atMs: Long) = Unit

    override fun runNow() {
        runNow++
    }
}

/**
 * The app's data layer over the data-local / data-hc fakes, filled with the synthetic [SampleData] nights; everything
 * runs on the calling thread (unconfined), so ViewModels and composables see results without waiting.
 */
class TestApp(
    /** Wall time (default: 10:00 local on the newest sample wake date). */
    now: Instant =
        SampleData.LAST_WAKE
            .atTime(10, 0)
            .atZone(SampleData.ZONE)
            .toInstant(),
    nights: Int = SampleData.evaluated.size,
    settings: AppSettings = AppSettings(onboardingDone = true),
    access: AccessStatus? = allowed(),
) {
    /** The clock. */
    val clock = SettableClock(now)

    /** Settings. */
    val settings = FakeSettingsRepository(settings)

    /** Stored nights. */
    val nights = InMemoryNightRepository()

    /** Run log. */
    val runLog = InMemoryRunLog { clock.millis() }

    /** Platform status. */
    val access = FakeAccessMonitor(access)

    /** Package manager / notifications. */
    val platform = FakePlatform()

    /** Scheduler. */
    val scheduler = FakeScheduler()

    /** Health Connect (synthetic, empty unless a test adds data). */
    val gateway = FakeHealthConnectGateway { clock.millis() }

    /** Whether a check runs. */
    val running = MutableStateFlow(false)

    /** The person's zone (a test may change it, then announce it with [FakePlatform.timeChange]). */
    var zoneId: ZoneId = SampleData.ZONE

    private val reader = HealthConnectReader(gateway, { true }, { SampleData.ZONE }, ownPackage = "io.github.alien2003.nightbaseline")

    /** The bundle the ViewModels take. */
    val data =
        AppData(
            settingsRepository = this.settings,
            nightRepository = this.nights,
            runLog = runLog,
            access = this.access,
            checks = { running },
            scheduler = scheduler,
            clock = clock,
            zone = { zoneId },
            platform = platform,
            reader = reader,
            backfill = HistoryBackfill(this.settings, reader, this.nights, runLog, clock),
            reevaluator = NightReevaluator(this.settings, this.nights, runLog, clock),
            compute = Dispatchers.Unconfined,
            io = Dispatchers.Unconfined,
        )

    init {
        runBlocking { records(nights).forEach { this@TestApp.nights.upsert(it) } }
    }

    /** Sets the local time on the newest sample wake date. */
    fun at(time: LocalDateTime) {
        clock.now = time.atZone(SampleData.ZONE).toInstant()
    }

    /** ViewModels over [data]; [SavedStateHandle][androidx.lifecycle.SavedStateHandle] from the creation extras. */
    val factory: ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras,
            ): T {
                val handle = extras.createSavedStateHandle()
                val vm: ViewModel =
                    when (modelClass) {
                        HomeViewModel::class.java -> HomeViewModel(data)
                        HistoryViewModel::class.java -> HistoryViewModel(handle, data)
                        NightDetailViewModel::class.java -> NightDetailViewModel(handle, data)
                        MetricDetailViewModel::class.java -> MetricDetailViewModel(handle, data)
                        SettingsViewModel::class.java -> SettingsViewModel(handle, data)
                        OnboardingViewModel::class.java -> OnboardingViewModel(handle, data)
                        AppRootViewModel::class.java -> AppRootViewModel(data)
                        else -> error("no test factory for $modelClass")
                    }
                return vm as T
            }
        }

    /** Constructors. */
    companion object {
        /** Everything granted, Health Connect available, the sample source installed, notifications allowed. */
        fun allowed(
            granted: Set<String> = HcPermissions.ALL,
            healthConnect: HcAvailability = HcAvailability.AVAILABLE,
            sourceLabel: String? = SampleData.SOURCE,
            notifications: NotificationAccess = NotificationAccess.Allowed,
        ): AccessStatus =
            AccessStatus(
                healthConnect = healthConnect,
                granted = granted,
                backgroundFeature = true,
                sourcePackage = AppSettings.DEFAULT_SOURCE_PACKAGE,
                sourceLabel = sourceLabel,
                notifications = notifications,
            )

        /**
         * The first [count] synthetic nights as stored records of the default source, oldest first, evaluated with the
         * default parameters (their fingerprint is current, so nothing is re-evaluated unless a test changes them).
         */
        fun records(count: Int = SampleData.evaluated.size): List<NightRecord> {
            val fingerprint = AlgoFingerprint.of(AppSettings().algoParams())
            return SampleData.evaluated.take(count).map { (summary, evaluation) ->
                NightRecord(
                    AppSettings.DEFAULT_SOURCE_PACKAGE,
                    summary.date,
                    summary,
                    evaluation,
                    0L,
                    final = true,
                    evaluationCount = 1,
                    algoFingerprint = fingerprint,
                )
            }
        }
    }
}
