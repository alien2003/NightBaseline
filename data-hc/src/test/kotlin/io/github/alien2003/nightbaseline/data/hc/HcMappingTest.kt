package io.github.alien2003.nightbaseline.data.hc

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.SkinTemperatureRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.TemperatureDelta
import io.github.alien2003.nightbaseline.algo.ChannelId
import io.github.alien2003.nightbaseline.algo.Sample
import io.github.alien2003.nightbaseline.algo.StageKind
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset

class HcMappingTest {
    private val t0 = Instant.parse("2026-01-01T00:00:00Z")
    private val md = Metadata.unknownRecordingMethod()

    @Test
    fun stageCodesMapToKinds() {
        val asleep = listOf(2, 4, 5, 6)
        val awake = listOf(1, 7)
        (0..9).forEach { code ->
            val expected =
                when (code) {
                    in asleep -> StageKind.ASLEEP
                    in awake -> StageKind.AWAKE
                    else -> StageKind.OTHER
                }
            assertEquals(expected, HcStages.kindOf(code), "code $code")
        }
        assertEquals(2, SleepSessionRecord.STAGE_TYPE_SLEEPING)
        assertEquals(7, SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED)
    }

    @Test
    fun permissionStringsMatchTheClientLibrary() {
        assertEquals(HealthPermission.getReadPermission(SleepSessionRecord::class), HcPermissions.READ_SLEEP)
        assertEquals(HealthPermission.getReadPermission(HeartRateRecord::class), HcPermissions.READ_HEART_RATE)
        assertEquals(HealthPermission.getReadPermission(OxygenSaturationRecord::class), HcPermissions.READ_OXYGEN_SATURATION)
        assertEquals(HealthPermission.getReadPermission(RespiratoryRateRecord::class), HcPermissions.READ_RESPIRATORY_RATE)
        assertEquals(HealthPermission.getReadPermission(HeartRateVariabilityRmssdRecord::class), HcPermissions.READ_HEART_RATE_VARIABILITY)
        assertEquals(HealthPermission.getReadPermission(SkinTemperatureRecord::class), HcPermissions.READ_SKIN_TEMPERATURE)
        assertEquals(HealthPermission.getReadPermission(StepsRecord::class), HcPermissions.READ_STEPS)
        assertEquals(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY, HcPermissions.READ_HEALTH_DATA_HISTORY)
        assertEquals(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND, HcPermissions.READ_HEALTH_DATA_IN_BACKGROUND)
        // The table uses the same permissions, and nothing is a write permission.
        HcChannels.ALL.forEach { assertEquals(HealthPermission.getReadPermission(it.recordType), it.permission) }
        assertTrue(HcPermissions.ALL.none { it.contains("WRITE") })
    }

    @Test
    fun gatedPermissionsAreRequestedOnlyWithTheirFeature() {
        val none = HcPermissions.requestable(emptySet())
        assertEquals(HcPermissions.REQUIRED + HcPermissions.OPTIONAL - HcPermissions.READ_SKIN_TEMPERATURE, none)
        assertEquals(HcPermissions.ALL, HcPermissions.requestable(HcFeature.entries.toSet()))
        assertEquals(setOf(HcPermissions.READ_HEART_RATE), HcPermissions.missingRequired(setOf(HcPermissions.READ_SLEEP)))
    }

    @Test
    fun recordsBecomeSamples() {
        val hr =
            HeartRateRecord(
                t0,
                ZoneOffset.UTC,
                t0.plusSeconds(240),
                ZoneOffset.UTC,
                listOf(HeartRateRecord.Sample(t0, 55), HeartRateRecord.Sample(t0.plusSeconds(120), 57)),
                md,
            )
        assertEquals(
            listOf(Sample(t0.toEpochMilli(), 55.0), Sample(t0.toEpochMilli() + 120_000, 57.0)),
            HcChannels.HEART_RATE.toSamples(hr),
        )

        val spo2 = OxygenSaturationRecord(t0, ZoneOffset.UTC, Percentage(96.0), md)
        assertEquals(listOf(Sample(t0.toEpochMilli(), 96.0)), HcChannels.SPO2.toSamples(spo2))
        val rr = RespiratoryRateRecord(t0, ZoneOffset.UTC, 14.5, md)
        assertEquals(listOf(Sample(t0.toEpochMilli(), 14.5)), HcChannels.RESPIRATORY_RATE.toSamples(rr))
        val hrv = HeartRateVariabilityRmssdRecord(t0, ZoneOffset.UTC, 42.0, md)
        assertEquals(listOf(Sample(t0.toEpochMilli(), 42.0)), HcChannels.HRV_RMSSD.toSamples(hrv))

        // Steps: one sample at the start with the steps per minute.
        val steps = StepsRecord(t0, ZoneOffset.UTC, t0.plusSeconds(120), ZoneOffset.UTC, 30, md)
        assertEquals(listOf(Sample(t0.toEpochMilli(), 15.0)), HcChannels.STEPS.toSamples(steps))
    }

    @Test
    fun skinTemperatureIsOneSampleAtTheMidpointWithTheMeanDelta() {
        val end = t0.plusSeconds(3_600)
        val record =
            SkinTemperatureRecord(
                startTime = t0,
                startZoneOffset = ZoneOffset.UTC,
                endTime = end,
                endZoneOffset = ZoneOffset.UTC,
                deltas =
                    listOf(
                        SkinTemperatureRecord.Delta(t0.plusSeconds(60), TemperatureDelta.celsius(0.2)),
                        SkinTemperatureRecord.Delta(t0.plusSeconds(1_800), TemperatureDelta.celsius(0.4)),
                    ),
                metadata = md,
            )
        val samples = HcChannels.SKIN_TEMPERATURE.toSamples(record)
        assertEquals(1, samples.size)
        assertEquals(t0.toEpochMilli() + 1_800_000, samples[0].timeMs)
        assertEquals(0.3, samples[0].value, 1e-9)
        val empty = SkinTemperatureRecord(t0, ZoneOffset.UTC, end, ZoneOffset.UTC, metadata = md, deltas = emptyList())
        assertEquals(emptyList<Sample>(), HcChannels.SKIN_TEMPERATURE.toSamples(empty))
    }

    @Test
    fun theTableHasOneEntryPerChannel() {
        val ids = HcChannels.ALL.map { it.id }
        assertEquals(ids.toSet().size, ids.size)
        assertEquals(
            listOf(ChannelId.HEART_RATE, ChannelId.SPO2, ChannelId.RESPIRATORY_RATE, ChannelId.HRV_RMSSD, ChannelId.SKIN_TEMPERATURE),
            HcChannels.METRIC_CHANNELS.map { it.id },
        )
    }

    @Test
    fun pagingStopsOnNullOrEmptyToken() =
        runTest {
            val asked = mutableListOf<String?>()
            val pages = mapOf(null to Page(listOf(1, 2), "a"), "a" to Page(listOf(3), "b"), "b" to Page(listOf(4), ""))
            val all =
                readAllPages { token ->
                    asked += token
                    pages.getValue(token)
                }
            assertEquals(listOf(1, 2, 3, 4), all)
            assertEquals(listOf(null, "a", "b"), asked)

            assertEquals(listOf(9), readAllPages { Page(listOf(9), null) })
            var calls = 0
            readAllPages(maxPages = 3) {
                calls++
                Page(listOf(0), "again")
            }
            assertEquals(3, calls)
        }

    @Test
    fun errorsAreClassifiedWithoutTheirMessages() {
        val secret = "value 57 bpm"

        fun map(
            e: Throwable,
            code: Int? = null,
            available: Boolean = true,
        ) = HcErrorMapper.map("op", e, { if (it === e) code else null }, { available })

        assertTrue(map(SecurityException(secret)) is HcError.PermissionMissing)
        assertTrue(map(IllegalStateException(secret), code = 7) is HcError.RateLimited)
        assertTrue(map(IllegalStateException(secret), code = 8) is HcError.RateLimited)
        assertTrue(map(IllegalStateException(secret), code = 9) is HcError.Unavailable)
        assertTrue(map(UnsupportedOperationException(secret)) is HcError.Unavailable)
        assertTrue(map(IllegalStateException(secret), available = false) is HcError.Unavailable)
        val state = map(IllegalStateException(secret)) as HcError.Other
        assertTrue(state.retryable)
        assertTrue((map(IOException(secret)) as HcError.Other).retryable)
        val other = map(IllegalArgumentException(secret)) as HcError.Other
        assertEquals(false, other.retryable)
        // A coded platform exception one level down (coroutines stack-trace recovery) is still recognised.
        val inner = IllegalStateException(secret)
        assertTrue(HcErrorMapper.map("op", IllegalStateException("wrap", inner), { if (it === inner) 7 else null }) is HcError.RateLimited)
        listOf(state, other, map(SecurityException(secret))).forEach {
            assertTrue(!it.message.orEmpty().contains(secret), it.message)
            assertEquals(null, it.cause)
        }
    }
}
