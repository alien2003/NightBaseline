package io.github.alien2003.nightbaseline.data.hc

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

/**
 * The thin boundary to Health Connect: raw reads mapped to plain value types right away, so that everything above it
 * runs on the JVM without a `HealthConnectClient` (tests use `FakeHealthConnectGateway` from the test fixtures).
 *
 * Health Connect filters reads by record **start** time: every read returns the records whose start lies in
 * `[startMs, endMs)`. Callers add padding for interval records themselves.
 */
interface HealthConnectGateway {
    /** SDK status. */
    fun availability(): HcAvailability

    /** Granted permissions. */
    suspend fun grantedPermissions(): Set<String>

    /** Whether [feature] is available on this device. */
    suspend fun isFeatureAvailable(feature: HcFeature): Boolean

    /** Sleep sessions starting in `[startMs, endMs)`, of [origin] (null = every app). */
    suspend fun readSleepSessions(
        startMs: Long,
        endMs: Long,
        origin: String?,
    ): List<HcSession>

    /** Records of [channel] from [origin] starting in `[startMs, endMs)`. */
    suspend fun readChannel(
        channel: HcChannel,
        startMs: Long,
        endMs: Long,
        origin: String,
    ): List<HcPointRecord>

    /** Whether [origin] wrote at least one record of [channel] starting in `[startMs, endMs)` (one small read). */
    suspend fun hasRecords(
        channel: HcChannel,
        startMs: Long,
        endMs: Long,
        origin: String,
    ): Boolean
}

/**
 * [HealthConnectGateway] over the Jetpack client (connect-client 1.1.0, API 28–37). Reads page with
 * [PAGE_SIZE] until the page token is null or empty; every client exception becomes an [HcError]. Manual entries
 * are skipped (out of scope: the app is fully passive).
 */
@Singleton
class HealthConnectClientGateway
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : HealthConnectGateway {
        @Volatile
        private var cachedClient: HealthConnectClient? = null

        // getSdkStatus must never crash the caller; any failure means "not usable".
        @Suppress("TooGenericExceptionCaught")
        override fun availability(): HcAvailability =
            try {
                when (HealthConnectClient.getSdkStatus(context)) {
                    HealthConnectClient.SDK_AVAILABLE -> HcAvailability.AVAILABLE
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HcAvailability.UPDATE_REQUIRED
                    else -> HcAvailability.NOT_SUPPORTED
                }
            } catch (_: RuntimeException) {
                HcAvailability.NOT_SUPPORTED
            }

        override suspend fun grantedPermissions(): Set<String> =
            call("getGrantedPermissions") {
                client().permissionController.getGrantedPermissions()
            }

        override suspend fun isFeatureAvailable(feature: HcFeature): Boolean {
            if (availability() != HcAvailability.AVAILABLE) return false
            val code =
                when (feature) {
                    HcFeature.READ_IN_BACKGROUND -> HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND
                    HcFeature.READ_HISTORY -> HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY
                    HcFeature.SKIN_TEMPERATURE -> HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE
                }
            return call("getFeatureStatus") { client().features.getFeatureStatus(code) } == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        }

        override suspend fun readSleepSessions(
            startMs: Long,
            endMs: Long,
            origin: String?,
        ): List<HcSession> =
            readAll("readSleepSessions", SleepSessionRecord::class, startMs, endMs, origin).map { r ->
                HcSession(
                    id = r.metadata.id,
                    origin = r.metadata.dataOrigin.packageName,
                    startMs = r.startTime.toEpochMilli(),
                    endMs = r.endTime.toEpochMilli(),
                    endOffsetS = r.endZoneOffset?.totalSeconds,
                    lastModifiedMs = r.metadata.lastModifiedTime.toEpochMilli(),
                    stages = r.stages.map { HcStage(it.startTime.toEpochMilli(), it.endTime.toEpochMilli(), it.stage) },
                )
            }

        override suspend fun readChannel(
            channel: HcChannel,
            startMs: Long,
            endMs: Long,
            origin: String,
        ): List<HcPointRecord> =
            readAll("read:${channel.id.key}", channel.recordType, startMs, endMs, origin).map { r ->
                HcPointRecord(startOf(r, channel), r.metadata.lastModifiedTime.toEpochMilli(), channel.toSamples(r))
            }

        override suspend fun hasRecords(
            channel: HcChannel,
            startMs: Long,
            endMs: Long,
            origin: String,
        ): Boolean {
            if (startMs >= endMs) return false
            val request = request(channel.recordType, startMs, endMs, origin, pageToken = null, pageSize = 1)
            // One page of one record; the page token is deliberately not followed.
            return call("probe:${channel.id.key}") { client().readRecords(request) }.records.isNotEmpty()
        }

        private fun startOf(
            r: Record,
            channel: HcChannel,
        ): Long = channel.toSamples(r).minOfOrNull { it.timeMs } ?: 0L

        /** Every page of [type] records of [origin] whose start lies in `[startMs, endMs)`, without manual entries. */
        private suspend fun <T : Record> readAll(
            operation: String,
            type: KClass<T>,
            startMs: Long,
            endMs: Long,
            origin: String?,
        ): List<T> {
            if (startMs >= endMs) return emptyList()
            return readAllPages { token ->
                val response = call(operation) { client().readRecords(request(type, startMs, endMs, origin, token)) }
                Page(response.records, response.pageToken)
            }.filter { it.metadata.recordingMethod != Metadata.RECORDING_METHOD_MANUAL_ENTRY }
        }

        private fun <T : Record> request(
            type: KClass<T>,
            startMs: Long,
            endMs: Long,
            origin: String?,
            pageToken: String?,
            pageSize: Int = PAGE_SIZE,
        ): ReadRecordsRequest<T> =
            ReadRecordsRequest(
                recordType = type,
                timeRangeFilter = TimeRangeFilter.between(Instant.ofEpochMilli(startMs), Instant.ofEpochMilli(endMs)),
                dataOriginFilter = origin?.let { setOf(DataOrigin(it)) } ?: emptySet(),
                ascendingOrder = true,
                pageSize = pageSize,
                pageToken = pageToken,
            )

        private fun client(): HealthConnectClient =
            cachedClient ?: synchronized(this) {
                cachedClient ?: HealthConnectClient.getOrCreate(context).also { cachedClient = it }
            }

        // Health Connect calls are binder IPC; keep them off the caller's thread.
        @Suppress("InjectDispatcher")
        private suspend fun <T> call(
            operation: String,
            block: suspend () -> T,
        ): T =
            withContext(Dispatchers.IO) {
                mapHcErrors({ HcErrorMapper.map(operation, it, isAvailable = { availability() == HcAvailability.AVAILABLE }) }, block)
            }

        /** Constants. */
        companion object {
            /** Records per page (the platform allows 1..5000). */
            const val PAGE_SIZE: Int = 5000
        }
    }
