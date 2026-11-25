package io.github.alien2003.nightbaseline.cli

import io.github.alien2003.nightbaseline.algo.ChannelId
import io.github.alien2003.nightbaseline.algo.Sample
import io.github.alien2003.nightbaseline.algo.SleepSession
import io.github.alien2003.nightbaseline.algo.StageInterval
import io.github.alien2003.nightbaseline.algo.StageKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File

/** Sessions and channel samples of one source, read from the merged dataset (docs/DATASET.md). */
class SourceData(
    val sessions: List<SleepSession>,
    val channels: Map<ChannelId, List<Sample>>,
)

/** Reads `.data/dataset/<type>.jsonl` for one origin package. */
object DatasetReader {
    private val json = Json { ignoreUnknownKeys = true }

    /** Health Connect sleep stage codes counted as sleep / awake (others: not sleep). */
    private val SLEEP_CODES = setOf(STAGE_SLEEPING, STAGE_LIGHT, STAGE_DEEP, STAGE_REM)
    private val AWAKE_CODES = setOf(STAGE_AWAKE, STAGE_AWAKE_IN_BED)

    // Health Connect SleepSessionRecord stage codes.
    private const val STAGE_AWAKE = 1
    private const val STAGE_SLEEPING = 2
    private const val STAGE_LIGHT = 4
    private const val STAGE_DEEP = 5
    private const val STAGE_REM = 6
    private const val STAGE_AWAKE_IN_BED = 7

    // Field positions in the dataset's array tuples: stages are [start, end, code], samples are [time, value].
    private const val STAGE_START = 0
    private const val STAGE_END = 1
    private const val STAGE_CODE = 2
    private const val SAMPLE_TIME = 0
    private const val SAMPLE_VALUE = 1

    fun read(
        datasetDir: File,
        origin: String,
    ): SourceData {
        val sessions =
            lines(datasetDir, "sleep", origin).map { o ->
                val stages =
                    o["stages"]?.jsonArray.orEmpty().map { s ->
                        val a = s.jsonArray
                        val code = a[STAGE_CODE].jsonPrimitive.int
                        val kind =
                            when (code) {
                                in SLEEP_CODES -> StageKind.ASLEEP
                                in AWAKE_CODES -> StageKind.AWAKE
                                else -> StageKind.OTHER
                            }
                        StageInterval(a[STAGE_START].jsonPrimitive.long, a[STAGE_END].jsonPrimitive.long, kind)
                    }
                SleepSession(
                    id = o.getValue("id").jsonPrimitive.content,
                    startMs = o.getValue("start").jsonPrimitive.long,
                    endMs = o.getValue("end").jsonPrimitive.long,
                    endOffsetS = o["endZoneOffsetS"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    stages = stages,
                )
            }
        val channels = mutableMapOf<ChannelId, List<Sample>>()
        channels[ChannelId.HEART_RATE] =
            lines(datasetDir, "heart_rate", origin).flatMap { o ->
                o.getValue("samples").jsonArray.map { s ->
                    Sample(s.jsonArray[SAMPLE_TIME].jsonPrimitive.long, s.jsonArray[SAMPLE_VALUE].jsonPrimitive.double)
                }
            }
        channels[ChannelId.SPO2] = instant(datasetDir, "spo2", origin)
        channels[ChannelId.RESPIRATORY_RATE] = instant(datasetDir, "respiratory_rate", origin)
        channels[ChannelId.HRV_RMSSD] = instant(datasetDir, "hrv_rmssd", origin)
        channels[ChannelId.SKIN_TEMPERATURE] =
            // A skin temperature record covers an interval (on the sources seen: one record per night with a single
            // delta whose timestamp may lie outside the session). Its value is the mean delta, placed at the interval
            // midpoint, so it belongs to the night the record overlaps most.
            lines(datasetDir, "skin_temperature", origin).mapNotNull { o ->
                val deltas = o["deltas"]?.jsonArray.orEmpty().map { it.jsonArray[SAMPLE_VALUE].jsonPrimitive.double }
                if (deltas.isEmpty()) return@mapNotNull null
                val start = o.getValue("start").jsonPrimitive.long
                val end = o.getValue("end").jsonPrimitive.long
                Sample(start + (end - start) / 2, deltas.average())
            }
        return SourceData(sessions, channels.mapValues { (_, v) -> v.sortedBy { it.timeMs } })
    }

    /** Origins present in the sleep file with their session counts. */
    fun origins(datasetDir: File): Map<String, Int> =
        lines(datasetDir, "sleep", null).groupingBy { it.getValue("origin").jsonPrimitive.content }.eachCount()

    private fun instant(
        dir: File,
        type: String,
        origin: String,
    ): List<Sample> =
        lines(dir, type, origin).map { o -> Sample(o.getValue("time").jsonPrimitive.long, o.getValue("value").jsonPrimitive.double) }

    private fun lines(
        dir: File,
        type: String,
        origin: String?,
    ): List<JsonObject> {
        val f = File(dir, "$type.jsonl")
        if (!f.exists()) return emptyList()
        return f.useLines { seq ->
            seq
                .filter { it.isNotBlank() }
                .map { json.parseToJsonElement(it).jsonObject }
                .filter { origin == null || it["origin"]?.jsonPrimitive?.content == origin }
                .toList()
        }
    }

    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
}
