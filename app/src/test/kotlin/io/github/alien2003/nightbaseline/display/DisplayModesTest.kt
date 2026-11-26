package io.github.alien2003.nightbaseline.display

import io.github.alien2003.nightbaseline.display.DisplayModes.NO_PREFERENCE
import io.github.alien2003.nightbaseline.display.DisplayModes.highestRefreshAt
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The window asks for the fastest mode at the current resolution, never another resolution (ADR 0008). */
class DisplayModesTest {
    // A foldable's outer panel at two resolutions with several rates, and a faster mode at a third resolution.
    private val outer =
        listOf(
            ModeSpec(1, 1080, 2400, 60f),
            ModeSpec(2, 1080, 2400, 90f),
            ModeSpec(3, 1080, 2400, 120f),
            ModeSpec(4, 1440, 3200, 60f),
            ModeSpec(5, 1440, 3200, 120.00001f),
            ModeSpec(6, 720, 1600, 144f),
        )

    @Test
    fun `picks the highest refresh rate at the current resolution`() {
        assertEquals(3, highestRefreshAt(outer, currentId = 1, currentWidth = 1080, currentHeight = 2400))
        assertEquals(5, highestRefreshAt(outer, currentId = 4, currentWidth = 1440, currentHeight = 3200))
    }

    @Test
    fun `a faster mode at another resolution is never chosen`() {
        assertEquals(3, highestRefreshAt(outer, currentId = 2, currentWidth = 1080, currentHeight = 2400))
    }

    @Test
    fun `keeps the current mode when it already is the fastest`() {
        assertEquals(3, highestRefreshAt(outer, currentId = 3, currentWidth = 1080, currentHeight = 2400))
    }

    @Test
    fun `rates within half a hertz are one rate and prefer the current mode, then the lowest id`() {
        val twins = listOf(ModeSpec(7, 1080, 2400, 60f), ModeSpec(9, 1080, 2400, 119.99f), ModeSpec(8, 1080, 2400, 120f))
        assertEquals(9, highestRefreshAt(twins, currentId = 9, currentWidth = 1080, currentHeight = 2400))
        assertEquals(8, highestRefreshAt(twins, currentId = 7, currentWidth = 1080, currentHeight = 2400))
    }

    @Test
    fun `no modes means no preference`() {
        assertEquals(NO_PREFERENCE, highestRefreshAt(emptyList(), currentId = 1, currentWidth = 1080, currentHeight = 2400))
    }

    @Test
    fun `no mode at the current resolution means no preference`() {
        assertEquals(NO_PREFERENCE, highestRefreshAt(outer, currentId = 99, currentWidth = 1200, currentHeight = 2000))
    }

    @Test
    fun `a single refresh rate at the current resolution means no preference`() {
        val fixed = listOf(ModeSpec(1, 1080, 2400, 60f), ModeSpec(2, 1440, 3200, 120f))
        assertEquals(NO_PREFERENCE, highestRefreshAt(fixed, currentId = 1, currentWidth = 1080, currentHeight = 2400))
    }
}
