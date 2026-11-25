package io.github.alien2003.nightbaseline.data.local

import io.github.alien2003.nightbaseline.data.local.db.RunLogDao
import io.github.alien2003.nightbaseline.data.local.db.RunLogEntity
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** What a run-log line is about. */
enum class RunLogKind {
    /** History backfill. */
    BACKFILL,

    /** A night was evaluated or re-evaluated. */
    EVALUATED,

    /** The night is not complete yet; another check is scheduled. */
    WAITING,

    /** Nothing could be read (Health Connect unavailable, permission missing). */
    BLOCKED,

    /** A notification was shown. */
    NOTIFIED,

    /** A run failed. */
    ERROR,
}

/** One run-log line. */
data class RunLogLine(
    /** Epoch ms. */
    val timeMs: Long,
    /** Kind. */
    val kind: RunLogKind,
    /** Value-free message. */
    val message: String,
)

/**
 * A small diagnostic log of the background runs, kept in the app database and shown on a debug screen. Callers pass
 * dates, statuses, counts and error kinds only: never measurement values (rule: no health values in logs).
 */
interface RunLog {
    /** Appends a line. */
    suspend fun log(
        kind: RunLogKind,
        message: String,
    )

    /** The newest [limit] lines, newest first. */
    suspend fun recent(limit: Int = DEFAULT_LIMIT): List<RunLogLine>

    /** Defaults. */
    companion object {
        /** Lines returned by [recent] by default. */
        const val DEFAULT_LIMIT: Int = 200
    }
}

/** [RunLog] over Room; lines older than [RETENTION_MS] are dropped on write. */
@Singleton
class RoomRunLog
    @Inject
    constructor(
        private val dao: RunLogDao,
        private val clock: Clock,
    ) : RunLog {
        override suspend fun log(
            kind: RunLogKind,
            message: String,
        ) {
            val now = clock.millis()
            dao.insert(RunLogEntity(timeMs = now, kind = kind.name, message = message.take(MAX_MESSAGE_LENGTH)))
            dao.deleteOlderThan(now - RETENTION_MS)
        }

        override suspend fun recent(limit: Int): List<RunLogLine> =
            dao.recent(limit).map { row ->
                RunLogLine(row.timeMs, RunLogKind.entries.firstOrNull { it.name == row.kind } ?: RunLogKind.ERROR, row.message)
            }

        /** Limits. */
        companion object {
            /** Lines are kept for 30 days. */
            const val RETENTION_MS: Long = 30L * 24 * 60 * 60 * 1000

            /** Longest stored message. */
            const val MAX_MESSAGE_LENGTH: Int = 500
        }
    }
