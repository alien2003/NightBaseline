package io.github.alien2003.nightbaseline.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One evaluated main night of one source. The summary and the evaluation are stored as JSON of the :core-algo types
 * ([NightJson]) so that new metrics need no schema change.
 */
@Entity(tableName = "nights", primaryKeys = ["sourcePackage", "date"])
data class NightEntity(
    /** Package of the source app that wrote the night. */
    val sourcePackage: String,
    /** Local wake date, ISO `yyyy-MM-dd`. */
    val date: String,
    /** `NightSummary` as JSON. */
    val summaryJson: String,
    /** `NightEvaluation` as JSON. */
    val evaluationJson: String,
    /** When the evaluation was computed, epoch ms. */
    val computedAtMs: Long,
    /** No further re-evaluation is due (the night is settled in the source). */
    val final: Boolean,
    /** How many times the night was evaluated. */
    val evaluationCount: Int,
    /** When a notification for the night was shown, epoch ms. */
    val notifiedAtMs: Long? = null,
    /** `NightStatus` name the shown notification was about. */
    val notifiedStatus: String? = null,
)

/**
 * One line of the run log. Messages describe what a run did (dates, statuses, counts, error kinds) and never contain
 * measurement values.
 */
@Entity(tableName = "run_log", indices = [Index("timeMs")])
data class RunLogEntity(
    /** Row id. */
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Epoch ms. */
    val timeMs: Long,
    /** `RunLogKind` name. */
    val kind: String,
    /** Value-free message. */
    @ColumnInfo(name = "message") val message: String,
)
