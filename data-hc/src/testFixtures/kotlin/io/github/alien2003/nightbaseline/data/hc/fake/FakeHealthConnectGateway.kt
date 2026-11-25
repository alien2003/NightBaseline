package io.github.alien2003.nightbaseline.data.hc.fake

import io.github.alien2003.nightbaseline.algo.ChannelId
import io.github.alien2003.nightbaseline.algo.Sample
import io.github.alien2003.nightbaseline.algo.SyntheticNights
import io.github.alien2003.nightbaseline.data.hc.HcAvailability
import io.github.alien2003.nightbaseline.data.hc.HcChannel
import io.github.alien2003.nightbaseline.data.hc.HcChannels
import io.github.alien2003.nightbaseline.data.hc.HcError
import io.github.alien2003.nightbaseline.data.hc.HcFeature
import io.github.alien2003.nightbaseline.data.hc.HcPermissions
import io.github.alien2003.nightbaseline.data.hc.HcPointRecord
import io.github.alien2003.nightbaseline.data.hc.HcSession
import io.github.alien2003.nightbaseline.data.hc.HealthConnectGateway

/**
 * In-memory [HealthConnectGateway] following Health Connect's read semantics: records are filtered by start time
 * (`[start, end)`) and origin. A record is visible only once its `lastModifiedMs` is not after [now], which models
 * when the source published it; replacing a record with a later `lastModifiedMs` models a source rewrite.
 */
class FakeHealthConnectGateway(
    /** Current time; records written later are invisible. */
    var now: () -> Long = { Long.MAX_VALUE },
) : HealthConnectGateway {
    /** SDK status. */
    var status: HcAvailability = HcAvailability.AVAILABLE

    /** Granted permissions (all by default). */
    val granted: MutableSet<String> = HcPermissions.ALL.toMutableSet()

    /** Available features (all by default). */
    val features: MutableSet<HcFeature> = HcFeature.entries.toMutableSet()

    /** Stored sessions. */
    val sessions: MutableList<HcSession> = mutableListOf()

    /** Stored channel records: channel → (origin, record). */
    val records: MutableMap<ChannelId, MutableList<Pair<String, HcPointRecord>>> = mutableMapOf()

    /** Errors thrown by the next read calls, in order. */
    val failures: ArrayDeque<HcError> = ArrayDeque()

    /** Number of read calls (sessions, channels, probes). */
    var reads: Int = 0
        private set

    /** Oldest readable start; models the 30-day limit without READ_HEALTH_DATA_HISTORY (null = no limit). */
    var readableFromMs: Long? = null

    /** Adds a channel record of [origin]. */
    fun add(
        channel: ChannelId,
        origin: String,
        record: HcPointRecord,
    ) {
        records.getOrPut(channel) { mutableListOf() } += origin to record
    }

    override fun availability(): HcAvailability = status

    override suspend fun grantedPermissions(): Set<String> = granted.toSet()

    override suspend fun isFeatureAvailable(feature: HcFeature): Boolean = feature in features

    override suspend fun readSleepSessions(
        startMs: Long,
        endMs: Long,
        origin: String?,
    ): List<HcSession> {
        read()
        val from = clampStart(startMs)
        return sessions
            .filter { it.startMs in from until endMs && (origin == null || it.origin == origin) && it.lastModifiedMs <= now() }
            .sortedBy { it.startMs }
    }

    override suspend fun readChannel(
        channel: HcChannel,
        startMs: Long,
        endMs: Long,
        origin: String,
    ): List<HcPointRecord> {
        read()
        return visible(channel.id, clampStart(startMs), endMs, origin)
    }

    override suspend fun hasRecords(
        channel: HcChannel,
        startMs: Long,
        endMs: Long,
        origin: String,
    ): Boolean {
        read()
        return visible(channel.id, clampStart(startMs), endMs, origin).isNotEmpty()
    }

    private fun visible(
        channel: ChannelId,
        startMs: Long,
        endMs: Long,
        origin: String,
    ): List<HcPointRecord> =
        records[channel]
            .orEmpty()
            .filter { (o, r) -> o == origin && r.startMs in startMs until endMs && r.lastModifiedMs <= now() }
            .map { it.second }
            .sortedBy { it.startMs }

    private fun clampStart(startMs: Long): Long = readableFromMs?.let { maxOf(it, startMs) } ?: startMs

    private fun read() {
        reads++
        failures.removeFirstOrNull()?.let { throw it }
    }
}

/**
 * Loads [SyntheticNights] output into a [FakeHealthConnectGateway] as one source would publish it: every record is
 * written [writeDelayMs] after its newest sample, heart rate as one series record per night, the other channels as
 * one record per sample, and (when [stepsAfterEndMs] is not null) one brisk step record that long after each night's
 * end.
 */
object SyntheticSource {
    /** The default source. */
    const val ORIGIN: String = "com.heytap.health.international"

    /** Source records are written five minutes after their newest sample. */
    const val WRITE_DELAY_MS: Long = 5 * 60_000L

    /** The person gets up ten minutes after the night. */
    const val STEPS_AFTER_END_MS: Long = 10 * 60_000L

    /** End of the main night of [night] (naps excluded), epoch ms. */
    fun nightEndMs(night: SyntheticNights.Generated): Long = night.sessions.filterNot { it.id.startsWith("nap") }.maxOf { it.endMs }

    /** Loads [nights] into [gateway]. */
    fun load(
        gateway: FakeHealthConnectGateway,
        nights: List<SyntheticNights.Generated>,
        origin: String = ORIGIN,
        writeDelayMs: Long = WRITE_DELAY_MS,
        stepsAfterEndMs: Long? = STEPS_AFTER_END_MS,
    ) {
        nights.filterNot { it.missing }.forEach { night -> loadNight(gateway, night, origin, writeDelayMs, stepsAfterEndMs) }
    }

    /** Loads one night (see [load]). */
    fun loadNight(
        gateway: FakeHealthConnectGateway,
        night: SyntheticNights.Generated,
        origin: String = ORIGIN,
        writeDelayMs: Long = WRITE_DELAY_MS,
        stepsAfterEndMs: Long? = STEPS_AFTER_END_MS,
    ) {
        night.sessions.forEach { s ->
            gateway.sessions += HcSession(s.id, origin, s.startMs, s.endMs, s.endOffsetS, s.endMs + writeDelayMs)
        }
        night.channels.forEach { (channel, samples) ->
            if (samples.isEmpty()) return@forEach
            if (channel == ChannelId.HEART_RATE) {
                gateway.add(channel, origin, HcPointRecord(samples.first().timeMs, samples.last().timeMs + writeDelayMs, samples))
            } else {
                samples.forEach { p -> gateway.add(channel, origin, HcPointRecord(p.timeMs, p.timeMs + writeDelayMs, listOf(p))) }
            }
        }
        stepsAfterEndMs?.let { delay ->
            val t = nightEndMs(night) + delay
            gateway.add(HcChannels.STEPS_ID, origin, HcPointRecord(t, t + writeDelayMs, listOf(Sample(t, BRISK_STEPS_PER_MIN))))
        }
    }

    /** Replaces every record of [night] (a source rewrite at [rewrittenAtMs]) with the samples of [replacement]. */
    fun rewriteNight(
        gateway: FakeHealthConnectGateway,
        night: SyntheticNights.Generated,
        replacement: SyntheticNights.Generated,
        rewrittenAtMs: Long,
        origin: String = ORIGIN,
    ) {
        val start = night.sessions.minOf { it.startMs } - HOUR_MS
        val end = nightEndMs(night) + HOUR_MS
        gateway.records.values.forEach { list -> list.removeAll { (o, r) -> o == origin && r.startMs in start..end } }
        gateway.sessions.removeAll { s -> s.origin == origin && night.sessions.any { it.id == s.id } }
        replacement.sessions.forEach { s -> gateway.sessions += HcSession(s.id, origin, s.startMs, s.endMs, s.endOffsetS, rewrittenAtMs) }
        replacement.channels.forEach { (channel, samples) ->
            samples.forEach { p -> gateway.add(channel, origin, HcPointRecord(p.timeMs, rewrittenAtMs, listOf(p))) }
        }
        val t = nightEndMs(night) + STEPS_AFTER_END_MS
        gateway.add(HcChannels.STEPS_ID, origin, HcPointRecord(t, rewrittenAtMs, listOf(Sample(t, BRISK_STEPS_PER_MIN))))
    }

    private const val BRISK_STEPS_PER_MIN = 60.0
    private const val HOUR_MS = 3_600_000L
}
