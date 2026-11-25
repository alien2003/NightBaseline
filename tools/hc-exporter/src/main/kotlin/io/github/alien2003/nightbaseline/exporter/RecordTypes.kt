package io.github.alien2003.nightbaseline.exporter

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import org.json.JSONArray
import org.json.JSONObject
import kotlin.reflect.KClass

/** One exported Health Connect record type: the dump file name, the record class and how its payload is encoded. */
class RecordType(
    val name: String,
    val kClass: KClass<out Record>,
    val encode: (Record, JSONObject) -> Unit,
) {
    val permission: String = HealthPermission.getReadPermission(kClass)
}

private fun instant(o: JSONObject, time: java.time.Instant, offset: java.time.ZoneOffset?) {
    o.put("time", time.toEpochMilli())
    o.put("zoneOffsetS", offset?.totalSeconds)
}

private fun interval(o: JSONObject, start: java.time.Instant, end: java.time.Instant, startOffset: java.time.ZoneOffset?, endOffset: java.time.ZoneOffset?) {
    o.put("start", start.toEpochMilli())
    o.put("end", end.toEpochMilli())
    o.put("startZoneOffsetS", startOffset?.totalSeconds)
    o.put("endZoneOffsetS", endOffset?.totalSeconds)
}

private fun pairs(list: List<Pair<Long, Number>>): JSONArray = JSONArray().apply { list.forEach { (t, v) -> put(JSONArray().put(t).put(v)) } }

/**
 * Every type the exporter reads. Free text (titles, notes) is never exported. Adding a type here is all it takes for
 * it to be dumped, counted in the manifest and merged by tools/hc-dataset.py.
 */
val RECORD_TYPES: List<RecordType> =
    listOf(
        RecordType("sleep", SleepSessionRecord::class) { r, o ->
            r as SleepSessionRecord
            interval(o, r.startTime, r.endTime, r.startZoneOffset, r.endZoneOffset)
            o.put("stages", JSONArray().apply { r.stages.forEach { s -> put(JSONArray().put(s.startTime.toEpochMilli()).put(s.endTime.toEpochMilli()).put(s.stage)) } })
        },
        RecordType("heart_rate", HeartRateRecord::class) { r, o ->
            r as HeartRateRecord
            interval(o, r.startTime, r.endTime, r.startZoneOffset, r.endZoneOffset)
            o.put("samples", pairs(r.samples.map { it.time.toEpochMilli() to it.beatsPerMinute }))
        },
        RecordType("spo2", OxygenSaturationRecord::class) { r, o ->
            r as OxygenSaturationRecord
            instant(o, r.time, r.zoneOffset)
            o.put("value", r.percentage.value)
        },
        RecordType("respiratory_rate", RespiratoryRateRecord::class) { r, o ->
            r as RespiratoryRateRecord
            instant(o, r.time, r.zoneOffset)
            o.put("value", r.rate)
        },
        RecordType("resting_heart_rate", RestingHeartRateRecord::class) { r, o ->
            r as RestingHeartRateRecord
            instant(o, r.time, r.zoneOffset)
            o.put("value", r.beatsPerMinute)
        },
        RecordType("hrv_rmssd", HeartRateVariabilityRmssdRecord::class) { r, o ->
            r as HeartRateVariabilityRmssdRecord
            instant(o, r.time, r.zoneOffset)
            o.put("value", r.heartRateVariabilityMillis)
        },
        RecordType("skin_temperature", SkinTemperatureRecord::class) { r, o ->
            r as SkinTemperatureRecord
            interval(o, r.startTime, r.endTime, r.startZoneOffset, r.endZoneOffset)
            o.put("baselineC", r.baseline?.inCelsius)
            o.put("location", r.measurementLocation)
            o.put("deltas", pairs(r.deltas.map { it.time.toEpochMilli() to it.delta.inCelsius }))
        },
        RecordType("body_temperature", BodyTemperatureRecord::class) { r, o ->
            r as BodyTemperatureRecord
            instant(o, r.time, r.zoneOffset)
            o.put("value", r.temperature.inCelsius)
            o.put("location", r.measurementLocation)
        },
        RecordType("steps", StepsRecord::class) { r, o ->
            r as StepsRecord
            interval(o, r.startTime, r.endTime, r.startZoneOffset, r.endZoneOffset)
            o.put("value", r.count)
        },
        RecordType("exercise", ExerciseSessionRecord::class) { r, o ->
            r as ExerciseSessionRecord
            interval(o, r.startTime, r.endTime, r.startZoneOffset, r.endZoneOffset)
            o.put("exerciseType", r.exerciseType)
            o.put("segments", r.segments.size)
        },
    )
