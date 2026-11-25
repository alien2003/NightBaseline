package io.github.alien2003.nightbaseline.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Access to `nights`. */
@Dao
interface NightDao {
    /** Inserts or replaces the night with the same (sourcePackage, date). */
    @Upsert
    suspend fun upsert(night: NightEntity)

    /** The night of [date] from [sourcePackage], if stored. */
    @Query("SELECT * FROM nights WHERE sourcePackage = :sourcePackage AND date = :date")
    suspend fun get(
        sourcePackage: String,
        date: String,
    ): NightEntity?

    /** Nights of [sourcePackage] on or after [fromDate] (ISO dates sort as text), oldest first. */
    @Query("SELECT * FROM nights WHERE sourcePackage = :sourcePackage AND date >= :fromDate ORDER BY date")
    suspend fun since(
        sourcePackage: String,
        fromDate: String,
    ): List<NightEntity>

    /** Every night of [sourcePackage], newest first, updated on every change. */
    @Query("SELECT * FROM nights WHERE sourcePackage = :sourcePackage ORDER BY date DESC")
    fun observe(sourcePackage: String): Flow<List<NightEntity>>

    /** Every night of every source, newest first, updated on every change. */
    @Query("SELECT * FROM nights ORDER BY date DESC, sourcePackage")
    fun observeAll(): Flow<List<NightEntity>>

    /** Number of stored nights of [sourcePackage]. */
    @Query("SELECT COUNT(*) FROM nights WHERE sourcePackage = :sourcePackage")
    suspend fun count(sourcePackage: String): Int
}

/** Access to `run_log`. */
@Dao
interface RunLogDao {
    /** Appends one line. */
    @Insert
    suspend fun insert(entry: RunLogEntity)

    /** The newest [limit] lines, newest first. */
    @Query("SELECT * FROM run_log ORDER BY timeMs DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<RunLogEntity>

    /** Deletes every line older than [beforeMs]. */
    @Query("DELETE FROM run_log WHERE timeMs < :beforeMs")
    suspend fun deleteOlderThan(beforeMs: Long)
}
