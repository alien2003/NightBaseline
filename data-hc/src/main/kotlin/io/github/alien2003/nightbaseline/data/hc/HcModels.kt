package io.github.alien2003.nightbaseline.data.hc

import io.github.alien2003.nightbaseline.algo.ChannelId
import io.github.alien2003.nightbaseline.algo.Sample
import io.github.alien2003.nightbaseline.algo.SleepSession

/** Whether Health Connect can be used (`HealthConnectClient.getSdkStatus`). */
enum class HcAvailability {
    /** Ready. */
    AVAILABLE,

    /** Installed but the provider must be updated (Android 9–13 APK). */
    UPDATE_REQUIRED,

    /** Not supported on this device. */
    NOT_SUPPORTED,
}

/** Optional Health Connect features (`HealthConnectFeatures`). */
enum class HcFeature {
    /** READ_HEALTH_DATA_IN_BACKGROUND exists. */
    READ_IN_BACKGROUND,

    /** READ_HEALTH_DATA_HISTORY exists. */
    READ_HISTORY,

    /** SkinTemperatureRecord exists. */
    SKIN_TEMPERATURE,
}

/** One staged interval of a sleep session with the raw Health Connect stage code. */
data class HcStage(
    /** Start, epoch ms. */
    val startMs: Long,
    /** End, epoch ms. */
    val endMs: Long,
    /** `SleepSessionRecord.STAGE_TYPE_*`. */
    val code: Int,
)

/** A sleep session as read from Health Connect (no free text: titles and notes are never read). */
data class HcSession(
    /** Health Connect record id. */
    val id: String,
    /** Package that wrote it. */
    val origin: String,
    /** Start, epoch ms. */
    val startMs: Long,
    /** End, epoch ms. */
    val endMs: Long,
    /** UTC offset at the end, seconds, when the source wrote one. */
    val endOffsetS: Int?,
    /** `metadata.lastModifiedTime`, epoch ms. */
    val lastModifiedMs: Long,
    /** Stages, empty when the source writes none. */
    val stages: List<HcStage> = emptyList(),
)

/** One record of a channel, reduced to its samples (see [HcChannel.toSamples]). */
data class HcPointRecord(
    /**
     * Start of the record, epoch ms: Health Connect filters reads by it. The real gateway fills in the first sample
     * time (the common record supertypes with `time` / `startTime` are internal in connect-client 1.1.0).
     */
    val startMs: Long,
    /** `metadata.lastModifiedTime`, epoch ms. */
    val lastModifiedMs: Long,
    /** Samples of the record. */
    val samples: List<Sample>,
)

/** A package that wrote sleep sessions. */
data class SleepOrigin(
    /** Package name. */
    val packageName: String,
    /** Sessions in the scanned range. */
    val sessions: Int,
    /** End of its newest session, epoch ms. */
    val lastEndMs: Long,
)

/**
 * Everything read for one time range of one source.
 *
 * Channels hold only samples inside the range; [steps] holds one sample per step record (its start, steps per
 * minute), used by the night-complete rule.
 */
data class NightData(
    /** Sessions overlapping the range, ordered by start. */
    val sessions: List<SleepSession>,
    /** Samples per channel that the source wrote in the range (granted channels only). */
    val channels: Map<ChannelId, List<Sample>>,
    /** Step records as (start, steps per minute); empty without READ_STEPS. */
    val steps: List<Sample>,
    /** Newest `lastModifiedTime` of any record read for the range, epoch ms; null when nothing was read. */
    val lastModifiedMs: Long?,
)
