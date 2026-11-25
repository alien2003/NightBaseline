package io.github.alien2003.nightbaseline.work.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.github.alien2003.nightbaseline.data.hc.HcError
import io.github.alien2003.nightbaseline.data.local.RunLog
import io.github.alien2003.nightbaseline.data.local.RunLogKind
import io.github.alien2003.nightbaseline.data.local.ZoneSource
import io.github.alien2003.nightbaseline.data.local.settings.SettingsRepository
import io.github.alien2003.nightbaseline.work.notify.DataProblem
import io.github.alien2003.nightbaseline.work.notify.ProblemNotifier
import io.github.alien2003.nightbaseline.work.process.BackfillOutcome
import io.github.alien2003.nightbaseline.work.process.BlockReason
import io.github.alien2003.nightbaseline.work.process.HistoryBackfill
import io.github.alien2003.nightbaseline.work.process.NightProcessor
import io.github.alien2003.nightbaseline.work.process.ProcessOutcome
import io.github.alien2003.nightbaseline.work.schedule.DailySchedule
import io.github.alien2003.nightbaseline.work.schedule.NightScheduler
import io.github.alien2003.nightbaseline.work.schedule.RunResult
import io.github.alien2003.nightbaseline.work.schedule.WorkPlanner
import io.github.alien2003.nightbaseline.work.schedule.WorkStep
import kotlinx.coroutines.flow.first
import java.time.Clock
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [NightRun] and continues the chain (see [WorkPlanner]): a thin adapter, so all logic is tested without
 * WorkManager. Constructed by `HiltWorkerFactory` (the app's `Configuration.Provider`). Never throws except for
 * cancellation.
 */
@HiltWorker
@Suppress("LongParameterList") // Assisted worker parameters plus the injected collaborators.
class NightWorker
    @AssistedInject
    constructor(
        @Assisted appContext: Context,
        @Assisted params: WorkerParameters,
        private val run: NightRun,
        private val scheduler: NightScheduler,
        private val problems: ProblemNotifier,
        private val clock: Clock,
        private val zone: ZoneSource,
        private val settings: SettingsRepository,
    ) : CoroutineWorker(appContext, params) {
        override suspend fun doWork(): Result {
            val result = run.run()
            val nextDaily = DailySchedule.nextRunMs(clock.millis(), scheduler.dailyTimeMinutes, zone.zone())
            return when (val step = WorkPlanner.plan(result, runAttemptCount, nextDaily)) {
                WorkStep.Backoff -> {
                    Result.retry()
                }

                is WorkStep.RunAt -> {
                    // Out of retries: the reads keep failing, which the person should know about.
                    if (result is RunResult.Failure && settings.settings.first().problemNotificationsEnabled) {
                        problems.update(DataProblem.READ_FAILED)
                    }
                    scheduler.scheduleNext(step.atMs)
                    Result.success()
                }
            }
        }
    }

/**
 * One run: the first-run backfill if needed, then the night check; every failure becomes a [RunResult]. A blocked
 * run shows its data-access problem, a successful one clears every problem.
 */
class NightRun
    @Inject
    constructor(
        private val backfill: HistoryBackfill,
        private val processor: NightProcessor,
        private val runLog: RunLog,
        private val problems: ProblemNotifier,
        private val settings: SettingsRepository,
    ) {
        /** Runs once. */
        @Suppress("TooGenericExceptionCaught") // A background run must never crash: every exception becomes a logged failure.
        suspend fun run(): RunResult =
            try {
                val outcome =
                    when (val b = backfill.runIfNeeded()) {
                        is BackfillOutcome.Blocked -> ProcessOutcome.Blocked(b.reason)
                        else -> processor.process()
                    }
                // "Problems reading data" off in Settings: nothing is posted and shown problems are cancelled.
                val notify = settings.settings.first().problemNotificationsEnabled
                problems.update((outcome as? ProcessOutcome.Blocked)?.takeIf { notify }?.let { problemOf(it.reason) })
                RunResult.Outcome(outcome)
            } catch (e: CancellationException) {
                throw e
            } catch (e: HcError.RateLimited) {
                failed(e, transient = true)
            } catch (e: HcError.Other) {
                failed(e, transient = e.retryable)
            } catch (e: HcError) {
                failed(e, transient = false)
            } catch (e: Exception) {
                // Only the exception type: messages may carry data.
                runLog.log(RunLogKind.ERROR, "night check failed unexpectedly: ${e.javaClass.simpleName}")
                RunResult.Failure(transient = false)
            }

        private suspend fun failed(
            e: HcError,
            transient: Boolean,
        ): RunResult {
            // HcError messages name the operation and the error type only (no data).
            runLog.log(RunLogKind.ERROR, "night check failed: ${e.message}")
            return RunResult.Failure(transient)
        }
    }

/** The notification problem of a block [reason]. */
fun problemOf(reason: BlockReason): DataProblem =
    when (reason) {
        BlockReason.HEALTH_CONNECT_UNAVAILABLE -> DataProblem.HEALTH_CONNECT
        BlockReason.PERMISSION_MISSING -> DataProblem.PERMISSION
        BlockReason.BACKGROUND_PERMISSION_MISSING -> DataProblem.BACKGROUND
    }
