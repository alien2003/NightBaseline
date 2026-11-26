package io.github.alien2003.nightbaseline.e2e.support

import io.github.alien2003.nightbaseline.app.AccessMonitor
import io.github.alien2003.nightbaseline.app.AccessStatus
import io.github.alien2003.nightbaseline.app.AndroidAppPlatform
import io.github.alien2003.nightbaseline.app.AppData
import io.github.alien2003.nightbaseline.data.hc.HealthDataReader
import io.github.alien2003.nightbaseline.data.local.ZoneSource
import io.github.alien2003.nightbaseline.data.local.fake.FakeSettingsRepository
import io.github.alien2003.nightbaseline.data.local.fake.InMemoryNightRepository
import io.github.alien2003.nightbaseline.data.local.fake.InMemoryRunLog
import io.github.alien2003.nightbaseline.e2e.E2e
import io.github.alien2003.nightbaseline.e2e.safety.E2eHealth
import io.github.alien2003.nightbaseline.work.process.HistoryBackfill
import io.github.alien2003.nightbaseline.work.process.NightReevaluator
import io.github.alien2003.nightbaseline.work.schedule.NightScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import java.time.Clock

/**
 * An [AppData] for code of the app that takes one (source discovery): the given read-only [HealthDataReader], the
 * real package manager, an in-memory copy of the app's settings and in-memory nights and run log. Its scheduler and
 * access monitor do nothing, so nothing built on it can schedule work or write the app's files.
 */
object E2eAppData {
    /** The bundle. */
    fun create(reader: HealthDataReader): AppData {
        val clock = Clock.systemUTC()
        val settings = FakeSettingsRepository(E2eHealth.settingsSnapshot())
        val nights = InMemoryNightRepository()
        val runLog = InMemoryRunLog { clock.millis() }
        return AppData(
            settingsRepository = settings,
            nightRepository = nights,
            runLog = runLog,
            access = NoAccessMonitor,
            checks = { flowOf(false) },
            scheduler = NoScheduler,
            clock = clock,
            zone = ZoneSource.SYSTEM,
            platform = AndroidAppPlatform(E2e.app),
            reader = reader,
            backfill = HistoryBackfill(settings, reader, nights, runLog, clock),
            reevaluator = NightReevaluator(settings, nights, runLog, clock),
            compute = Dispatchers.Default,
            io = Dispatchers.IO,
        )
    }

    /** Never checks anything. */
    private object NoAccessMonitor : AccessMonitor {
        override val status: StateFlow<AccessStatus?> = MutableStateFlow(null)

        override suspend fun refresh() = Unit
    }

    /** Never schedules anything; a call fails the test. */
    private object NoScheduler : NightScheduler {
        override val dailyTimeMinutes: Int = 0

        override fun ensureScheduled(): Unit = error("the suite never schedules the app's work")

        override fun scheduleNext(atMs: Long): Unit = error("the suite never schedules the app's work")

        override fun runNow(): Unit = error("the suite never schedules the app's work")
    }
}
