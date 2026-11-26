package io.github.alien2003.nightbaseline.nav

import io.github.alien2003.nightbaseline.data.hc.HcPermissions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a shell permission request continues in Health Connect ([PermissionFallback]). */
class PermissionFallbackTest {
    private val background = setOf(HcPermissions.READ_HEALTH_DATA_IN_BACKGROUND)
    private val core = HcPermissions.ALL - HcPermissions.READ_HEALTH_DATA_IN_BACKGROUND

    @Test
    fun `background still missing and the result came back at once opens Health Connect`() {
        assertTrue(PermissionFallback.mustOpenHealthConnect(background, core, elapsedMs = 120))
    }

    @Test
    fun `a slow answer is a choice made in a dialog and is left alone`() {
        assertFalse(PermissionFallback.mustOpenHealthConnect(background, core, elapsedMs = 4_000))
    }

    @Test
    fun `nothing granted at all opens Health Connect whenever it came back`() {
        assertTrue(PermissionFallback.mustOpenHealthConnect(HcPermissions.REQUIRED, emptySet(), elapsedMs = 4_000))
    }

    @Test
    fun `what was asked for was added`() {
        assertFalse(PermissionFallback.mustOpenHealthConnect(background, HcPermissions.ALL, elapsedMs = 50))
        assertFalse(PermissionFallback.mustOpenHealthConnect(HcPermissions.REQUIRED, setOf(HcPermissions.READ_SLEEP), elapsedMs = 50))
    }
}
