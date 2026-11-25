package io.github.alien2003.nightbaseline.data.hc

import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import io.github.alien2003.nightbaseline.algo.ChannelId
import io.github.alien2003.nightbaseline.algo.Sample
import io.github.alien2003.nightbaseline.algo.StageKind
import kotlin.reflect.KClass

/**
 * How one Health Connect record type becomes a :core-algo channel.
 *
 * @property id the channel.
 * @property recordType the Health Connect record class.
 * @property permission the read permission.
 * @property feature the feature the type needs, if any.
 * @property series records span an interval and may start before the read window (read with left padding).
 * @property toSamples the samples of one record (value in the channel's unit).
 */
class HcChannel(
    val id: ChannelId,
    val recordType: KClass<out Record>,
    val permission: String,
    val feature: HcFeature? = null,
    val series: Boolean = false,
    val toSamples: (Record) -> List<Sample>,
)

/**
 * The single mapping table from Health Connect types to channels. Adding a type means adding one entry here (and a
 * `MetricSpec` in :core-algo that reads the channel); discovery, permission gating and reads pick it up.
 */
object HcChannels {
    /** Step records: one sample per record at its start with the steps per minute (not a metric channel). */
    val STEPS_ID: ChannelId = ChannelId("steps")

    private const val MS_PER_MIN = 60_000.0

    /** Heart rate, bpm. */
    val HEART_RATE =
        HcChannel(ChannelId.HEART_RATE, HeartRateRecord::class, HcPermissions.READ_HEART_RATE, series = true) { r ->
            (r as HeartRateRecord).samples.map { Sample(it.time.toEpochMilli(), it.beatsPerMinute.toDouble()) }
        }

    /** SpO2, percent. */
    val SPO2 =
        HcChannel(ChannelId.SPO2, OxygenSaturationRecord::class, HcPermissions.READ_OXYGEN_SATURATION) { r ->
            r as OxygenSaturationRecord
            listOf(Sample(r.time.toEpochMilli(), r.percentage.value))
        }

    /** Respiratory rate, breaths per minute. */
    val RESPIRATORY_RATE =
        HcChannel(ChannelId.RESPIRATORY_RATE, RespiratoryRateRecord::class, HcPermissions.READ_RESPIRATORY_RATE) { r ->
            r as RespiratoryRateRecord
            listOf(Sample(r.time.toEpochMilli(), r.rate))
        }

    /** HRV (RMSSD), ms. */
    val HRV_RMSSD =
        HcChannel(ChannelId.HRV_RMSSD, HeartRateVariabilityRmssdRecord::class, HcPermissions.READ_HEART_RATE_VARIABILITY) { r ->
            r as HeartRateVariabilityRmssdRecord
            listOf(Sample(r.time.toEpochMilli(), r.heartRateVariabilityMillis))
        }

    /** Skin temperature: one sample at the record's midpoint with the mean delta, °C (relative to the source's baseline). */
    val SKIN_TEMPERATURE =
        HcChannel(
            ChannelId.SKIN_TEMPERATURE,
            SkinTemperatureRecord::class,
            HcPermissions.READ_SKIN_TEMPERATURE,
            feature = HcFeature.SKIN_TEMPERATURE,
            series = true,
        ) { r ->
            r as SkinTemperatureRecord
            if (r.deltas.isEmpty()) {
                emptyList()
            } else {
                val mid = r.startTime.toEpochMilli() + (r.endTime.toEpochMilli() - r.startTime.toEpochMilli()) / 2
                listOf(Sample(mid, r.deltas.map { it.delta.inCelsius }.average()))
            }
        }

    /** Steps: (record start, steps per minute). */
    val STEPS =
        HcChannel(STEPS_ID, StepsRecord::class, HcPermissions.READ_STEPS, series = true) { r ->
            r as StepsRecord
            val minutes = maxOf(1.0, (r.endTime.toEpochMilli() - r.startTime.toEpochMilli()) / MS_PER_MIN)
            listOf(Sample(r.startTime.toEpochMilli(), r.count / minutes))
        }

    /** Every channel, metric channels first. */
    val ALL: List<HcChannel> = listOf(HEART_RATE, SPO2, RESPIRATORY_RATE, HRV_RMSSD, SKIN_TEMPERATURE, STEPS)

    /** Channels that feed metrics (all but steps). */
    val METRIC_CHANNELS: List<HcChannel> = ALL.filter { it.id != STEPS_ID }
}

/** Health Connect sleep stage codes to :core-algo stage kinds. */
object HcStages {
    private val ASLEEP =
        setOf(
            SleepSessionRecord.STAGE_TYPE_SLEEPING,
            SleepSessionRecord.STAGE_TYPE_LIGHT,
            SleepSessionRecord.STAGE_TYPE_DEEP,
            SleepSessionRecord.STAGE_TYPE_REM,
        )
    private val AWAKE = setOf(SleepSessionRecord.STAGE_TYPE_AWAKE, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED)

    /** Codes 2, 4, 5, 6 are sleep; 1 and 7 awake; everything else (unknown, out of bed) other. */
    fun kindOf(code: Int): StageKind =
        when (code) {
            in ASLEEP -> StageKind.ASLEEP
            in AWAKE -> StageKind.AWAKE
            else -> StageKind.OTHER
        }
}
