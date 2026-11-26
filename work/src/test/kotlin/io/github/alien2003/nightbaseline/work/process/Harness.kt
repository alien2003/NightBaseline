package io.github.alien2003.nightbaseline.work.process

import io.github.alien2003.nightbaseline.algo.SyntheticNights
import io.github.alien2003.nightbaseline.data.hc.HealthConnectReader
import io.github.alien2003.nightbaseline.data.hc.fake.FakeHealthConnectGateway
import io.github.alien2003.nightbaseline.data.hc.fake.SyntheticSource
import io.github.alien2003.nightbaseline.data.local.fake.FakeSettingsRepository
import io.github.alien2003.nightbaseline.data.local.fake.InMemoryNightRepository
import io.github.alien2003.nightbaseline.data.local.fake.InMemoryRunLog
import io.github.alien2003.nightbaseline.work.notify.DataProblem
import io.github.alien2003.nightbaseline.work.notify.NightNotice
import io.github.alien2003.nightbaseline.work.notify.NightNotifier
import io.github.alien2003.nightbaseline.work.notify.ProblemNotifier
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** A clock the test moves. */
class MutableClock(
    var nowMs: Long = 0,
) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = Instant.ofEpochMilli(nowMs)
}

/** The processor and the backfill over fakes: Health Connect (with the real reader), Room, settings, notifications. */
class Harness(
    /** Device zone. */
    val zone: ZoneId = ZoneOffset.ofHours(3),
) {
    val clock = MutableClock()
    val gateway = FakeHealthConnectGateway { clock.nowMs }
    var inForeground = false
    val reader = HealthConnectReader(gateway, { inForeground }, { zone }, ownPackage = "io.github.alien2003.nightbaseline")
    val settings = FakeSettingsRepository()
    val nights = InMemoryNightRepository()
    val runLog = InMemoryRunLog { clock.nowMs }
    val notices = mutableListOf<NightNotice>()
    var notificationsShown = true
    val notifier =
        NightNotifier { notice ->
            if (notificationsShown) notices += notice
            notificationsShown
        }
    val problems = mutableListOf<DataProblem?>()
    val problemNotifier = ProblemNotifier { problems += it }
    val reevaluator = NightReevaluator(settings, nights, runLog, clock)
    val diagnostics = mutableListOf<String>()
    val processor = NightProcessor(settings, reader, nights, notifier, runLog, clock, { zone }, reevaluator) { diagnostics += it }
    val backfill = HistoryBackfill(settings, reader, nights, runLog, clock)

    /** Stored record of [night]. */
    suspend fun record(night: SyntheticNights.Generated) = nights.get(SyntheticSource.ORIGIN, night.wakeDate)

    companion object {
        const val MIN: Long = 60_000L
        const val HOUR: Long = 60 * MIN

        /** Regular nights (no missing or short nights), optionally with episodes. */
        fun nights(
            count: Int,
            episodes: Map<Int, Int> = emptyMap(),
            seed: Long = 11,
        ): List<SyntheticNights.Generated> =
            SyntheticNights(
                SyntheticNights.Config(seed = seed, nights = count, missingNightP = 0.0, shortNightP = 0.0, episodes = episodes),
            ).generate()

        /** End of the main night. */
        fun endOf(night: SyntheticNights.Generated): Long = SyntheticSource.nightEndMs(night)
    }
}
