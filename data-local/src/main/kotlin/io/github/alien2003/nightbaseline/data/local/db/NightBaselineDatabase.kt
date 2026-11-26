package io.github.alien2003.nightbaseline.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** The app database. Schema history is exported to `data-local/schemas` (a schema change needs a migration). */
@Database(entities = [NightEntity::class, RunLogEntity::class], version = 2, exportSchema = true)
abstract class NightBaselineDatabase : RoomDatabase() {
    /** DAO of `nights`. */
    abstract fun nightDao(): NightDao

    /** DAO of `run_log`. */
    abstract fun runLogDao(): RunLogDao

    /** Constants and migrations. */
    companion object {
        /** Database file name. */
        const val FILE_NAME: String = "nightbaseline.db"

        /**
         * 1 -> 2: `nights.algoFingerprint` (nullable). Existing rows get null, which never equals a current fingerprint,
         * so they are re-evaluated from their stored summaries on the next start.
         */
        val MIGRATION_1_2: Migration =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE nights ADD COLUMN algoFingerprint TEXT")
                }
            }
    }
}
