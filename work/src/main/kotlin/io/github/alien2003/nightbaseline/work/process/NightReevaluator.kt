package io.github.alien2003.nightbaseline.work.process

import io.github.alien2003.nightbaseline.algo.AlgoFingerprint
import io.github.alien2003.nightbaseline.algo.AlgoParams
import io.github.alien2003.nightbaseline.algo.Evaluator
import io.github.alien2003.nightbaseline.data.local.NightRepository
import io.github.alien2003.nightbaseline.data.local.RunLog
import io.github.alien2003.nightbaseline.data.local.RunLogKind
import io.github.alien2003.nightbaseline.data.local.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps stored evaluations in step with the algorithm: every stored night carries the [AlgoFingerprint] of the
 * algorithm version and parameters that evaluated it. When a source has a night whose fingerprint differs from the
 * current one (an app update bumped [AlgoFingerprint.ALGO_VERSION], or the sensitivity changed the parameters), every
 * night of that source is evaluated again from its stored summary against the other stored summaries. Nothing is read
 * from Health Connect, `final`, the evaluation count and the notification fields are kept, and nothing is notified.
 *
 * Runs at the start of every night check ([NightProcessor]) and whenever the app's parameters change (app start,
 * sensitivity change; see the app's root ViewModel).
 */
@Singleton
class NightReevaluator
    @Inject
    constructor(
        private val settings: SettingsRepository,
        private val nights: NightRepository,
        private val runLog: RunLog,
        private val clock: Clock,
    ) {
        private val mutex = Mutex()

        /** Re-evaluates with the parameters of the current settings; returns the number of nights written. */
        suspend fun reevaluateIfNeeded(): Int = reevaluateIfNeeded(settings.settings.first().algoParams())

        /** Re-evaluates every source that has a night not evaluated with [params]; returns the number of nights written. */
        suspend fun reevaluateIfNeeded(params: AlgoParams): Int =
            mutex.withLock {
                val current = AlgoFingerprint.of(params)
                val now = clock.millis()
                var written = 0
                val bySource = nights.observeAll().first().groupBy { it.sourcePackage }
                for ((_, records) in bySource) {
                    if (records.all { it.algoFingerprint == current }) continue
                    val history = records.map { it.summary }
                    for (record in records.sortedBy { it.date }) {
                        val evaluation = Evaluator.evaluate(record.summary, history, params)
                        nights.upsert(record.copy(evaluation = evaluation, computedAtMs = now, algoFingerprint = current))
                        written++
                    }
                }
                if (written > 0) {
                    runLog.log(RunLogKind.RECOMPUTED, "re-evaluated $written stored nights (algorithm $current)")
                }
                written
            }
    }
