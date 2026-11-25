package io.github.alien2003.nightbaseline.data.local

import io.github.alien2003.nightbaseline.algo.NightEvaluation
import io.github.alien2003.nightbaseline.algo.NightStatus
import io.github.alien2003.nightbaseline.algo.NightSummary
import io.github.alien2003.nightbaseline.data.local.db.NightDao
import io.github.alien2003.nightbaseline.data.local.db.NightEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A stored, evaluated night.
 *
 * Lifecycle: the daily run stores a night with `final = false` and `evaluationCount = 1`; one re-evaluation after the
 * next morning (sources rewrite the previous day) sets `final = true`. The history backfill stores nights as final
 * at once.
 */
data class NightRecord(
    /** Source app package. */
    val sourcePackage: String,
    /** Local wake date (ISO); equals [NightSummary.date]. */
    val date: String,
    /** Metric values. */
    val summary: NightSummary,
    /** Evaluation against the personal baseline. */
    val evaluation: NightEvaluation,
    /** When [evaluation] was computed, epoch ms. */
    val computedAtMs: Long,
    /** No further re-evaluation is due. */
    val final: Boolean,
    /** How many times the night was evaluated. */
    val evaluationCount: Int,
    /** When a notification for the night was shown, epoch ms. */
    val notifiedAtMs: Long? = null,
    /** The status the shown notification was about. */
    val notifiedStatus: NightStatus? = null,
)

/** Stored nights, per source. */
interface NightRepository {
    /** The night of [date] from [sourcePackage], if stored. */
    suspend fun get(
        sourcePackage: String,
        date: String,
    ): NightRecord?

    /** Nights of [sourcePackage] on or after [fromDate] (ISO), oldest first. */
    suspend fun since(
        sourcePackage: String,
        fromDate: String,
    ): List<NightRecord>

    /** Inserts or replaces the night with the same source and date. */
    suspend fun upsert(record: NightRecord)

    /** Whether any night of [sourcePackage] is stored. */
    suspend fun hasAny(sourcePackage: String): Boolean

    /** Every night of [sourcePackage], newest first, updated on change. */
    fun observe(sourcePackage: String): Flow<List<NightRecord>>
}

/** JSON of the stored :core-algo types; unknown keys are ignored so older rows survive new fields. */
object NightJson {
    /** The configured [Json]. */
    val json: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    /** Row of [record]. */
    fun toEntity(record: NightRecord): NightEntity =
        NightEntity(
            sourcePackage = record.sourcePackage,
            date = record.date,
            summaryJson = json.encodeToString(NightSummary.serializer(), record.summary),
            evaluationJson = json.encodeToString(NightEvaluation.serializer(), record.evaluation),
            computedAtMs = record.computedAtMs,
            final = record.final,
            evaluationCount = record.evaluationCount,
            notifiedAtMs = record.notifiedAtMs,
            notifiedStatus = record.notifiedStatus?.name,
        )

    /** Record of [entity]. */
    fun fromEntity(entity: NightEntity): NightRecord =
        NightRecord(
            sourcePackage = entity.sourcePackage,
            date = entity.date,
            summary = json.decodeFromString(NightSummary.serializer(), entity.summaryJson),
            evaluation = json.decodeFromString(NightEvaluation.serializer(), entity.evaluationJson),
            computedAtMs = entity.computedAtMs,
            final = entity.final,
            evaluationCount = entity.evaluationCount,
            notifiedAtMs = entity.notifiedAtMs,
            notifiedStatus = NightStatus.entries.firstOrNull { it.name == entity.notifiedStatus },
        )
}

/** [NightRepository] over Room. */
@Singleton
class RoomNightRepository
    @Inject
    constructor(
        private val dao: NightDao,
    ) : NightRepository {
        override suspend fun get(
            sourcePackage: String,
            date: String,
        ): NightRecord? = dao.get(sourcePackage, date)?.let(NightJson::fromEntity)

        override suspend fun since(
            sourcePackage: String,
            fromDate: String,
        ): List<NightRecord> = dao.since(sourcePackage, fromDate).map(NightJson::fromEntity)

        override suspend fun upsert(record: NightRecord) {
            dao.upsert(NightJson.toEntity(record))
        }

        override suspend fun hasAny(sourcePackage: String): Boolean = dao.count(sourcePackage) > 0

        override fun observe(sourcePackage: String): Flow<List<NightRecord>> =
            dao.observe(sourcePackage).map { rows -> rows.map(NightJson::fromEntity) }
    }
