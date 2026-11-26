package io.github.alien2003.nightbaseline.e2e

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.alien2003.nightbaseline.app.SourceDiscovery
import io.github.alien2003.nightbaseline.data.hc.HcAvailability
import io.github.alien2003.nightbaseline.data.hc.HcFeature
import io.github.alien2003.nightbaseline.data.hc.HcPermissions
import io.github.alien2003.nightbaseline.data.local.settings.AppSettings
import io.github.alien2003.nightbaseline.e2e.safety.E2eHealth
import io.github.alien2003.nightbaseline.e2e.safety.ReadBudget
import io.github.alien2003.nightbaseline.e2e.support.E2eAppData
import io.github.alien2003.nightbaseline.e2e.support.E2eStatusRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Health Connect as the installed app sees it, read-only (inside the app's process, with the app's permissions):
 * the SDK, the granted permissions, the configured source's last 7 days through the app's own reader within a read
 * budget, source discovery, and the background-read feature. Reports counts and flags only.
 */
@RunWith(AndroidJUnit4::class)
class HealthConnectE2e {
    @get:Rule
    val status = E2eStatusRule()

    // One budget for the whole class (JUnit creates an instance per test; the class runs in one process).
    private val budget get() = sharedBudget
    private val reader get() = sharedReader

    @Test
    fun sdkIsAvailable() {
        val availability = reader.availability()
        status.flag("available", availability == HcAvailability.AVAILABLE)
        assertEquals("Health Connect SDK status", HcAvailability.AVAILABLE, availability)
    }

    @Test
    fun requiredPermissionsAreGranted() {
        val granted = runBlocking { reader.grantedPermissions() }
        val missing = HcPermissions.REQUIRED - granted
        status.count("requiredGranted", (HcPermissions.REQUIRED.size - missing.size).toLong())
        status.count("optionalGranted", HcPermissions.OPTIONAL.count { it in granted }.toLong())
        status.flag("historyGranted", HcPermissions.READ_HEALTH_DATA_HISTORY in granted)
        status.flag("backgroundGranted", HcPermissions.READ_HEALTH_DATA_IN_BACKGROUND in granted)
        // Whatever else is granted must be one of the app's own READ permissions (the app declares nothing else).
        status.count("unexpectedGranted", granted.count { it.startsWith(HEALTH_PREFIX) && it !in HcPermissions.ALL }.toLong())
        assertTrue("required permissions missing: $missing", missing.isEmpty())
        assertTrue(
            "the app holds a Health Connect permission it does not declare",
            granted.filter { it.startsWith(HEALTH_PREFIX) }.all {
                it in
                    HcPermissions.ALL
            },
        )
    }

    @Test
    fun readsTheConfiguredSourceForSevenDaysWithinTheBudget() {
        val source = E2eHealth.settingsSnapshot().sourcePackage
        status.flag("sourceIsDefault", source == AppSettings.DEFAULT_SOURCE_PACKAGE)
        val now = System.currentTimeMillis()
        val from = now - WINDOW_DAYS * DAY_MS
        val started = System.nanoTime()
        val (sessions, channels, night) =
            runBlocking {
                val sessions = reader.readSessions(source, from, now)
                val channels = reader.channelsOf(source, from, now)
                // The newest session's night, as the daily run reads it.
                val night = sessions.lastOrNull()?.let { reader.readNight(source, it.startMs, it.endMs + 1) }
                Triple(sessions, channels, night)
            }
        status.count("readMs", (System.nanoTime() - started) / NANOS_PER_MS)
        status.count("sessions", sessions.size.toLong())
        status.count("channels", channels.size.toLong())
        status.count("newestNightChannelsWithSamples", night?.channels?.count { it.value.isNotEmpty() }?.toLong() ?: 0L)
        status.count(
            "newestNightSamples",
            night
                ?.channels
                ?.values
                ?.sumOf { it.size }
                ?.toLong() ?: 0L,
        )
        status.count("readCalls", budget.spent.toLong())
        status.count("readBudget", budget.limit.toLong())
        status.count("permissionQueries", budget.metaCalls.toLong())
        assertTrue(
            "sessions must be ordered and well formed",
            sessions.zipWithNext().all { (a, b) -> a.startMs <= b.startMs } && sessions.all { it.endMs > it.startMs },
        )
        assertTrue("read calls ${budget.spent} over the budget ${budget.limit}", budget.spent <= budget.limit)
    }

    @Test
    fun sourceDiscoveryListsTheDefaultSource() {
        val options = runBlocking { SourceDiscovery.discover(E2eAppData.create(reader)) }
        status.count("sources", options.size.toLong())
        status.flag("defaultListed", options.any { it.packageName == AppSettings.DEFAULT_SOURCE_PACKAGE })
        status.flag("defaultFirst", options.firstOrNull()?.packageName == AppSettings.DEFAULT_SOURCE_PACKAGE)
        status.count("readCalls", budget.spent.toLong())
        assertTrue(
            "source discovery does not list ${AppSettings.DEFAULT_SOURCE_PACKAGE}",
            options.any {
                it.packageName ==
                    AppSettings.DEFAULT_SOURCE_PACKAGE
            },
        )
        assertTrue("the default source must be listed first", options.first().packageName == AppSettings.DEFAULT_SOURCE_PACKAGE)
        assertTrue("source discovery listed the app itself", options.none { it.packageName == E2e.APP_PACKAGE })
    }

    @Test
    fun backgroundReadFeatureStatusIsRecorded() {
        val (available, granted) =
            runBlocking {
                reader.isFeatureAvailable(HcFeature.READ_IN_BACKGROUND) to
                    (HcPermissions.READ_HEALTH_DATA_IN_BACKGROUND in reader.grantedPermissions())
            }
        val history = runBlocking { reader.isFeatureAvailable(HcFeature.READ_HISTORY) }
        status.flag("backgroundFeatureAvailable", available)
        status.flag("backgroundGranted", granted)
        status.flag("historyFeatureAvailable", history)
        E2e.progress("background-read feature available: $available, granted: $granted")
        // Recorded either way; a grant without the feature would mean Health Connect reports something impossible.
        assertTrue("READ_HEALTH_DATA_IN_BACKGROUND granted but the feature is not available", available || !granted)
    }

    private companion object {
        val sharedBudget: ReadBudget by lazy { ReadBudget.fromArgs(DEFAULT_BUDGET) }
        val sharedReader by lazy { E2eHealth.reader(sharedBudget) }
        const val DEFAULT_BUDGET = 150
        const val WINDOW_DAYS = 7L
        const val DAY_MS = 86_400_000L
        const val NANOS_PER_MS = 1_000_000L
        const val HEALTH_PREFIX = "android.permission.health."
    }
}
