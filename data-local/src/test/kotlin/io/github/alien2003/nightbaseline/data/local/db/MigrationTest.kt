package io.github.alien2003.nightbaseline.data.local.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import io.github.alien2003.nightbaseline.algo.NightEvaluation
import io.github.alien2003.nightbaseline.algo.NightSummary
import io.github.alien2003.nightbaseline.algo.Pipeline
import io.github.alien2003.nightbaseline.algo.SyntheticNights
import io.github.alien2003.nightbaseline.data.local.NightJson
import io.github.alien2003.nightbaseline.data.local.NightRecord
import io.github.alien2003.nightbaseline.data.local.RoomNightRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * Migrations: a database created from the exported schema 1 (`data-local/schemas/.../1.json`) with a stored night is
 * opened by the current Room database with [NightBaselineDatabase.MIGRATION_1_2]; Room validates the migrated schema
 * against the current one, and the stored night survives with no fingerprint (so it is re-evaluated).
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var file: File

    @Before
    fun setUp() {
        file = context.getDatabasePath(NAME)
        file.parentFile?.mkdirs()
        context.deleteDatabase(NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(NAME)
    }

    @Test
    fun version1To2KeepsNightsWithoutFingerprint() =
        runTest {
            val (summary, evaluation) = night()
            createVersion1(summary, evaluation)
            val db =
                Room
                    .databaseBuilder(context, NightBaselineDatabase::class.java, NAME)
                    .addMigrations(NightBaselineDatabase.MIGRATION_1_2)
                    .allowMainThreadQueries()
                    .build()
            try {
                val record = RoomNightRepository(db.nightDao()).get(SOURCE, summary.date)
                checkNotNull(record)
                assertEquals(summary, record.summary)
                assertEquals(evaluation, record.evaluation)
                assertTrue(record.final)
                assertEquals(2, record.evaluationCount)
                assertNull(record.algoFingerprint)
                assertEquals(2, db.openHelper.readableDatabase.version)
            } finally {
                db.close()
            }
        }

    @Test
    fun fingerprintRoundTripsAfterTheMigration() =
        runTest {
            val (summary, evaluation) = night()
            createVersion1(summary, evaluation)
            val db =
                Room
                    .databaseBuilder(context, NightBaselineDatabase::class.java, NAME)
                    .addMigrations(NightBaselineDatabase.MIGRATION_1_2)
                    .allowMainThreadQueries()
                    .build()
            try {
                val repo = RoomNightRepository(db.nightDao())
                val old = checkNotNull(repo.get(SOURCE, summary.date))
                repo.upsert(old.copy(algoFingerprint = "v2-test"))
                assertEquals("v2-test", repo.get(SOURCE, summary.date)?.algoFingerprint)
            } finally {
                db.close()
            }
        }

    /** A synthetic night, never real data. */
    private fun night(): Pair<NightSummary, NightEvaluation> {
        val gen = SyntheticNights(SyntheticNights.Config(seed = 5, nights = 10))
        val (sessions, channels) = gen.flatten(gen.generate())
        return Pipeline.run(sessions, channels).last()
    }

    /** The tables of the exported schema 1 with one night in it, at user_version 1. */
    private fun createVersion1(
        summary: NightSummary,
        evaluation: NightEvaluation,
    ) {
        val schema =
            Json
                .parseToJsonElement(File(SCHEMA_1).readText())
                .jsonObject
                .getValue("database")
                .jsonObject
        val sql =
            schema.getValue("entities").jsonArray.flatMap { e ->
                val entity = e.jsonObject
                val table = entity.getValue("tableName").jsonPrimitive.content
                (
                    listOf(entity.getValue("createSql").jsonPrimitive.content) +
                        entity["indices"]?.jsonArray.orEmpty().map {
                            it.jsonObject
                                .getValue("createSql")
                                .jsonPrimitive.content
                        }
                ).map { it.replace("\${TABLE_NAME}", table) }
            } + schema.getValue("setupQueries").jsonArray.map { it.jsonPrimitive.content }
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            sql.forEach(db::execSQL)
            val row = NightJson.toEntity(NightRecord(SOURCE, summary.date, summary, evaluation, 1_000L, final = true, evaluationCount = 2))
            val values =
                ContentValues().apply {
                    put("sourcePackage", row.sourcePackage)
                    put("date", row.date)
                    put("summaryJson", row.summaryJson)
                    put("evaluationJson", row.evaluationJson)
                    put("computedAtMs", row.computedAtMs)
                    put("final", if (row.final) 1 else 0)
                    put("evaluationCount", row.evaluationCount)
                }
            db.insertOrThrow("nights", null, values)
            db.version = 1
        } finally {
            db.close()
        }
    }

    private companion object {
        const val NAME = "migration-test.db"
        const val SOURCE = "com.example.synthetic"
        const val SCHEMA_1 = "schemas/io.github.alien2003.nightbaseline.data.local.db.NightBaselineDatabase/1.json"
    }
}
