package io.github.alien2003.nightbaseline.exporter

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.Writer
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Reads every record of every granted type in [RECORD_TYPES] over the whole history and writes
 * `files/export/<runId>/<type>.jsonl` plus `manifest.json`. Read-only: it calls nothing but readRecords and
 * getGrantedPermissions. `files/export/latest.json` names the last finished run (polled by tools/hc-export.sh).
 */
class Exporter(
    private val context: Context,
    private val client: HealthConnectClient,
    private val progress: (String) -> Unit,
) {
    suspend fun run(): File {
        val runId = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val root = File(context.filesDir, "export")
        val dir = File(root, runId).apply { mkdirs() }
        val startedAt = System.currentTimeMillis()
        val granted = client.permissionController.getGrantedPermissions()
        val features = client.features
        val historyAvailable =
            features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        val skinAvailable =
            features.getFeatureStatus(HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE) ==
                HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        val filter = TimeRangeFilter.between(Instant.parse("2010-01-01T00:00:00Z"), Instant.now().plusSeconds(DAY_S))
        val types = JSONObject()
        for (type in RECORD_TYPES) {
            types.put(type.name, exportType(type, granted, skinAvailable, filter, dir))
        }
        val manifest =
            JSONObject()
                .put("format", "nightbaseline-hc-dump")
                .put("formatVersion", 1)
                .put("runId", runId)
                .put("startedAtMs", startedAt)
                .put("finishedAtMs", System.currentTimeMillis())
                .put("zoneId", ZoneId.systemDefault().id)
                .put("device", deviceInfo())
                .put("healthConnectApkVersionCode", healthConnectVersion())
                .put("historyFeatureAvailable", historyAvailable)
                .put("skinTemperatureFeatureAvailable", skinAvailable)
                .put("grantedPermissions", JSONArray(granted.sorted()))
                .put("types", types)
        File(dir, "manifest.json").writeText(manifest.toString(2))
        File(root, "latest.json").writeText(JSONObject().put("runId", runId).toString())
        return dir
    }

    /**
     * Dumps one type into [dir] and returns its manifest entry; a failure is recorded there, never thrown.
     * Suppression reason: this debug tool must record any failure of one type (HC, IO, encoding) and still export the
     * other types, so the catch is deliberately generic.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun exportType(
        type: RecordType,
        granted: Set<String>,
        skinAvailable: Boolean,
        filter: TimeRangeFilter,
        dir: File,
    ): JSONObject {
        val info = JSONObject()
        when {
            type.permission !in granted -> {
                info.put("status", "NOT_GRANTED")
            }

            type.name == "skin_temperature" && !skinAvailable -> {
                info.put("status", "FEATURE_UNAVAILABLE")
            }

            else -> {
                try {
                    dumpType(type, filter, File(dir, "${type.name}.jsonl"), info)
                    info.put("status", "OK")
                } catch (e: Exception) {
                    info.put("status", "ERROR")
                    info.put("error", "${e.javaClass.simpleName}: ${e.message}")
                }
            }
        }
        return info
    }

    private fun deviceInfo(): JSONObject =
        JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("sdkInt", Build.VERSION.SDK_INT)
            .put("release", Build.VERSION.RELEASE)
            .put("display", Build.DISPLAY)

    private fun healthConnectVersion(): Long? =
        runCatching {
            context.packageManager.getPackageInfo("com.google.android.apps.healthdata", 0).longVersionCode
        }.getOrNull()

    /** Running totals of one type's dump. */
    private class TypeStats {
        var count = 0
        var minTime = Long.MAX_VALUE
        var maxTime = Long.MIN_VALUE
        val origins = JSONObject()
    }

    private suspend fun dumpType(
        type: RecordType,
        filter: TimeRangeFilter,
        out: File,
        info: JSONObject,
    ) {
        val stats = TypeStats()
        out.bufferedWriter().use { w ->
            var pageToken: String? = null
            do {
                val response =
                    client.readRecords(
                        ReadRecordsRequest(
                            recordType = type.kClass,
                            timeRangeFilter = filter,
                            ascendingOrder = true,
                            pageSize = PAGE_SIZE,
                            pageToken = pageToken,
                        ),
                    )
                response.records.forEach { r -> writeRecord(type, r, w, stats) }
                progress("${type.name}: ${stats.count}")
                pageToken = response.pageToken
            } while (!pageToken.isNullOrEmpty())
        }
        info.put("count", stats.count)
        if (stats.count > 0) {
            info.put("firstMs", stats.minTime)
            info.put("lastMs", stats.maxTime)
        }
        info.put("origins", stats.origins)
    }

    private fun writeRecord(
        type: RecordType,
        r: Record,
        w: Writer,
        stats: TypeStats,
    ) {
        val o = encodeCommon(r)
        type.encode(r, o)
        w.write(o.toString())
        w.write("\n")
        stats.count++
        val t = o.optLong("time", o.optLong("start"))
        if (t < stats.minTime) stats.minTime = t
        if (t > stats.maxTime) stats.maxTime = t
        val origin = r.metadata.dataOrigin.packageName
        stats.origins.put(origin, stats.origins.optInt(origin) + 1)
    }

    private fun encodeCommon(r: Record): JSONObject {
        val m = r.metadata
        val o =
            JSONObject()
                .put("id", m.id)
                .put("origin", m.dataOrigin.packageName)
                .put("lastModifiedMs", m.lastModifiedTime.toEpochMilli())
                .put("recordingMethod", m.recordingMethod)
        m.device?.let { d -> o.put("device", JSONObject().put("type", d.type).put("manufacturer", d.manufacturer).put("model", d.model)) }
        return o
    }

    private companion object {
        const val PAGE_SIZE = 5000
        const val DAY_S = 86_400L
    }
}
