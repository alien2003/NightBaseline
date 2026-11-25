package io.github.alien2003.nightbaseline.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The TrendChart value axis uses evenly spaced round ticks that cover the data (no "57 / 52 / 46" axes). */
class NiceScaleTest {
    @Test
    fun `heart rate range gets ticks every 5`() {
        val scale = niceScale(45.2, 58.0)
        assertEquals(listOf(45.0, 50.0, 55.0, 60.0), scale.ticks)
        assertEquals(0, scale.decimals)
    }

    @Test
    fun `ticks are evenly spaced, round and cover the data`() {
        listOf(13.4 to 16.1, 93.2 to 98.7, 33.0 to 61.0, 34.05 to 34.6, 0.0 to 3.0).forEach { (lo, hi) ->
            val scale = niceScale(lo, hi)
            val ticks = scale.ticks
            assertTrue(ticks.size in 2..5, "$lo..$hi: $ticks")
            assertTrue(ticks.first() <= lo && ticks.last() >= hi, "$lo..$hi: $ticks")
            ticks.zipWithNext().forEach { (a, b) -> assertEquals(scale.step, b - a, 1e-9) }
            val mantissa = scale.step / Math.pow(10.0, Math.floor(Math.log10(scale.step)))
            assertTrue(listOf(1.0, 2.0, 2.5, 5.0).any { kotlin.math.abs(it - mantissa) < 1e-6 }, "$lo..$hi step ${scale.step}")
        }
    }

    @Test
    fun `respiratory rate needs one decimal only for half steps`() {
        val scale = niceScale(15.1, 17.4)
        assertEquals(listOf(15.0, 16.0, 17.0, 18.0), scale.ticks)
        assertEquals(0, scale.decimals)
        assertEquals(1, niceScale(34.05, 34.6).decimals)
    }

    @Test
    fun `durations step in whole parts of hours`() {
        val scale = niceScale(380.0, 505.0, duration = true)
        assertEquals(60.0, scale.step)
        assertEquals(listOf(360.0, 420.0, 480.0, 540.0), scale.ticks)
        assertEquals(30.0, niceScale(400.0, 470.0, duration = true).step)
    }

    @Test
    fun `a flat series gets room on both sides`() {
        val scale = niceScale(96.0, 96.0)
        assertTrue(scale.min < 96.0 && scale.max > 96.0, "${scale.ticks}")
    }
}
