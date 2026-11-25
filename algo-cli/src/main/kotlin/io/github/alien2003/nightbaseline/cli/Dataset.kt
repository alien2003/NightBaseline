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
    private val SLEEP_CODES = setOf(2, 4, 5, 6)
    private val AWAKE_CODES = setOf(1, 7)

    fun read(datasetDir: File, origin: String): SourceData {
        val sessions =
            lines(datasetDir, "sleep", origin).map { o ->
                val stages =
                    o["stages"]?.jsonArray.orEmpty().map { s ->
                        val a = s.jsonArray
                        val code = a[2].jsonPrimitive.int
                        val kind =
                            when (code) {
                                in SLEEP_CODES -> StageKind.ASLEEP
                                in AWAKE_CODES -> StageKind.AWAKE
                                else -> StageKind.OTHER
                            }
                        StageInterval(a[0].jsonPrimitive.long, a[1].jsonPrimitive.long, kind)
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
                o.getValue("samples").jsonArray.map { s -> Sample(s.jsonArray[0].jsonPrimitive.long, s.jsonArray[1].jsonPrimitive.double) }
            }
        channels[ChannelId.SPO2] = instant(datasetDir, "spo2", origin)
        channels[ChannelId.RESPIRATORY_RATE] = instant(datasetDir, "respiratory_rate", origin)
        channels[ChannelId.HRV_RMSSD] = instant(datasetDir, "hrv_rmssd", origin)
        channels[ChannelId.SKIN_TEMPERATURE] =
            lines(datasetDir, "skin_temperature", origin).flatMap { o ->
                o["deltas"]?.jsonArray.orEmpty().map { s -> Sample(s.jsonArray[0].jsonPrimitive.long, s.jsonArray[1].jsonPrimitive.double) }
            }
        return SourceData(sessions, channels.mapValues { (_, v) -> v.sortedBy { it.timeMs } })
    }

    /** Origins present in the sleep file with their session counts. */
    fun origins(datasetDir: File): Map<String, Int> =
        lines(datasetDir, "sleep", null).groupingBy { it.getValue("origin").jsonPrimitive.content }.eachCount()

    private fun instant(dir: File, type: String, origin: String): List<Sample> =
        lines(dir, type, origin).map { o -> Sample(o.getValue("time").jsonPrimitive.long, o.getValue("value").jsonPrimitive.double) }

    private fun lines(dir: File, type: String, origin: String?): List<JsonObject> {
        val f = File(dir, "$type.jsonl")
        if (!f.exists()) return emptyList()
        return f.useLines { seq ->
            seq.filter { it.isNotBlank() }
                .map { json.parseToJsonElement(it).jsonObject }
                .filter { origin == null || it["origin"]?.jsonPrimitive?.content == origin }
                .toList()
        }
    }

    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
}
