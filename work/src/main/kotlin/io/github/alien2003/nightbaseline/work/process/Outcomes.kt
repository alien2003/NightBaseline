package io.github.alien2003.nightbaseline.work.process

import io.github.alien2003.nightbaseline.algo.NightStatus
import io.github.alien2003.nightbaseline.algo.WaitReason
import io.github.alien2003.nightbaseline.data.hc.HcError

/** Why nothing could be read. */
enum class BlockReason {
    /** Health Connect is not available. */
    HEALTH_CONNECT_UNAVAILABLE,

    /** A required permission (sleep, heart rate) is missing. */
    PERMISSION_MISSING,

    /** The run was in the background and READ_HEALTH_DATA_IN_BACKGROUND is not granted. */
    BACKGROUND_PERMISSION_MISSING,
}

/** What one run of [NightProcessor] did. */
sealed interface ProcessOutcome {
    /** The newest main night was evaluated (first evaluation). */
    data class Evaluated(
        /** Night date. */
        val date: String,
        /** Its status. */
        val status: NightStatus,
        /** A notification was shown for it. */
        val notified: Boolean,
        /** Older nights re-evaluated in the same run. */
        val reevaluated: List<String> = emptyList(),
    ) : ProcessOutcome

    /** No new night to evaluate (older nights may have been re-evaluated or filled in). */
    data class NothingNew(
        /** Older nights re-evaluated in the same run. */
        val reevaluated: List<String> = emptyList(),
    ) : ProcessOutcome

    /** The newest night is not complete in the source yet (or not visible yet): check again at [atMs]. */
    data class Retry(
        /** When to check again, epoch ms. */
        val atMs: Long,
        /** Why the night is not complete; empty while no new night is visible yet. */
        val reasons: List<WaitReason>,
        /** The waiting night, null while no new night is visible. */
        val date: String?,
    ) : ProcessOutcome

    /** Nothing could be read. */
    data class Blocked(
        /** Why. */
        val reason: BlockReason,
    ) : ProcessOutcome
}

/** What [HistoryBackfill] did. */
sealed interface BackfillOutcome {
    /** Nights of the source were stored before: nothing to do. */
    data object NotNeeded : BackfillOutcome

    /** History read and stored. */
    data class Done(
        /** Nights stored. */
        val nights: Int,
        /** Whether the whole history was readable (READ_HEALTH_DATA_HISTORY) or only the last 30 days. */
        val wholeHistory: Boolean,
    ) : BackfillOutcome

    /** Nothing could be read. */
    data class Blocked(
        /** Why. */
        val reason: BlockReason,
    ) : BackfillOutcome
}

/** The block reason of a read refusal, or null for errors that are not refusals. */
fun blockReasonOf(error: HcError): BlockReason? =
    when (error) {
        is HcError.Unavailable -> BlockReason.HEALTH_CONNECT_UNAVAILABLE
        is HcError.PermissionMissing -> if (error.background) BlockReason.BACKGROUND_PERMISSION_MISSING else BlockReason.PERMISSION_MISSING
        is HcError.RateLimited, is HcError.Other -> null
    }
