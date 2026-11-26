package io.github.alien2003.nightbaseline.nav

import android.app.Application
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * "Health Connect permissions" opens NightBaseline's own permission page first (the framework action on Android 14+,
 * the Health Connect app's on older versions), the generic screens only after that; "Install or update" ends with the
 * Play web page, so a device without a store app still opens something.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HealthConnectIntentsTest {
    private val app: Application get() = RuntimeEnvironment.getApplication()

    @Test
    fun `the app's own permission page comes first`() {
        val modern = HealthConnectIntents.forPermissions(app, sdkInt = 34)
        assertEquals(HealthConnectIntents.ACTION_MANAGE_PERMISSIONS_FRAMEWORK, modern.first().action)
        assertEquals(app.packageName, modern.first().getStringExtra(Intent.EXTRA_PACKAGE_NAME))
        assertEquals(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS, modern.last().action)
        val older = HealthConnectIntents.forPermissions(app, sdkInt = 33)
        assertEquals(HealthConnectIntents.ACTION_MANAGE_PERMISSIONS_APK, older.first().action)
        assertEquals(HealthConnectIntents.PACKAGE, older.first().`package`)
        assertEquals(app.packageName, older.first().getStringExtra(Intent.EXTRA_PACKAGE_NAME))
    }

    @Test
    fun `the store falls back to the Play web page`() {
        val store = HealthConnectIntents.store()
        assertEquals(listOf("market", "market", "https"), store.map { it.data?.scheme })
        assertEquals("com.android.vending", store.first().`package`)
        assertEquals("play.google.com", store.last().data?.host)
        assertEquals(HealthConnectIntents.PACKAGE, store.last().data?.getQueryParameter("id"))
    }
}
