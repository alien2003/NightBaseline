package io.github.alien2003.nightbaseline.data.hc

import io.github.alien2003.nightbaseline.algo.ChannelId
import io.github.alien2003.nightbaseline.algo.Sample
import io.github.alien2003.nightbaseline.algo.StageKind
import io.github.alien2003.nightbaseline.algo.SyntheticNights
import io.github.alien2003.nightbaseline.data.hc.fake.FakeHealthConnectGateway
import io.github.alien2003.nightbaseline.data.hc.fake.SyntheticSource
import io.github.alien2003.nightbaseline.data.local.ZoneSource
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.ZoneOffset

class HealthConnectReaderTest {
    private val gateway = FakeHealthConnectGateway()
    private var inForeground = true
    private val reader = HealthConnectReader(gateway, { inForeground }, { ZoneOffset.ofHours(2) }, ownPackage = OWN)

    private fun session(
        id: String,
        origin: String,
        startMs: Long,
        endMs: Long,
        offset: Int? = 0,
        stages: List<HcStage> = emptyList(),
    ) = HcSession(id, origin, startMs, endMs, offset, lastModifiedMs = endMs + 1, stages = stages)

    @Test
    fun sleepOriginsExcludePulseStagesAndThisApp() =
        runTest {
            gateway.sessions += session("a", ORIGIN, 0, H)
            gateway.sessions += session("b", ORIGIN, 2 * H, 3 * H)
            gateway.sessions += session("c", "org.other", 0, H)
            gateway.sessions += session("d", "io.github.alien2003.pulsestages", 0, H)
            gateway.sessions += session("e", OWN, 0, H)

            val origins = reader.sleepOrigins(0, 10 * H)
            assertEquals(listOf(ORIGIN, "org.other"), origins.map { it.packageName })
            assertEquals(2, origins[0].sessions)
            assertEquals(3 * H, origins[0].lastEndMs)
            assertEquals(emptyList<ChannelId>(), reader.channelsOf("io.github.alien2003.pulsestages", 0, H).toList())
        }

    @Test
    fun channelsOfFollowsWhatTheSourceWritesAndWhatIsGranted() =
        runTest {
            gateway.add(ChannelId.HEART_RATE, ORIGIN, HcPointRecord(H, H, listOf(Sample(H, 50.0))))
            gateway.add(ChannelId.SPO2, ORIGIN, HcPointRecord(H, H, listOf(Sample(H, 96.0))))
            gateway.add(ChannelId.SKIN_TEMPERATURE, ORIGIN, HcPointRecord(H, H, listOf(Sample(H, 0.1))))
            gateway.add(HcChannels.STEPS_ID, ORIGIN, HcPointRecord(H, H, listOf(Sample(H, 20.0))))
            gateway.add(ChannelId.RESPIRATORY_RATE, "org.other", HcPointRecord(H, H, listOf(Sample(H, 14.0))))

            assertEquals(
                setOf(ChannelId.HEART_RATE, ChannelId.SPO2, ChannelId.SKIN_TEMPERATURE, HcChannels.STEPS_ID),
                reader.channelsOf(ORIGIN, 0, 2 * H),
            )
            gateway.features -= HcFeature.SKIN_TEMPERATURE
            gateway.granted -= HcPermissions.READ_OXYGEN_SATURATION
            assertEquals(setOf(ChannelId.HEART_RATE, HcChannels.STEPS_ID), reader.channelsOf(ORIGIN, 0, 2 * H))
        }

    @Test
    fun readNightPadsSeriesTrimsSamplesAndMapsStages() =
        runTest {
            val start = 30 * H
            val end = 38 * H
            // A session that started before the window but overlaps it; one fully outside; one of another source.
            gateway.sessions +=
                session(
                    "s1",
                    ORIGIN,
                    start - H,
                    end,
                    offset = null,
                    stages = listOf(HcStage(start - H, start, 1), HcStage(start, end, 5), HcStage(end, end, 4)),
                )
            gateway.sessions += session("old", ORIGIN, start - 30 * H, start - 20 * H)
            gateway.sessions += session("x", "org.other", start, end)
            // A heart-rate series that starts 2 h before the window: found through the padding, trimmed by sample time.
            gateway.add(
                ChannelId.HEART_RATE,
                ORIGIN,
                HcPointRecord(start - 2 * H, end + 5, listOf(Sample(start - 2 * H, 70.0), Sample(start + 1, 50.0), Sample(end + 1, 60.0))),
            )
            // An instantaneous record before the window is not read (no padding for instantaneous types).
            gateway.add(ChannelId.SPO2, ORIGIN, HcPointRecord(start - 1, start, listOf(Sample(start - 1, 90.0))))
            gateway.add(ChannelId.SPO2, ORIGIN, HcPointRecord(start + 10, start + 99, listOf(Sample(start + 10, 96.0))))
            gateway.add(HcChannels.STEPS_ID, ORIGIN, HcPointRecord(end - H, end, listOf(Sample(end - H, 30.0))))

            val data = reader.readNight(ORIGIN, start, end + 1)
            assertEquals(listOf("s1"), data.sessions.map { it.id })
            val s1 = data.sessions.single()
            // No offset in the record: the zone's offset is used.
            assertEquals(2 * 3600, s1.endOffsetS)
            assertEquals(listOf(StageKind.AWAKE, StageKind.ASLEEP), s1.stages.map { it.kind })
            assertEquals(listOf(Sample(start + 1, 50.0)), data.channels[ChannelId.HEART_RATE])
            assertEquals(listOf(Sample(start + 10, 96.0)), data.channels[ChannelId.SPO2])
            assertEquals(emptyList<Sample>(), data.channels[ChannelId.RESPIRATORY_RATE])
            assertEquals(listOf(Sample(end - H, 30.0)), data.steps)
            assertTrue(HcChannels.STEPS_ID !in data.channels)
            assertEquals(end + 5, data.lastModifiedMs)
        }

    @Test
    fun syntheticNightsRoundTripThroughTheReader() =
        runTest {
            val nights = SyntheticNights(SyntheticNights.Config(seed = 5, nights = 3)).generate()
            SyntheticSource.load(gateway, nights)
            val night = nights.first { !it.missing }
            val start = night.sessions.minOf { it.startMs }
            val end = SyntheticSource.nightEndMs(night)
            val data = reader.readNight(ORIGIN, start, end)
            val expected = night.channels.getValue(ChannelId.HEART_RATE).filter { it.timeMs in start until end }
            assertEquals(expected, data.channels[ChannelId.HEART_RATE])
            assertEquals(night.sessions.filter { it.endMs > start && it.startMs < end }.map { it.id }, data.sessions.map { it.id })
        }

    @Test
    fun missingRequiredPermissionIsAnError() =
        runTest {
            gateway.granted -= HcPermissions.READ_HEART_RATE
            val e = assertThrows<HcError.PermissionMissing> { reader.readNight(ORIGIN, 0, H) }
            assertEquals(setOf(HcPermissions.READ_HEART_RATE), e.permissions)
            // Sessions alone need only READ_SLEEP; optional channels are skipped silently when not granted.
            assertEquals(emptyList<Any>(), reader.readSessions(ORIGIN, 0, H))
        }

    @Test
    fun unavailableHealthConnectIsAnError() =
        runTest {
            gateway.status = HcAvailability.NOT_SUPPORTED
            assertThrows<HcError.Unavailable> { reader.readSessions(ORIGIN, 0, H) }
        }

    @Test
    fun backgroundReadsNeedTheBackgroundPermission() =
        runTest {
            gateway.sessions += session("a", ORIGIN, 0, H)
            inForeground = false
            assertEquals(1, reader.readSessions(ORIGIN, 0, 2 * H).size)
            gateway.granted -= HcPermissions.READ_HEALTH_DATA_IN_BACKGROUND
            val e = assertThrows<HcError.PermissionMissing> { reader.readSessions(ORIGIN, 0, 2 * H) }
            assertTrue(e.background)
            // In the foreground the read is allowed without it.
            inForeground = true
            assertEquals(1, reader.readSessions(ORIGIN, 0, 2 * H).size)
        }

    @Test
    fun gatewayErrorsPropagate() =
        runTest {
            gateway.failures += HcError.RateLimited("readSleepSessions")
            assertThrows<HcError.RateLimited> { reader.readSessions(ORIGIN, 0, H) }
        }

    @Test
    fun systemZoneSourceIsTheDefaultZone() {
        assertEquals(java.time.ZoneId.systemDefault(), ZoneSource.SYSTEM.zone())
    }

    private companion object {
        const val H = 3_600_000L
        const val ORIGIN = SyntheticSource.ORIGIN
        const val OWN = "io.github.alien2003.nightbaseline"
    }
}
