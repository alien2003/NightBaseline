package io.github.alien2003.nightbaseline.data.hc

import android.app.ActivityManager
import io.github.alien2003.nightbaseline.algo.ChannelId
import io.github.alien2003.nightbaseline.algo.Sample
import io.github.alien2003.nightbaseline.algo.SleepSession
import io.github.alien2003.nightbaseline.algo.StageInterval
import io.github.alien2003.nightbaseline.data.local.ZoneSource
import java.time.Instant

/**
 * What the rest of the app reads from Health Connect, in :core-algo types. Every read is of one source app (the
 * configured source package); PulseStages and the app itself are never read as sources.
 *
 * Reads throw [HcError]: [HcError.Unavailable] when Health Connect cannot be used, [HcError.PermissionMissing] when a
 * required permission is missing or a background read is refused, [HcError.RateLimited] / [HcError.Other] otherwise.
 */
interface HealthDataReader {
    /** SDK status. */
    fun availability(): HcAvailability

    /** Granted permissions. */
    suspend fun grantedPermissions(): Set<String>

    /** Whether [feature] is available. */
    suspend fun isFeatureAvailable(feature: HcFeature): Boolean

    /** Apps that wrote sleep sessions starting in the range (excluding PulseStages and this app), most sessions first. */
    suspend fun sleepOrigins(
        startMs: Long,
        endMs: Long,
    ): List<SleepOrigin>

    /**
     * Channels [origin] wrote in the range (one small read per granted type), including
     * [HcChannels.STEPS_ID] when it writes step records.
     */
    suspend fun channelsOf(
        origin: String,
        startMs: Long,
        endMs: Long,
    ): Set<ChannelId>

    /** Sleep sessions of [origin] overlapping the range, ordered by start. */
    suspend fun readSessions(
        origin: String,
        startMs: Long,
        endMs: Long,
    ): List<SleepSession>

    /**
     * Sessions, channel samples and step records of [origin] for the range, and the newest write time of the records
     * that have data in `[startMs, writesBeforeMs)`: a check that reads past the night (to see the step records of the
     * morning) passes the night's end there, so the source's daytime writes (steps, all-day heart rate) do not count
     * as "still writing the night".
     */
    suspend fun readNight(
        origin: String,
        startMs: Long,
        endMs: Long,
        writesBeforeMs: Long = endMs,
    ): NightData
}

/**
 * Whether Health Connect treats this app as being in the foreground. Without READ_HEALTH_DATA_IN_BACKGROUND only a
 * foreground app may read other apps' data.
 */
fun interface AppForegroundState {
    /** True while the app is in the foreground for Health Connect. */
    fun isInForeground(): Boolean
}

/**
 * [AppForegroundState] from the process importance (copied from PulseStages): an activity in front or a foreground
 * service counts; anything less important counts as background, so the check errs on the side of refusing a read.
 */
object ProcessImportanceForegroundState : AppForegroundState {
    override fun isInForeground(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE
    }
}

/**
 * [HealthDataReader] over a [HealthConnectGateway].
 *
 * - Interval records (sessions, heart-rate / skin-temperature series, steps) are read with [SERIES_LEFT_PADDING_MS]
 *   of left padding, because Health Connect filters by start time; samples are then limited to the range by their own
 *   time, sessions by overlap.
 * - Optional channels are read only when granted (and their feature is available); missing required permissions
 *   throw [HcError.PermissionMissing].
 * - Background rule (adapted from PulseStages `BackgroundRuleHealthRepository`): Health Connect on Android 14
 *   silently returns only the caller's own data to a background app without READ_HEALTH_DATA_IN_BACKGROUND, which
 *   would look like "no night". So every read checks [foreground] before (and after) the call and throws
 *   [HcError.PermissionMissing] with `background = true` instead.
 */
class HealthConnectReader(
    private val gateway: HealthConnectGateway,
    private val foreground: AppForegroundState,
    private val zone: ZoneSource,
    ownPackage: String,
) : HealthDataReader {
    private val excludedOrigins: Set<String> = EXCLUDED_ORIGINS + ownPackage

    override fun availability(): HcAvailability = gateway.availability()

    override suspend fun grantedPermissions(): Set<String> = gateway.grantedPermissions()

    override suspend fun isFeatureAvailable(feature: HcFeature): Boolean = gateway.isFeatureAvailable(feature)

    override suspend fun sleepOrigins(
        startMs: Long,
        endMs: Long,
    ): List<SleepOrigin> {
        requireReadable(setOf(HcPermissions.READ_SLEEP))
        val sessions = guarded { gateway.readSleepSessions(startMs, endMs, origin = null) }
        return sessions
            .filter { it.origin !in excludedOrigins }
            .groupBy { it.origin }
            .map { (origin, list) -> SleepOrigin(origin, list.size, list.maxOf { it.endMs }) }
            .sortedWith(compareByDescending<SleepOrigin> { it.sessions }.thenBy { it.packageName })
    }

    override suspend fun channelsOf(
        origin: String,
        startMs: Long,
        endMs: Long,
    ): Set<ChannelId> {
        requireReadable(emptySet())
        if (origin in excludedOrigins) return emptySet()
        val readable = readableChannels(HcChannels.ALL)
        return readable
            .filterTo(LinkedHashSet()) { ch ->
                guarded {
                    gateway.hasRecords(ch, startMs, endMs, origin)
                }
            }.mapTo(LinkedHashSet()) { it.id }
    }

    override suspend fun readSessions(
        origin: String,
        startMs: Long,
        endMs: Long,
    ): List<SleepSession> {
        requireReadable(setOf(HcPermissions.READ_SLEEP))
        return sessionsOf(origin, startMs, endMs).map(::toSession)
    }

    override suspend fun readNight(
        origin: String,
        startMs: Long,
        endMs: Long,
        writesBeforeMs: Long,
    ): NightData {
        requireReadable(HcPermissions.REQUIRED)
        val sessions = sessionsOf(origin, startMs, endMs)
        var lastModified = sessions.filter { it.startMs < writesBeforeMs }.maxOfOrNull { it.lastModifiedMs }
        val channels = LinkedHashMap<ChannelId, List<Sample>>()
        var steps = emptyList<Sample>()
        if (origin !in excludedOrigins && startMs < endMs) {
            for (ch in readableChannels(HcChannels.ALL)) {
                val from = if (ch.series) startMs - SERIES_LEFT_PADDING_MS else startMs
                val records = guarded { gateway.readChannel(ch, from, endMs, origin) }
                val used = records.filter { r -> r.samples.any { it.timeMs in startMs until endMs } }
                used
                    .filter { r -> r.samples.any { it.timeMs in startMs until writesBeforeMs } }
                    .maxOfOrNull { it.lastModifiedMs }
                    ?.let { m -> lastModified = maxOf(lastModified ?: m, m) }
                val samples = used.flatMap { it.samples }.filter { it.timeMs in startMs until endMs }.sortedBy { it.timeMs }
                if (ch.id == HcChannels.STEPS_ID) steps = samples else channels[ch.id] = samples
            }
        }
        return NightData(sessions.map(::toSession), channels, steps, lastModified)
    }

    private suspend fun sessionsOf(
        origin: String,
        startMs: Long,
        endMs: Long,
    ): List<HcSession> {
        if (origin in excludedOrigins || startMs >= endMs) return emptyList()
        return guarded { gateway.readSleepSessions(startMs - SERIES_LEFT_PADDING_MS, endMs, origin) }
            .filter { it.origin == origin && it.endMs > it.startMs && it.endMs > startMs && it.startMs < endMs }
            .distinctBy { it.id }
            .sortedBy { it.startMs }
    }

    private fun toSession(s: HcSession): SleepSession =
        SleepSession(
            id = s.id,
            startMs = s.startMs,
            endMs = s.endMs,
            endOffsetS =
                s.endOffsetS ?: zone
                    .zone()
                    .rules
                    .getOffset(Instant.ofEpochMilli(s.endMs))
                    .totalSeconds,
            stages = s.stages.filter { it.endMs > it.startMs }.map { StageInterval(it.startMs, it.endMs, HcStages.kindOf(it.code)) },
        )

    /** Channels whose permission is granted and whose feature (if any) is available. */
    private suspend fun readableChannels(candidates: List<HcChannel>): List<HcChannel> {
        val granted = gateway.grantedPermissions()
        return candidates.filter { ch -> ch.permission in granted && (ch.feature == null || gateway.isFeatureAvailable(ch.feature)) }
    }

    private suspend fun requireReadable(required: Set<String>) {
        if (gateway.availability() != HcAvailability.AVAILABLE) throw HcError.Unavailable("read")
        val missing = required - gateway.grantedPermissions()
        if (missing.isNotEmpty()) throw HcError.PermissionMissing(missing)
    }

    /** Runs [call] under the background rule (see the class documentation). */
    private suspend fun <T> guarded(call: suspend () -> T): T {
        val before = foreground.isInForeground()
        if (!before) requireBackgroundPermission()
        val result = call()
        if (before && !foreground.isInForeground()) requireBackgroundPermission()
        return result
    }

    private suspend fun requireBackgroundPermission() {
        if (HcPermissions.READ_HEALTH_DATA_IN_BACKGROUND !in gateway.grantedPermissions()) {
            throw HcError.PermissionMissing(setOf(HcPermissions.READ_HEALTH_DATA_IN_BACKGROUND), background = true)
        }
    }

    /** Constants. */
    companion object {
        /** A series or session may start up to a day before the window it overlaps. */
        const val SERIES_LEFT_PADDING_MS: Long = 24L * 60 * 60 * 1000

        /** Never used as sources: PulseStages derives its sessions from the same heart rate (ADR 0001). */
        val EXCLUDED_ORIGINS: Set<String> = setOf("io.github.alien2003.pulsestages")
    }
}
