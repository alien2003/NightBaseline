package io.github.alien2003.nightbaseline.data.local.db

import androidx.room.Room
import io.github.alien2003.nightbaseline.algo.NightStatus
import io.github.alien2003.nightbaseline.algo.Pipeline
import io.github.alien2003.nightbaseline.algo.SyntheticNights
import io.github.alien2003.nightbaseline.data.local.NightRecord
import io.github.alien2003.nightbaseline.data.local.RoomNightRepository
import io.github.alien2003.nightbaseline.data.local.RoomRunLog
import io.github.alien2003.nightbaseline.data.local.RunLogKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Room DAO and repository tests on Robolectric's real SQLite (JUnit 4 through the Vintage engine). */
@RunWith(RobolectricTestRunner::class)
class NightDaoTest {
    private lateinit var db: NightBaselineDatabase

    @Before
    fun open() {
        db =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), NightBaselineDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun close() {
        db.close()
    }

    private fun records(): List<NightRecord> {
        val gen = SyntheticNights(SyntheticNights.Config(seed = 3, nights = 12))
        val (sessions, channels) = gen.flatten(gen.generate())
        return Pipeline.run(sessions, channels).map { (s, e) ->
            NightRecord(SOURCE, s.date, s, e, computedAtMs = 1_000L, final = false, evaluationCount = 1)
        }
    }

    @Test
    fun recordsRoundTripThroughJsonColumns() =
        runTest {
            val repo = RoomNightRepository(db.nightDao())
            val input = records()
            input.forEach { repo.upsert(it) }

            val back = repo.since(SOURCE, "0000-01-01")
            assertEquals(input, back)
            assertEquals(input.map { it.date }, back.map { it.date })
            assertTrue(repo.hasAny(SOURCE))
            assertFalse(repo.hasAny("other.app"))
        }

    @Test
    fun upsertReplacesTheSameSourceAndDate() =
        runTest {
            val repo = RoomNightRepository(db.nightDao())
            val first = records().first()
            repo.upsert(first)
            repo.upsert(first.copy(final = true, evaluationCount = 2, notifiedAtMs = 5L, notifiedStatus = NightStatus.DEVIATIONS))

            val stored = checkNotNull(repo.get(SOURCE, first.date))
            assertTrue(stored.final)
            assertEquals(2, stored.evaluationCount)
            assertEquals(5L, stored.notifiedAtMs)
            assertEquals(NightStatus.DEVIATIONS, stored.notifiedStatus)
            assertEquals(1, db.nightDao().count(SOURCE))
            assertNull(repo.get("other.app", first.date))
        }

    @Test
    fun sinceFiltersByDateAndSourceAndObserveIsNewestFirst() =
        runTest {
            val repo = RoomNightRepository(db.nightDao())
            val input = records()
            input.forEach { repo.upsert(it) }
            repo.upsert(input.first().copy(sourcePackage = "other.app"))

            val from = input[5].date
            assertEquals(input.drop(5).map { it.date }, repo.since(SOURCE, from).map { it.date })
            assertEquals(input.map { it.date }.reversed(), repo.observe(SOURCE).first().map { it.date })
        }

    @Test
    fun runLogKeepsNewestFirstAndDropsOldLines() =
        runTest {
            var now = 0L
            val clock =
                object : Clock() {
                    override fun getZone() = ZoneOffset.UTC

                    override fun withZone(zone: java.time.ZoneId?) = this

                    override fun instant(): Instant = Instant.ofEpochMilli(now)
                }
            val log = RoomRunLog(db.runLogDao(), clock)
            log.log(RunLogKind.BACKFILL, "old")
            now = RoomRunLog.RETENTION_MS + 10
            log.log(RunLogKind.EVALUATED, "a")
            log.log(RunLogKind.NOTIFIED, "b")

            val lines = log.recent()
            assertEquals(listOf("b", "a"), lines.map { it.message })
            assertEquals(listOf(RunLogKind.NOTIFIED, RunLogKind.EVALUATED), lines.map { it.kind })
        }

    private companion object {
        const val SOURCE = "com.heytap.health.international"
    }
}
