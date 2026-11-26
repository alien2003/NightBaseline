package io.github.alien2003.nightbaseline.e2e.safety

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.github.alien2003.nightbaseline.e2e.E2e
import java.time.Instant
import java.time.ZoneOffset

/**
 * The ONLY code of the suite that writes or deletes Health Connect data. It runs in the test package's own process
 * (SelfE2eRunner), so it writes as the test package, never as the app, and Health Connect lets a package delete only
 * its own records. On top of that it allows exactly this:
 *
 * - one insert per run: one [StepsRecord] (1 step) from [START] to [END] (1 January 2000, UTC, far outside every real
 *   night), clientRecordId [E2e.TEST_NAMESPACE] + a random UUID;
 * - deletes by record id only (never by time range), only of the id that insert returned, or of the test package's
 *   own leftovers of an interrupted earlier run (own data origin, inside the test window, clientRecordId in the
 *   namespace).
 *
 * Anything else throws [SafetyViolation].
 */
class TestRecordWriter(
    private val client: HealthConnectClient,
) {
    private var insertedIds: List<String> = emptyList()
    private var inserts = 0

    /** Inserts the test record with [clientRecordId]; returns its Health Connect id. */
    suspend fun insertTestRecord(clientRecordId: String): String {
        guard(clientRecordId.startsWith(E2e.TEST_NAMESPACE)) { "clientRecordId outside ${E2e.TEST_NAMESPACE}" }
        guard(inserts == 0) { "only one test record may be written per run" }
        inserts++
        val record =
            StepsRecord(
                startTime = START,
                startZoneOffset = ZoneOffset.UTC,
                endTime = END,
                endZoneOffset = ZoneOffset.UTC,
                count = 1,
                metadata = Metadata.manualEntry(clientRecordId = clientRecordId),
            )
        // Every id Health Connect returns is deleted again by deleteInserted, even when there is more than one.
        insertedIds = client.insertRecords(listOf(record)).recordIdsList
        guard(insertedIds.size == 1) { "Health Connect returned ${insertedIds.size} ids for one record" }
        return insertedIds.single()
    }

    /** Deletes the record [insertTestRecord] wrote, by its id (no-op when nothing was inserted). */
    suspend fun deleteInserted() {
        if (insertedIds.isEmpty()) return
        client.deleteRecords(StepsRecord::class, recordIdsList = insertedIds, clientRecordIdsList = emptyList())
        insertedIds = emptyList()
    }

    /** The test package's own step records in the test window (read as the test package: only its own data). */
    suspend fun ownRecords(): List<StepsRecord> =
        client
            .readRecords(
                ReadRecordsRequest(
                    StepsRecord::class,
                    TimeRangeFilter.between(WINDOW_START, WINDOW_END),
                    dataOriginFilter = setOf(DataOrigin(E2e.TEST_PACKAGE)),
                ),
            ).records
            .filter { it.metadata.dataOrigin.packageName == E2e.TEST_PACKAGE }

    /** Deletes the test package's own `nb-e2e:` leftovers in the test window (an interrupted earlier run); returns how many. */
    suspend fun deleteLeftovers(): Int {
        val leftovers =
            ownRecords().filter { r ->
                r.metadata.clientRecordId?.startsWith(E2e.TEST_NAMESPACE) == true && !r.startTime.isBefore(WINDOW_START) &&
                    r.endTime.isBefore(WINDOW_END)
            }
        if (leftovers.isEmpty()) return 0
        client.deleteRecords(StepsRecord::class, recordIdsList = leftovers.map { it.metadata.id }, clientRecordIdsList = emptyList())
        return leftovers.size
    }

    /** The test record's time. */
    companion object {
        /** Start of the test record: 1 January 2000, 03:00 UTC. */
        val START: Instant = Instant.parse("2000-01-01T03:00:00Z")

        /** End of the test record (one minute). */
        val END: Instant = Instant.parse("2000-01-01T03:01:00Z")

        /** The window the test package reads and cleans (the whole of 1 January 2000, UTC). */
        val WINDOW_START: Instant = Instant.parse("2000-01-01T00:00:00Z")

        /** End of that window. */
        val WINDOW_END: Instant = Instant.parse("2000-01-02T00:00:00Z")
    }
}

/** Throws [SafetyViolation] with [message] unless [condition] holds. */
private inline fun guard(
    condition: Boolean,
    message: () -> String,
) {
    if (!condition) throw SafetyViolation(message())
}

/** A write or delete the suite must never make (an Error, so no `catch (e: Exception)` swallows it). */
class SafetyViolation(
    message: String,
) : Error(message)
