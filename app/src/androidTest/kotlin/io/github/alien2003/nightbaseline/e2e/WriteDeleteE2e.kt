package io.github.alien2003.nightbaseline.e2e

import android.os.SystemClock
import androidx.health.connect.client.HealthConnectClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.alien2003.nightbaseline.e2e.safety.TestRecordWriter
import io.github.alien2003.nightbaseline.e2e.support.E2eStatusRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * THE ONLY WRITE of the suite. Runs as the test package itself (SelfE2eRunner, its own process and data origin), with
 * the test-only WRITE_STEPS permission that only the test APK declares (the app's manifest stays read-only). Through
 * [TestRecordWriter] it writes one step record (1 step, 1 January 2000 03:00–03:01 UTC, clientRecordId
 * `nb-e2e:<uuid>`), reads it back, deletes it by its id at once in `finally`, and checks that it is gone.
 *
 * Without WRITE_STEPS granted to the test package the test is SKIPPED: the suite never asks for the permission
 * itself; the owner grants it explicitly (`run-device-tests.sh --grant-write`). The runner runs HcSnapshotE2e#before
 * right before this class and HcSnapshotE2e#after right after it (in the app's process, which can see other apps'
 * data), and runs this class only when the "before" snapshot passed.
 */
@RunWith(AndroidJUnit4::class)
class WriteDeleteE2e {
    @get:Rule
    val status = E2eStatusRule(appProcess = false)

    @Test
    fun writeReadBackDeleteOneTestRecord() {
        val context = E2e.self
        assumeTrue(
            "Health Connect is not available to the test package",
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE,
        )
        val client = HealthConnectClient.getOrCreate(context)
        val granted = runBlocking { client.permissionController.getGrantedPermissions() }
        status.flag("writeGranted", WRITE_STEPS in granted)
        assumeTrue(
            "WRITE_STEPS is not granted to $E2E_PACKAGE: the single test write is skipped. The suite never requests " +
                "it; grant it for one run with tools/device-test/run-device-tests.sh --grant-write",
            WRITE_STEPS in granted,
        )
        // The test package holds nothing but this one permission.
        assertEquals("health permissions of the test package", setOf(WRITE_STEPS), granted.filter { it.startsWith(HEALTH_PREFIX) }.toSet())

        val writer = TestRecordWriter(client)
        runBlocking {
            status.count("leftoversDeletedBefore", writer.deleteLeftovers().toLong())
            assertEquals("the test package's records before the write", 0, writer.ownRecords().size)
        }

        val clientRecordId = E2e.TEST_NAMESPACE + UUID.randomUUID()
        val writtenAt = SystemClock.elapsedRealtime()
        var deletedAt = 0L
        try {
            val id = runBlocking { writer.insertTestRecord(clientRecordId) }
            status.count("inserted", 1)
            val back = runBlocking { writer.ownRecords() }
            status.count("readBack", back.size.toLong())
            assertEquals("the test package's records after the write", 1, back.size)
            val record = back.single()
            assertEquals("id read back", id, record.metadata.id)
            assertEquals("clientRecordId read back", clientRecordId, record.metadata.clientRecordId)
            assertEquals("start read back", TestRecordWriter.START, record.startTime)
            assertEquals("end read back", TestRecordWriter.END, record.endTime)
            assertEquals("synthetic step count read back", 1L, record.count)
        } finally {
            // Always, and at once: delete by id; the cleanup's own failure must not hide the test's.
            runBlocking { writer.deleteInserted() }
            deletedAt = SystemClock.elapsedRealtime()
        }
        val lifetime = deletedAt - writtenAt
        status.count("lifetimeMs", lifetime)
        val left = runBlocking { writer.ownRecords() }
        status.count("leftAfter", left.size.toLong())
        assertTrue("the test record still exists after the delete", left.isEmpty())
        assertTrue("the test record lived $lifetime ms (more than $MAX_LIFETIME_MS)", lifetime <= MAX_LIFETIME_MS)
    }

    private companion object {
        const val HEALTH_PREFIX = "android.permission.health."
        const val WRITE_STEPS = HEALTH_PREFIX + "WRITE_STEPS"
        const val E2E_PACKAGE = E2e.TEST_PACKAGE
        const val MAX_LIFETIME_MS = 60_000L
    }
}
