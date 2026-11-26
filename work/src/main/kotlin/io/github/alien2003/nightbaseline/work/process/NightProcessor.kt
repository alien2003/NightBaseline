package io.github.alien2003.nightbaseline.work.process

import io.github.alien2003.nightbaseline.algo.AlgoFingerprint
import io.github.alien2003.nightbaseline.algo.AlgoParams
import io.github.alien2003.nightbaseline.algo.ChannelId
import io.github.alien2003.nightbaseline.algo.Completeness
import io.github.alien2003.nightbaseline.algo.CompletenessInput
import io.github.alien2003.nightbaseline.algo.CompletenessParams
import io.github.alien2003.nightbaseline.algo.CompletenessVerdict
import io.github.alien2003.nightbaseline.algo.Evaluator
import io.github.alien2003.nightbaseline.algo.MetricExtractor
import io.github.alien2003.nightbaseline.algo.NightCandidate
import io.github.alien2003.nightbaseline.algo.NightEvaluation
import io.github.alien2003.nightbaseline.algo.NightSelector
import io.github.alien2003.nightbaseline.algo.NightStatus
import io.github.alien2003.nightbaseline.algo.NightSummary
import io.github.alien2003.nightbaseline.algo.Readiness
import io.github.alien2003.nightbaseline.data.hc.HcAvailability
import io.github.alien2003.nightbaseline.data.hc.HcChannels
import io.github.alien2003.nightbaseline.data.hc.HcError
import io.github.alien2003.nightbaseline.data.hc.HcPermissions
import io.github.alien2003.nightbaseline.data.hc.HealthDataReader
import io.github.alien2003.nightbaseline.data.local.NightRecord
import io.github.alien2003.nightbaseline.data.local.NightRepository
import io.github.alien2003.nightbaseline.data.local.RunLog
import io.github.alien2003.nightbaseline.data.local.RunLogKind
import io.github.alien2003.nightbaseline.data.local.ZoneSource
import io.github.alien2003.nightbaseline.data.local.settings.AppSettings
import io.github.alien2003.nightbaseline.data.local.settings.SettingsRepository
import io.github.alien2003.nightbaseline.work.notify.NightNotice
import io.github.alien2003.nightbaseline.work.notify.NightNotifier
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * One daily check of the configured source (pure apart from its collaborators; tested with fakes and a fixed clock).
 *
 * 1. The main nights of the last [AlgoParams.lookbackDays] (+2) days are selected from the source's sessions
 *    ([NightSelector]); the newest one of at least [AlgoParams.minNightMinutes] is the target.
 * 2. If the target was not evaluated yet, [Completeness] judges it: WAIT gives [ProcessOutcome.Retry] (at least
 *    [MIN_RETRY_MS] later, never past the cap); COMPLETE / CAP_REACHED evaluates it against the stored history.
 * 3. History: stored summaries are used as they are; older main nights that are not stored yet are computed from
 *    Health Connect and stored as final (never notified). Nights stored non-final get their one re-evaluation (the
 *    source rewrites the previous day the next morning) when a newer night is complete, or [REEVALUATE_AFTER_MS]
 *    after their end at the latest; it is final afterwards.
 * 4. Notification (docs/design/states.md §6.1): on the first evaluation when it is due (`evaluation.notify`, i.e.
 *    DEVIATIONS), notifications are enabled and the night woke today or yesterday; on the re-evaluation only when the
 *    status changed into DEVIATIONS. Learning, short and ambiguous nights are never notified.
 * 5. When no new night is visible yet in the morning (before [NO_NIGHT_WAIT_UNTIL]), the check is repeated every
 *    [NO_NIGHT_RETRY_MS] so that a late upload is still evaluated on its own day.
 *
 * Before all of this, stored nights evaluated by another algorithm version or other parameters are re-evaluated from
 * their stored summaries ([NightReevaluator]; silent, no Health Connect read), so the history the new night is compared
 * with is current. Every stored record carries the [AlgoFingerprint] of the parameters of the run.
 */
class NightProcessor
    @Suppress("LongParameterList") // Its collaborators, each a fake in the tests.
    @Inject
    constructor(
        private val settings: SettingsRepository,
        private val reader: HealthDataReader,
        private val nights: NightRepository,
        private val notifier: NightNotifier,
        private val runLog: RunLog,
        private val clock: Clock,
        private val zone: ZoneSource,
        private val reevaluator: NightReevaluator,
        private val diagnostics: NightDiagnostics,
    ) {
        /** Runs one check. Throws [HcError.RateLimited] / [HcError.Other]; refusals become [ProcessOutcome.Blocked]. */
        suspend fun process(): ProcessOutcome {
            val s = settings.settings.first()
            val params = s.algoParams()
            reevaluator.reevaluateIfNeeded(params)
            val now = clock.millis()
            diagnostics.log("check at ${hm(now)}")
            return try {
                Run(s, params, now).execute()
            } catch (e: HcError) {
                val reason = blockReasonOf(e)
                diagnostics.log(if (reason == null) "check failed: ${e.javaClass.simpleName}" else "check blocked: ${reason.name}")
                if (reason == null) throw e
                runLog.log(RunLogKind.BLOCKED, "night check blocked: ${reason.name}")
                ProcessOutcome.Blocked(reason)
            }
        }

        /** State of one run. */
        private inner class Run(
            val settings: AppSettings,
            val params: AlgoParams,
            val now: Long,
        ) {
            val source = settings.sourcePackage
            val fingerprint = AlgoFingerprint.of(params)
            val stored = mutableMapOf<String, NightRecord>()
            var provided: Set<ChannelId> = emptySet()
            val reevaluated = mutableListOf<String>()

            suspend fun execute(): ProcessOutcome {
                if (reader.availability() != HcAvailability.AVAILABLE) throw HcError.Unavailable("night check")
                val missing = HcPermissions.missingRequired(reader.grantedPermissions())
                if (missing.isNotEmpty()) throw HcError.PermissionMissing(missing)
                provided = reader.channelsOf(source, now - DISCOVERY_MS, now)
                val from = now - (params.lookbackDays + 2L) * DAY_MS
                val candidates = NightSelector.select(reader.readSessions(source, from, now), params).filter { it.endMs <= now }
                nights.since(source, localDate(from)).associateByTo(stored) { it.date }
                val target = candidates.lastOrNull { it.totalMinutes >= params.minNightMinutes }?.takeIf { it.date !in stored }
                traceSelection(candidates, target)
                val verdict = target?.let { judge(it) }
                val complete = verdict != null && verdict.readiness != Readiness.WAIT
                fillAndReevaluate(candidates, target, complete)
                return when {
                    target == null -> waitForNewNight(candidates)
                    !complete -> retry(target, checkNotNull(verdict))
                    else -> evaluateTarget(target)
                }
            }

            /** Diagnostics: the newest night seen and why it is or is not the target. */
            private fun traceSelection(
                candidates: List<NightCandidate>,
                target: NightCandidate?,
            ) {
                val newest = candidates.lastOrNull()
                if (newest == null) {
                    diagnostics.log("no night of the source since ${localDate(now - (params.lookbackDays + 2L) * DAY_MS)}")
                    return
                }
                val ambiguous = if (newest.ambiguous) ", ambiguous" else ""
                diagnostics.log("newest night ${newest.date}: ${hm(newest.startMs)}-${hm(newest.endMs)}$ambiguous")
                when {
                    target != null -> diagnostics.log("target night ${target.date}")
                    newest.totalMinutes < params.minNightMinutes -> diagnostics.log("night ${newest.date} too short to be the target")
                    else -> diagnostics.log("night ${newest.date} already stored: no new night")
                }
            }

            /** Older nights: fill in unknown ones (final, silent) and re-evaluate due ones, oldest first. */
            private suspend fun fillAndReevaluate(
                candidates: List<NightCandidate>,
                target: NightCandidate?,
                targetComplete: Boolean,
            ) {
                val limitDate = target?.date ?: localDate(now)
                val byDate = candidates.associateBy { it.date }
                val dates =
                    (
                        byDate.keys.filter { it < limitDate && it !in stored && byDate.getValue(it).endMs <= now - capMs() } +
                            stored.values.filter { !it.final && it.date < limitDate && reevaluationDue(it, targetComplete) }.map { it.date }
                    ).distinct()
                        .sorted()
                for (date in dates) {
                    val old = stored[date]
                    val candidate = byDate[date]
                    when {
                        old == null -> {
                            diagnostics.log("night $date filled in as history")
                            store(compute(checkNotNull(candidate)), final = true, count = 1, previous = null)
                        }

                        candidate == null -> {
                            diagnostics.log("night $date no longer in the source: kept as final")
                            store(old.copy(final = true))
                        }

                        else -> {
                            diagnostics.log("night $date re-evaluated")
                            reevaluate(old, candidate)
                        }
                    }
                }
            }

            private fun reevaluationDue(
                record: NightRecord,
                newerNightComplete: Boolean,
            ): Boolean = newerNightComplete || now >= record.summary.endMs + REEVALUATE_AFTER_MS

            private suspend fun reevaluate(
                old: NightRecord,
                candidate: NightCandidate,
            ) {
                val computed = compute(candidate)
                val evaluation = computed.evaluation
                val changedInto = evaluation.status == NightStatus.DEVIATIONS && old.evaluation.status != NightStatus.DEVIATIONS
                val notify =
                    changedInto && old.notifiedStatus != NightStatus.DEVIATIONS && settings.notificationsEnabled && recent(old.date)
                reevaluated += old.date
                runLog.log(
                    RunLogKind.EVALUATED,
                    "night ${old.date} re-evaluated: ${old.evaluation.status.name} -> ${evaluation.status.name}",
                )
                store(computed, final = true, count = old.evaluationCount + 1, previous = old, notify = notify)
            }

            private suspend fun evaluateTarget(target: NightCandidate): ProcessOutcome {
                val computed = compute(target)
                val evaluation = computed.evaluation
                val notify = evaluation.notify && settings.notificationsEnabled && recent(target.date)
                runLog.log(
                    RunLogKind.EVALUATED,
                    "night ${target.date} evaluated: ${evaluation.status.name}, ${evaluation.deviating} outside",
                )
                val record = store(computed, final = false, count = 1, previous = null, notify = notify)
                diagnostics.log("night ${target.date} evaluated and stored${if (record.notifiedAtMs != null) ", notified" else ""}")
                return ProcessOutcome.Evaluated(target.date, evaluation.status, record.notifiedAtMs != null, reevaluated.toList())
            }

            private suspend fun judge(target: NightCandidate): CompletenessVerdict {
                // Read up to now for the morning's step records, but only the night's own records count as writes:
                // the source keeps writing steps and all-day heart rate, which would never let the night look quiet.
                val data = reader.readNight(source, target.startMs, now, writesBeforeMs = target.endMs + 1)
                val night = target.withSamples(data.channels)
                val cp = COMPLETENESS
                val upFrom = target.endMs + (cp.upAfterEndMinutes * MIN_MS).toLong()
                val input =
                    CompletenessInput(
                        nowMs = now,
                        nightEndMs = target.endMs,
                        providedChannels = provided - HcChannels.STEPS_ID,
                        lastSampleMs = night.channels.mapNotNull { (k, v) -> v.maxOfOrNull { it.timeMs }?.let { k to it } }.toMap(),
                        sourceWritesSteps = HcChannels.STEPS_ID in provided,
                        firstBriskStepAfterEndMs =
                            data.steps
                                .firstOrNull {
                                    it.timeMs >= upFrom && it.value >= cp.upStepsPerMinute
                                }?.timeMs,
                        lastWriteMs = data.lastModifiedMs ?: target.endMs,
                    )
                return Completeness.judge(input, cp).also { traceVerdict(target, input, it) }
            }

            private fun traceVerdict(
                target: NightCandidate,
                input: CompletenessInput,
                verdict: CompletenessVerdict,
            ) {
                val up = if (input.sourceWritesSteps) "up ${hm(input.firstBriskStepAfterEndMs)}" else "no step records"
                val channels =
                    listOf(ChannelId.HEART_RATE, ChannelId.RESPIRATORY_RATE, ChannelId.SPO2)
                        .filter { it in input.providedChannels }
                        .joinToString { "${it.key} ${hm(input.lastSampleMs[it])}" }
                val reasons = if (verdict.reasons.isEmpty()) "" else " (${verdict.reasons.joinToString { it.name }})"
                diagnostics.log(
                    "night ${target.date} end ${hm(target.endMs)}: $up, $channels, last night write ${hm(input.lastWriteMs)}" +
                        " -> ${verdict.readiness.name}$reasons",
                )
            }

            private suspend fun retry(
                target: NightCandidate,
                verdict: CompletenessVerdict,
            ): ProcessOutcome {
                val capAt = target.endMs + capMs()
                val at = maxOf(now + MIN_RETRY_MS, minOf(now + WAIT_STEP_MS, capAt))
                runLog.log(RunLogKind.WAITING, "night ${target.date} not complete: ${verdict.reasons.joinToString { it.name }}")
                diagnostics.log("night ${target.date} waits: next check ${hm(at)}")
                return ProcessOutcome.Retry(at, verdict.reasons, target.date)
            }

            private fun waitForNewNight(candidates: List<NightCandidate>): ProcessOutcome {
                val today = localDate(now)
                val localTime = Instant.ofEpochMilli(now).atZone(zone.zone()).toLocalTime()
                val waiting = (candidates.lastOrNull()?.date ?: "") < today && localTime < NO_NIGHT_WAIT_UNTIL
                return if (waiting) {
                    diagnostics.log("no new night yet: next check ${hm(now + NO_NIGHT_RETRY_MS)}")
                    ProcessOutcome.Retry(now + NO_NIGHT_RETRY_MS, emptyList(), date = null)
                } else {
                    diagnostics.log("nothing new: next check at the daily time")
                    ProcessOutcome.NothingNew(reevaluated.toList())
                }
            }

            /** Summary of [candidate] from Health Connect, evaluated against the stored history. */
            private suspend fun compute(candidate: NightCandidate): Computed {
                val data = reader.readNight(source, candidate.startMs, candidate.endMs + 1)
                val summary = MetricExtractor.summarize(candidate.withSamples(data.channels), provided - HcChannels.STEPS_ID, params)
                return Computed(summary, Evaluator.evaluate(summary, stored.values.map { it.summary }, params))
            }

            private suspend fun store(
                computed: Computed,
                final: Boolean,
                count: Int,
                previous: NightRecord?,
                notify: Boolean = false,
            ): NightRecord {
                val (summary, evaluation) = computed
                val shown = notify && notifier.show(NightNotice.of(evaluation))
                if (shown) runLog.log(RunLogKind.NOTIFIED, "night ${evaluation.date} notified: ${evaluation.status.name}")
                val record =
                    NightRecord(
                        sourcePackage = source,
                        date = evaluation.date,
                        summary = summary,
                        evaluation = evaluation,
                        computedAtMs = now,
                        final = final,
                        evaluationCount = count,
                        notifiedAtMs = if (shown) now else previous?.notifiedAtMs,
                        notifiedStatus = if (shown) evaluation.status else previous?.notifiedStatus,
                        algoFingerprint = fingerprint,
                    )
                return store(record)
            }

            private suspend fun store(record: NightRecord): NightRecord {
                nights.upsert(record)
                stored[record.date] = record
                return record
            }

            /** Only nights that woke today or yesterday are notified; a late sync of older days updates silently. */
            private fun recent(date: String): Boolean = date >= LocalDate.parse(localDate(now)).minusDays(1).toString()

            private fun capMs(): Long = (COMPLETENESS.capHours * HOUR_MS).toLong()

            private fun localDate(ms: Long): String =
                Instant
                    .ofEpochMilli(ms)
                    .atZone(zone.zone())
                    .toLocalDate()
                    .toString()
        }

        /** Local `HH:mm` of [ms] for the diagnostics ("none" when null). */
        private fun hm(ms: Long?): String = ms?.let { Instant.ofEpochMilli(it).atZone(zone.zone()).format(HH_MM) } ?: "none"

        /** A summary with its evaluation. */
        private data class Computed(
            val summary: NightSummary,
            val evaluation: NightEvaluation,
        )

        /** Timing constants. */
        companion object {
            internal const val DAY_MS = 24 * 60 * 60_000L

            /** Thresholds of the night-complete rule. */
            val COMPLETENESS: CompletenessParams = CompletenessParams()

            /** Never check again sooner than this. */
            const val MIN_RETRY_MS: Long = 15 * 60_000L

            /** Normal wait between checks of an incomplete night. */
            const val WAIT_STEP_MS: Long = 30 * 60_000L

            /** Wait between checks while no new night is visible in the morning. */
            const val NO_NIGHT_RETRY_MS: Long = 60 * 60_000L

            /** Local time after which a day without a visible new night is given up. */
            val NO_NIGHT_WAIT_UNTIL: LocalTime = LocalTime.of(14, 0)

            /** A non-final night is re-evaluated at the latest this long after its end. */
            const val REEVALUATE_AFTER_MS: Long = 36 * 60 * 60_000L

            /** Which channels the source writes is decided from the last 30 days. */
            const val DISCOVERY_MS: Long = 30 * DAY_MS

            internal const val MIN_MS = 60_000.0
            internal const val HOUR_MS = 3_600_000.0

            private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        }
    }

/** First session start of the candidate. */
internal val NightCandidate.startMs: Long get() = sessions.minOf { it.startMs }

/** Last session end of the candidate. */
internal val NightCandidate.endMs: Long get() = sessions.maxOf { it.endMs }
