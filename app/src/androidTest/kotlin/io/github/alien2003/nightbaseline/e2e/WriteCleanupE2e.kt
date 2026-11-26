package io.github.alien2003.nightbaseline.e2e

import androidx.health.connect.client.HealthConnectClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.alien2003.nightbaseline.e2e.safety.TestRecordWriter
import io.github.alien2003.nightbaseline.e2e.support.E2eStatusRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The runner's safety net after WriteDeleteE2e (also when that step was interrupted or timed out), before the test
 * package is uninstalled: Health Connect keeps an uninstalled package's records, so every `nb-e2e:` record the test
 * package may have left in its test window is deleted now (by id, own data origin only, through [TestRecordWriter])
 * and none may remain. Runs as the test package (SelfE2eRunner); without WRITE_STEPS nothing can have been written,
 * and the test is skipped.
 */
@RunWith(AndroidJUnit4::class)
class WriteCleanupE2e {
    @get:Rule
    val status = E2eStatusRule(appProcess = false)

    @Test
    fun noTestRecordIsLeft() {
        val context = E2e.self
        assumeTrue("Health Connect is not available", HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE)
        val client = HealthConnectClient.getOrCreate(context)
        val granted = runBlocking { client.permissionController.getGrantedPermissions() }
        assumeTrue("WRITE_STEPS is not granted to the test package: it cannot have written anything", WRITE_STEPS in granted)
        val writer = TestRecordWriter(client)
        val deleted = runBlocking { writer.deleteLeftovers() }
        status.count("leftoversDeleted", deleted.toLong())
        if (deleted > 0) E2e.progress("deleted $deleted test record(s) an interrupted step had left")
        val left = runBlocking { writer.ownRecords() }
        status.count("leftAfter", left.size.toLong())
        assertEquals("records of the test package left in Health Connect", 0, left.size)
    }

    private companion object {
        const val WRITE_STEPS = "android.permission.health.WRITE_STEPS"
    }
}
