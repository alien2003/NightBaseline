package io.github.alien2003.nightbaseline.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

/** The app database. Schema history is exported to `data-local/schemas` (a schema change needs a migration). */
@Database(entities = [NightEntity::class, RunLogEntity::class], version = 1, exportSchema = true)
abstract class NightBaselineDatabase : RoomDatabase() {
    /** DAO of `nights`. */
    abstract fun nightDao(): NightDao

    /** DAO of `run_log`. */
    abstract fun runLogDao(): RunLogDao

    /** Constants. */
    companion object {
        /** Database file name. */
        const val FILE_NAME: String = "nightbaseline.db"
    }
}
