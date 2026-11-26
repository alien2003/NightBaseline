package io.github.alien2003.nightbaseline.seeder

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Temperature
import androidx.health.connect.client.units.TemperatureDelta
import io.github.alien2003.nightbaseline.algo.ChannelId
import io.github.alien2003.nightbaseline.algo.Sample
import java.time.Instant
import java.time.ZoneOffset
import kotlin.reflect.KClass

/**
 * Writes a [SeedPlan] into Health Connect as this app's own data origin, and deletes this app's records. Health
 * Connect only lets an app delete its own records, so [wipe] can never touch another source's data.
 */
class SeedWriter(
    private val client: HealthConnectClient,
    private val log: (String) -> Unit,
) {
    private val device = Device(type = Device.TYPE_WATCH, manufacturer = "NightBaseline", model = "Synthetic seeder")

    /** Deletes every record this app wrote, per type. Returns the number of types cleared. */
    suspend fun wipe(): Int {
        val range = TimeRangeFilter.between(Instant.parse(WIPE_FROM), Instant.now().plusSeconds(WIPE_AHEAD_S))
        val types = TYPES.filter { it != SkinTemperatureRecord::class || skinTemperatureAvailable() }
        types.forEach { client.deleteRecords(it, range) }
        log("wiped ${types.size} record types (own data origin only)")
        return types.size
    }

    /** Writes everything of [plan] except its late tail. Returns the number of records written. */
    suspend fun seed(plan: SeedPlan): Int {
        val tag = "seed:${plan.scenario.key}"
        val offset = ZoneOffset.ofTotalSeconds(plan.offsetSeconds)
        val records = mutableListOf<Record>()
        plan.sessions.forEach { s ->
            records +=
                SleepSessionRecord(
                    startTime = Instant.ofEpochMilli(s.startMs),
                    startZoneOffset = offset,
                    endTime = Instant.ofEpochMilli(s.endMs),
                    endZoneOffset = offset,
                    metadata = meta("$tag:sleep:${s.id}:${s.startMs}"),
                    title = null,
                    notes = null,
                )
        }
        records += channelRecords(tag, plan.channels, offset)
        plan.steps.forEach { b ->
            records +=
                StepsRecord(
                    startTime = Instant.ofEpochMilli(b.startMs),
                    startZoneOffset = offset,
                    endTime = Instant.ofEpochMilli(b.endMs),
                    endZoneOffset = offset,
                    count = b.count,
                    metadata = meta("$tag:steps:${b.startMs}"),
                )
        }
        return insert(records)
    }

    /** Writes the late tail of [plan] (late upload). Returns the number of records written. */
    suspend fun appendTail(plan: SeedPlan): Int =
        insert(channelRecords("seed:${plan.scenario.key}:tail", plan.tail, ZoneOffset.ofTotalSeconds(plan.offsetSeconds)))

    private suspend fun channelRecords(
        tag: String,
        channels: Map<ChannelId, List<Sample>>,
        offset: ZoneOffset,
    ): List<Record> {
        val out = mutableListOf<Record>()
        channels[ChannelId.HEART_RATE]?.let { out += heartRate(tag, it, offset) }
        channels[ChannelId.SPO2]?.forEach { s ->
            out +=
                OxygenSaturationRecord(
                    time = Instant.ofEpochMilli(s.timeMs),
                    zoneOffset = offset,
                    percentage = Percentage(s.value.coerceIn(0.0, MAX_PERCENT)),
                    metadata = meta("$tag:spo2:${s.timeMs}"),
                )
        }
        channels[ChannelId.RESPIRATORY_RATE]?.forEach { s ->
            out +=
                RespiratoryRateRecord(
                    time = Instant.ofEpochMilli(s.timeMs),
                    zoneOffset = offset,
                    rate = s.value,
                    metadata = meta("$tag:rr:${s.timeMs}"),
                )
        }
        channels[ChannelId.HRV_RMSSD]?.forEach { s ->
            out +=
                HeartRateVariabilityRmssdRecord(
                    time = Instant.ofEpochMilli(s.timeMs),
                    zoneOffset = offset,
                    heartRateVariabilityMillis = s.value.coerceIn(MIN_HRV, MAX_HRV),
                    metadata = meta("$tag:hrv:${s.timeMs}"),
                )
        }
        channels[ChannelId.SKIN_TEMPERATURE]?.let { samples ->
            if (skinTemperatureAvailable()) out += skinTemperature(tag, samples, offset) else log("skin temperature not available: skipped")
        }
        return out
    }

    /** Heart rate as one series record per hour, like a watch uploading in batches. */
    private fun heartRate(
        tag: String,
        samples: List<Sample>,
        offset: ZoneOffset,
    ): List<Record> =
        samples.groupBy { it.timeMs / HOUR_MS }.values.map { hour ->
            HeartRateRecord(
                startTime = Instant.ofEpochMilli(hour.first().timeMs),
                startZoneOffset = offset,
                endTime = Instant.ofEpochMilli(hour.last().timeMs + END_PAD_MS),
                endZoneOffset = offset,
                samples =
                    hour.map { HeartRateRecord.Sample(Instant.ofEpochMilli(it.timeMs), it.value.toLong().coerceIn(MIN_BPM, MAX_BPM)) },
                metadata = meta("$tag:hr:${hour.first().timeMs}"),
            )
        }

    /** Skin temperature: one record per night (samples more than 6 h apart start a new one), deltas against 33.5 °C. */
    private fun skinTemperature(
        tag: String,
        samples: List<Sample>,
        offset: ZoneOffset,
    ): List<Record> {
        val groups = mutableListOf<MutableList<Sample>>()
        samples.forEach { s ->
            if (groups.isEmpty() || s.timeMs - groups.last().last().timeMs > SKIN_GROUP_GAP_MS) {
                groups += mutableListOf(s)
            } else {
                groups.last() += s
            }
        }
        return groups.map { g ->
            SkinTemperatureRecord(
                startTime = Instant.ofEpochMilli(g.first().timeMs),
                startZoneOffset = offset,
                endTime = Instant.ofEpochMilli(g.last().timeMs + END_PAD_MS),
                endZoneOffset = offset,
                metadata = meta("$tag:skin:${g.first().timeMs}"),
                deltas =
                    g.map { SkinTemperatureRecord.Delta(Instant.ofEpochMilli(it.timeMs), TemperatureDelta.celsius(it.value)) },
                baseline = Temperature.celsius(SKIN_BASELINE_C),
                measurementLocation = SkinTemperatureRecord.MEASUREMENT_LOCATION_WRIST,
            )
        }
    }

    private suspend fun insert(records: List<Record>): Int {
        records.chunked(CHUNK).forEachIndexed { i, chunk ->
            client.insertRecords(chunk)
            log("inserted ${minOf((i + 1) * CHUNK, records.size)}/${records.size}")
        }
        return records.size
    }

    private suspend fun skinTemperatureAvailable(): Boolean =
        client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_SKIN_TEMPERATURE) == HealthConnectFeatures.FEATURE_STATUS_AVAILABLE

    private fun meta(clientRecordId: String): Metadata = Metadata.autoRecorded(device, clientRecordId, 1)

    private companion object {
        const val CHUNK = 500
        const val END_PAD_MS = 1_000L
        const val HOUR_MS = 3_600_000L
        const val SKIN_GROUP_GAP_MS = 6 * HOUR_MS
        const val SKIN_BASELINE_C = 33.5
        const val MAX_PERCENT = 100.0
        const val MIN_HRV = 1.0
        const val MAX_HRV = 200.0
        const val MIN_BPM = 30L
        const val MAX_BPM = 220L
        const val WIPE_FROM = "2000-01-01T00:00:00Z"
        const val WIPE_AHEAD_S = 2L * 24 * 3600

        val TYPES: List<KClass<out Record>> =
            listOf(
                SleepSessionRecord::class,
                HeartRateRecord::class,
                OxygenSaturationRecord::class,
                RespiratoryRateRecord::class,
                HeartRateVariabilityRmssdRecord::class,
                SkinTemperatureRecord::class,
                StepsRecord::class,
            )
    }
}
