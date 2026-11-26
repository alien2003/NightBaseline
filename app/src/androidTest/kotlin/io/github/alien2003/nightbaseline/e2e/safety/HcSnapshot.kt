package io.github.alien2003.nightbaseline.e2e.safety

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.github.alien2003.nightbaseline.data.hc.ProcessImportanceForegroundState
import io.github.alien2003.nightbaseline.e2e.E2e
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.time.Instant
import kotlin.reflect.KClass

/**
 * A value-free picture of Health Connect, taken before and after the single test write (HcSnapshotE2e).
 *
 * For each record type (steps, sleep sessions) and each window, per data origin: the number of records, the newest
 * `lastModifiedTime` and a SHA-256 over the sorted `id:lastModified` pairs. Origins are kept as a hash of the package
 * name only, so the report holds no package name, no measurement value and no time of a night.
 *
 * - [FIXED] is 31 Dec 1999 – 3 Jan 2000 (UTC): the test record is written into it ([TestRecordWriter]); every
 *   origin's data there must be identical before and after, and no `nb-e2e:` record may be left. Other apps' records
 *   that old are visible only with READ_HEALTH_DATA_HISTORY.
 * - [RECENT] is the last 7 days: other apps keep syncing while the suite runs, so changes there are reported as
 *   warnings, never as failures; it shows that real nights were not touched.
 */
@Serializable
data class HcSnapshot(
    /** When it was taken, epoch ms. */
    val takenAtMs: Long,
    /** READ_STEPS granted. */
    val canReadSteps: Boolean,
    /** READ_SLEEP granted. */
    val canReadSleep: Boolean,
    /** READ_HEALTH_DATA_HISTORY granted (other apps' data in [FIXED] visible). */
    val canReadHistory: Boolean,
    /**
     * Other apps' data is visible to this process: it is in the foreground or READ_HEALTH_DATA_IN_BACKGROUND is
     * granted (without it Health Connect silently returns only the caller's own records to a background caller).
     */
    val seesOtherApps: Boolean,
    /** Read calls made (pages). */
    val reads: Int,
    /** One entry per (window, type, origin). */
    val entries: List<Entry>,
    /** Records with a clientRecordId in the suite's namespace, any origin, any window. */
    val testRecords: Int,
    /** The runner's run id. */
    val runId: String,
) {
    /**
     * Records of one origin in one window.
     *
     * @property window [FIXED] or [RECENT].
     * @property type "steps" or "sleep".
     * @property origin SHA-256 prefix of the origin's package name ("self" for the test package, "app" for the app).
     * @property count number of records.
     * @property newestLastModifiedMs newest `lastModifiedTime`, epoch ms.
     * @property digest SHA-256 over the sorted `id:lastModified` pairs.
     */
    @Serializable
    data class Entry(
        val window: String,
        val type: String,
        val origin: String,
        val count: Int,
        val newestLastModifiedMs: Long,
        val digest: String,
    ) {
        /** The key two snapshots are compared by. */
        val key: String get() = "$window/$type/$origin"
    }

    /** Windows and taking a snapshot. */
    companion object {
        /** The window of the test record. */
        const val FIXED: String = "fixed-2000"

        /** The last 7 days. */
        const val RECENT: String = "recent-7d"

        /** Start of [FIXED]. */
        val FIXED_START: Instant = Instant.parse("1999-12-31T00:00:00Z")

        /** End of [FIXED]. */
        val FIXED_END: Instant = Instant.parse("2000-01-03T00:00:00Z")

        private const val RECENT_DAYS = 7L
        private const val DAY_MS = 86_400_000L
        private const val PAGE_SIZE = 1000
        private const val ORIGIN_HASH_CHARS = 12

        /** Takes a snapshot with [context]'s Health Connect permissions; at most [budget] read calls. */
        suspend fun take(
            context: Context,
            budget: ReadBudget,
        ): HcSnapshot {
            val client = HealthConnectClient.getOrCreate(context)
            budget.countMeta()
            val granted = client.permissionController.getGrantedPermissions()
            val now = System.currentTimeMillis()
            val canSteps = PERMISSION_PREFIX + "READ_STEPS" in granted
            val canSleep = PERMISSION_PREFIX + "READ_SLEEP" in granted
            val windows =
                listOf(
                    FIXED to TimeRangeFilter.between(FIXED_START, FIXED_END),
                    RECENT to TimeRangeFilter.between(Instant.ofEpochMilli(now - RECENT_DAYS * DAY_MS), Instant.ofEpochMilli(now)),
                )
            val entries = mutableListOf<Entry>()
            var testRecords = 0
            val startSpent = budget.spent
            for ((window, filter) in windows) {
                if (canSteps) {
                    val keys = readKeys(client, StepsRecord::class, filter, budget)
                    testRecords += keys.count { it.testRecord }
                    entries += summarize(window, "steps", keys)
                }
                if (canSleep) {
                    val keys = readKeys(client, SleepSessionRecord::class, filter, budget)
                    testRecords += keys.count { it.testRecord }
                    entries += summarize(window, "sleep", keys)
                }
            }
            return HcSnapshot(
                takenAtMs = now,
                canReadSteps = canSteps,
                canReadSleep = canSleep,
                canReadHistory = PERMISSION_PREFIX + "READ_HEALTH_DATA_HISTORY" in granted,
                seesOtherApps =
                    ProcessImportanceForegroundState.isInForeground() ||
                        PERMISSION_PREFIX + "READ_HEALTH_DATA_IN_BACKGROUND" in granted,
                reads = budget.spent - startSpent,
                entries = entries.sortedBy { it.key },
                testRecords = testRecords,
                runId = E2e.runId,
            )
        }

        private const val PERMISSION_PREFIX = "android.permission.health."

        /** The value-free key of one record. */
        private data class Key(
            val origin: String,
            val id: String,
            val lastModifiedMs: Long,
            val testRecord: Boolean,
        )

        private suspend fun <T : Record> readKeys(
            client: HealthConnectClient,
            type: KClass<T>,
            filter: TimeRangeFilter,
            budget: ReadBudget,
        ): List<Key> {
            val keys = mutableListOf<Key>()
            var token: String? = null
            do {
                budget.charge("readRecords(${type.simpleName}) for the snapshot")
                val response = client.readRecords(ReadRecordsRequest(type, filter, pageSize = PAGE_SIZE, pageToken = token))
                response.records.forEach { r ->
                    val m = r.metadata
                    keys +=
                        Key(
                            origin = m.dataOrigin.packageName,
                            id = m.id,
                            lastModifiedMs = m.lastModifiedTime.toEpochMilli(),
                            testRecord = m.clientRecordId?.startsWith(E2e.TEST_NAMESPACE) == true,
                        )
                }
                token = response.pageToken?.takeIf { it.isNotEmpty() }
            } while (token != null)
            return keys
        }

        private fun summarize(
            window: String,
            type: String,
            keys: List<Key>,
        ): List<Entry> =
            keys.groupBy { it.origin }.map { (origin, list) ->
                Entry(
                    window = window,
                    type = type,
                    origin = originLabel(origin),
                    count = list.size,
                    newestLastModifiedMs = list.maxOf { it.lastModifiedMs },
                    digest = sha256(list.map { "${it.id}:${it.lastModifiedMs}" }.sorted().joinToString("\n")),
                )
            }

        /** "app", "self" or a hash prefix of the package name. */
        fun originLabel(packageName: String): String =
            when (packageName) {
                E2e.APP_PACKAGE -> "app"
                E2e.TEST_PACKAGE -> "self"
                else -> "origin-" + sha256(packageName).take(ORIGIN_HASH_CHARS)
            }

        private fun sha256(text: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(text.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}

/** What changed between two snapshots. */
data class SnapshotDiff(
    /** Changes in [HcSnapshot.FIXED] of an origin other than the test package: each one fails the comparison. */
    val fixedChanges: List<String>,
    /** Changes in [HcSnapshot.RECENT] (other apps keep syncing): warnings only. */
    val recentChanges: List<String>,
) {
    /** Comparison. */
    companion object {
        /** [before] vs [after]; the test package's own entries are left out (they are checked on their own). */
        fun of(
            before: HcSnapshot,
            after: HcSnapshot,
        ): SnapshotDiff {
            val b = before.entries.filter { it.origin != "self" }.associateBy { it.key }
            val a = after.entries.filter { it.origin != "self" }.associateBy { it.key }
            val changed =
                (b.keys + a.keys).sorted().mapNotNull { key ->
                    val x = b[key]
                    val y = a[key]
                    when {
                        x == y -> null
                        x == null -> "$key: added (${y?.count} records)"
                        y == null -> "$key: gone (was ${x.count} records)"
                        else -> "$key: ${x.count} -> ${y.count} records, digest ${if (x.digest == y.digest) "same" else "changed"}"
                    }
                }
            return SnapshotDiff(
                fixedChanges = changed.filter { it.startsWith(HcSnapshot.FIXED) },
                recentChanges = changed.filter { it.startsWith(HcSnapshot.RECENT) },
            )
        }
    }
}
