package io.github.alien2003.nightbaseline.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.alien2003.nightbaseline.e2e.safety.HcSnapshot
import io.github.alien2003.nightbaseline.e2e.safety.ReadBudget
import io.github.alien2003.nightbaseline.e2e.safety.SnapshotDiff
import io.github.alien2003.nightbaseline.e2e.support.E2eStatusRule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The snapshots around the single test write (WriteDeleteE2e), taken inside the app's process with the app's READ
 * permissions, so that other apps' data is visible (the test package itself may only write). The runner calls
 * [before] right before WriteDeleteE2e and [after] right after it; [after] compares:
 *
 * - every other origin's records in the fixed window of the test record (late December 1999 to early January 2000)
 *   must be identical (count, newest lastModified, digest of ids and lastModified): a difference FAILS;
 * - no record with a clientRecordId in `nb-e2e:` may be left, of any origin, in any window;
 * - changes in the last 7 days are listed as warnings (other apps sync while the suite runs).
 *
 * Both write value-free JSON (`snapshot-before.json`, `snapshot-after.json`, `snapshot-compare.json`).
 */
@RunWith(AndroidJUnit4::class)
class HcSnapshotE2e {
    @get:Rule
    val status = E2eStatusRule()

    private val budget = ReadBudget.fromArgs(DEFAULT_BUDGET)

    /**
     * The comparison.
     *
     * @property fixedChanges changes in the fixed window (must be empty).
     * @property recentChanges changes in the last 7 days (warnings).
     * @property testRecordsLeft `nb-e2e:` records seen after the write (must be 0).
     * @property runId the runner's run id.
     */
    @Serializable
    data class Comparison(
        val fixedChanges: List<String>,
        val recentChanges: List<String>,
        val testRecordsLeft: Int,
        val runId: String,
    )

    @Test
    fun before() {
        val snapshot = take()
        assumeTrue(
            "other apps' data is not visible to the app's process (READ_HEALTH_DATA_IN_BACKGROUND not granted): " +
                "the snapshot cannot show that they are unchanged, so the single write is not attempted",
            snapshot.seesOtherApps,
        )
        assertEquals("records of the suite (${E2e.TEST_NAMESPACE}) already in Health Connect before the write", 0, snapshot.testRecords)
        E2e.writeJson(BEFORE, snapshot)
    }

    @Test
    fun after() {
        val before = E2e.readJson<HcSnapshot>(BEFORE)
        assertNotNull("no snapshot before the write in this run", before)
        checkNotNull(before)
        assertEquals("the snapshot before the write is from another run", E2e.runId, before.runId)
        val after = take()
        E2e.writeJson(AFTER, after)
        val diff = SnapshotDiff.of(before, after)
        E2e.writeJson(COMPARE, Comparison(diff.fixedChanges, diff.recentChanges, after.testRecords, E2e.runId))
        status.count("fixedChanges", diff.fixedChanges.size.toLong())
        status.count("recentChanges", diff.recentChanges.size.toLong())
        status.count("testRecordsLeft", after.testRecords.toLong())
        diff.recentChanges.forEach { E2e.progress("warning (another app synced?): $it") }
        assertEquals(
            "the permissions of the two snapshots differ",
            listOf(before.canReadSteps, before.canReadSleep),
            listOf(after.canReadSteps, after.canReadSleep),
        )
        assertEquals("records of the suite (${E2e.TEST_NAMESPACE}) left in Health Connect", 0, after.testRecords)
        assertTrue("other apps' data in the test window changed: ${diff.fixedChanges}", diff.fixedChanges.isEmpty())
    }

    private fun take(): HcSnapshot {
        val snapshot = runBlocking { HcSnapshot.take(E2e.app, budget) }
        status.count("reads", snapshot.reads.toLong())
        status.count("entries", snapshot.entries.size.toLong())
        status.count("records", snapshot.entries.sumOf { it.count }.toLong())
        status.flag("canReadSteps", snapshot.canReadSteps)
        status.flag("canReadSleep", snapshot.canReadSleep)
        status.flag("canReadHistory", snapshot.canReadHistory)
        status.flag("seesOtherApps", snapshot.seesOtherApps)
        assertTrue("the app can read neither steps nor sleep: no snapshot possible", snapshot.canReadSteps || snapshot.canReadSleep)
        return snapshot
    }

    private companion object {
        const val DEFAULT_BUDGET = 100
        const val BEFORE = "snapshot-before.json"
        const val AFTER = "snapshot-after.json"
        const val COMPARE = "snapshot-compare.json"
    }
}
