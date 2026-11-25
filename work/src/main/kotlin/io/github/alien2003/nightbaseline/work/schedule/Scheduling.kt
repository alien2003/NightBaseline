package io.github.alien2003.nightbaseline.work.schedule

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.alien2003.nightbaseline.data.local.ZoneSource
import io.github.alien2003.nightbaseline.work.process.ProcessOutcome
import io.github.alien2003.nightbaseline.work.worker.NightWorker
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** The local time of the next daily run (copied from PulseStages `DailySchedule`). */
object DailySchedule {
    /** Default daily check: 08:00 local time. */
    const val DEFAULT_DAILY_MINUTES: Int = 8 * 60

    private const val MINUTES_PER_HOUR = 60
    private const val LAST_MINUTE_OF_DAY = 24 * 60 - 1

    /**
     * The first time strictly after [nowMs] at which the local time in [zone] is [dailyTimeMinutes] after midnight. A
     * time skipped by a DST change moves forward to the first valid instant; computing it anew for every run (one-time
     * work, not a 24-hour period) keeps the run at the same local time across DST changes.
     */
    fun nextRunMs(
        nowMs: Long,
        dailyTimeMinutes: Int,
        zone: ZoneId,
    ): Long {
        val minutes = dailyTimeMinutes.coerceIn(0, LAST_MINUTE_OF_DAY)
        val time = LocalTime.of(minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val candidate = ZonedDateTime.of(today, time, zone).toInstant().toEpochMilli()
        return if (candidate > nowMs) candidate else ZonedDateTime.of(today.plusDays(1), time, zone).toInstant().toEpochMilli()
    }
}

/** How a run ended, for [WorkPlanner]. */
sealed interface RunResult {
    /** The processor returned an outcome. */
    data class Outcome(
        /** The outcome. */
        val outcome: ProcessOutcome,
    ) : RunResult

    /** The run failed. */
    data class Failure(
        /** Rate limit or IPC / IO error that may pass soon. */
        val transient: Boolean,
    ) : RunResult
}

/** What the worker does next. */
sealed interface WorkStep {
    /** Finish successfully and run again at [atMs]. */
    data class RunAt(
        /** Epoch ms. */
        val atMs: Long,
    ) : WorkStep

    /** `Result.retry()`: WorkManager's exponential backoff from [NightWorkRequests.BACKOFF_DELAY_MS]. */
    data object Backoff : WorkStep
}

/**
 * Pure decision of the next step (the WorkOutcome mapping of PulseStages, reduced):
 * - [ProcessOutcome.Retry] → run again at its time (the night-complete wait, at least 15 min, until the 12 h cap);
 * - evaluated, nothing new, blocked → the next daily run (blocked runs are not retried: the user must act first);
 * - transient failure → backoff while `attempt < MAX_TRANSIENT_RETRIES`, unexpected failure while
 *   `attempt < MAX_UNEXPECTED_RETRIES`; then the next daily run. The chain never ends in a failed work, because a
 *   failed work would also fail the work appended to it.
 */
object WorkPlanner {
    /** Backoff retries of a rate limit / transient error. */
    const val MAX_TRANSIENT_RETRIES: Int = 5

    /** Backoff retries of an unexpected error. */
    const val MAX_UNEXPECTED_RETRIES: Int = 2

    /** The next step after [result] on attempt [attempt] (0 = first run); [nextDailyMs] is the next daily run. */
    fun plan(
        result: RunResult,
        attempt: Int,
        nextDailyMs: Long,
    ): WorkStep =
        when (result) {
            is RunResult.Outcome -> {
                when (val o = result.outcome) {
                    is ProcessOutcome.Retry -> WorkStep.RunAt(minOf(o.atMs, nextDailyMs))
                    else -> WorkStep.RunAt(nextDailyMs)
                }
            }

            is RunResult.Failure -> {
                val limit = if (result.transient) MAX_TRANSIENT_RETRIES else MAX_UNEXPECTED_RETRIES
                if (attempt < limit) WorkStep.Backoff else WorkStep.RunAt(nextDailyMs)
            }
        }
}

/** The work requests of the daily chain. */
object NightWorkRequests {
    /** Unique name of the chain (at most one pending check). */
    const val UNIQUE_NAME: String = "night-check"

    /** Tag of every NightBaseline work. */
    const val TAG: String = "nightbaseline"

    /** Initial backoff delay of a failed run. */
    const val BACKOFF_DELAY_MS: Long = 30_000L

    /** One check of the night after [delayMs]; no constraints (one small read a day). */
    fun check(delayMs: Long): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<NightWorker>()
            .setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_DELAY_MS, TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .build()
}

/**
 * Schedules the daily chain: a one-time work under [NightWorkRequests.UNIQUE_NAME] that, when it finishes, appends
 * the next one (a periodic-like chain whose every run is aligned anew to the morning time, so DST changes and the
 * run's own delay do not make it drift).
 */
interface NightScheduler {
    /** Local time of the daily run, minutes after midnight. */
    val dailyTimeMinutes: Int

    /** Starts the chain at the next daily time unless a check is already pending or running (app start). */
    fun ensureScheduled()

    /** From inside the running check: the next check at [atMs], run after the current one finishes. */
    fun scheduleNext(atMs: Long)

    /** Replaces any pending check with one that runs now (onboarding, source change). */
    fun runNow()
}

/** [NightScheduler] over WorkManager. */
@Singleton
class WorkManagerNightScheduler
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val clock: Clock,
        private val zone: ZoneSource,
    ) : NightScheduler {
        override val dailyTimeMinutes: Int = DailySchedule.DEFAULT_DAILY_MINUTES

        private val workManager: WorkManager get() = WorkManager.getInstance(context)

        override fun ensureScheduled() {
            val now = clock.millis()
            val delay = DailySchedule.nextRunMs(now, dailyTimeMinutes, zone.zone()) - now
            workManager.enqueueUniqueWork(NightWorkRequests.UNIQUE_NAME, ExistingWorkPolicy.KEEP, NightWorkRequests.check(delay))
        }

        override fun scheduleNext(atMs: Long) {
            val request = NightWorkRequests.check(atMs - clock.millis())
            // APPEND: the running check is the chain's last work, so the next one starts after it succeeds.
            workManager.enqueueUniqueWork(NightWorkRequests.UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        override fun runNow() {
            workManager.enqueueUniqueWork(NightWorkRequests.UNIQUE_NAME, ExistingWorkPolicy.REPLACE, NightWorkRequests.check(0))
        }
    }
