package io.github.alien2003.nightbaseline.work.process

import io.github.alien2003.nightbaseline.algo.AlgoFingerprint
import io.github.alien2003.nightbaseline.algo.AlgoParams
import io.github.alien2003.nightbaseline.algo.Evaluator
import io.github.alien2003.nightbaseline.algo.MetricExtractor
import io.github.alien2003.nightbaseline.algo.NightCandidate
import io.github.alien2003.nightbaseline.algo.NightSelector
import io.github.alien2003.nightbaseline.algo.NightSummary
import io.github.alien2003.nightbaseline.data.hc.HcChannels
import io.github.alien2003.nightbaseline.data.hc.HcError
import io.github.alien2003.nightbaseline.data.hc.HcFeature
import io.github.alien2003.nightbaseline.data.hc.HcPermissions
import io.github.alien2003.nightbaseline.data.hc.HealthDataReader
import io.github.alien2003.nightbaseline.data.local.NightRecord
import io.github.alien2003.nightbaseline.data.local.NightRepository
import io.github.alien2003.nightbaseline.data.local.RunLog
import io.github.alien2003.nightbaseline.data.local.RunLogKind
import io.github.alien2003.nightbaseline.data.local.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Clock
import javax.inject.Inject

/**
 * First-run history: when no night of the configured source is stored yet, computes and stores a summary and an
 * evaluation of every readable main night, marked final. Readable means the whole history with
 * READ_HEALTH_DATA_HISTORY (and its feature), otherwise the last [WITHOUT_HISTORY_DAYS] days (Health Connect hides
 * older data of other apps). Nights that ended less than the completeness cap ago are left to [NightProcessor] (they
 * may still change and may deserve a notification). The backfill never notifies.
 *
 * Channel data is read in chunks of at most [CHUNK_DAYS] days (one read per channel and chunk), which keeps a long
 * history far below Health Connect's read quota.
 */
class HistoryBackfill
    @Inject
    constructor(
        private val settings: SettingsRepository,
        private val reader: HealthDataReader,
        private val nights: NightRepository,
        private val runLog: RunLog,
        private val clock: Clock,
    ) {
        /** Runs the backfill if no night of the source is stored. Throws [HcError.RateLimited] / [HcError.Other]. */
        suspend fun runIfNeeded(): BackfillOutcome {
            val s = settings.settings.first()
            if (nights.hasAny(s.sourcePackage)) return BackfillOutcome.NotNeeded
            return try {
                backfill(s.sourcePackage, s.algoParams())
            } catch (e: HcError) {
                val reason = blockReasonOf(e) ?: throw e
                runLog.log(RunLogKind.BLOCKED, "backfill blocked: ${reason.name}")
                BackfillOutcome.Blocked(reason)
            }
        }

        private suspend fun backfill(
            source: String,
            params: AlgoParams,
        ): BackfillOutcome {
            val now = clock.millis()
            val whole =
                HcPermissions.READ_HEALTH_DATA_HISTORY in reader.grantedPermissions() && reader.isFeatureAvailable(HcFeature.READ_HISTORY)
            val from = if (whole) HISTORY_START_MS else now - WITHOUT_HISTORY_DAYS * DAY_MS
            val settledBefore = now - (NightProcessor.COMPLETENESS.capHours * NightProcessor.HOUR_MS).toLong()
            val provided = reader.channelsOf(source, from, now) - HcChannels.STEPS_ID
            val noAccess = reader.noAccessChannels()
            val candidates = NightSelector.select(reader.readSessions(source, from, now), params).filter { it.endMs <= settledBefore }
            val summaries = mutableListOf<NightSummary>()
            for (chunk in chunks(candidates)) {
                val data = reader.readNight(source, chunk.first().startMs, chunk.last().endMs + 1)
                chunk.forEach { c -> summaries += MetricExtractor.summarize(c.withSamples(data.channels), provided, params, noAccess) }
            }
            val fingerprint = AlgoFingerprint.of(params)
            summaries.forEach { summary ->
                val evaluation = Evaluator.evaluate(summary, summaries, params)
                nights.upsert(
                    NightRecord(
                        source,
                        summary.date,
                        summary,
                        evaluation,
                        now,
                        final = true,
                        evaluationCount = 1,
                        algoFingerprint = fingerprint,
                    ),
                )
            }
            runLog.log(RunLogKind.BACKFILL, "backfill stored ${summaries.size} nights (${if (whole) "whole history" else "last 30 days"})")
            return BackfillOutcome.Done(summaries.size, whole)
        }

        /**
         * Wake dates of the valid nights of the configured source that [runIfNeeded] leaves to the daily check (they
         * ended less than the completeness cap ago). Nothing is stored. The onboarding counts them with the stored
         * nights, so its learning progress is the count Home shows once the first check has run (the newest night
         * included). Throws [HcError].
         */
        suspend fun unsettledValidDates(): Set<String> {
            val s = settings.settings.first()
            val params = s.algoParams()
            val now = clock.millis()
            val settledBefore = now - (NightProcessor.COMPLETENESS.capHours * NightProcessor.HOUR_MS).toLong()
            val from = settledBefore - DAY_MS
            val candidates =
                NightSelector
                    .select(
                        reader.readSessions(s.sourcePackage, from, now),
                        params,
                    ).filter { it.endMs > settledBefore }
            if (candidates.isEmpty()) return emptySet()
            val provided = reader.channelsOf(s.sourcePackage, now - WITHOUT_HISTORY_DAYS * DAY_MS, now) - HcChannels.STEPS_ID
            val noAccess = reader.noAccessChannels()
            val data = reader.readNight(s.sourcePackage, candidates.first().startMs, candidates.last().endMs + 1)
            return candidates
                .map { MetricExtractor.summarize(it.withSamples(data.channels), provided, params, noAccess) }
                .filter { it.valid }
                .map { it.date }
                .toSet()
        }

        /** Consecutive candidates grouped so that each group spans at most [CHUNK_DAYS] days. */
        private fun chunks(candidates: List<NightCandidate>): List<List<NightCandidate>> {
            val out = mutableListOf<MutableList<NightCandidate>>()
            for (c in candidates) {
                val current = out.lastOrNull()
                if (current != null && c.endMs - current.first().startMs <= CHUNK_DAYS * DAY_MS) current += c else out += mutableListOf(c)
            }
            return out
        }

        /** Constants. */
        companion object {
            /** Without READ_HEALTH_DATA_HISTORY, Health Connect returns other apps' data of the last 30 days only. */
            const val WITHOUT_HISTORY_DAYS: Long = 30

            /** Longest span read at once. */
            const val CHUNK_DAYS: Long = 30

            /** Start of "the whole history" (2010-01-01T00:00Z; Health Connect did not exist before). */
            const val HISTORY_START_MS: Long = 1_262_304_000_000L

            private const val DAY_MS = NightProcessor.DAY_MS
        }
    }
