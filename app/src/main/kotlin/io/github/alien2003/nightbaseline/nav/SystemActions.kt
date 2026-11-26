package io.github.alien2003.nightbaseline.nav

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import io.github.alien2003.nightbaseline.R

/**
 * Everything that leaves the app or asks the system: the Health Connect permission request, the notification prompt
 * and the external screens of `screens.md` 2 ("(system)"). An interface so the navigation tests and screenshots run
 * without an activity result registry.
 */
interface SystemActions {
    /** Launches the Health Connect permission request (`PermissionController.createRequestPermissionResultContract`). */
    fun requestHealthPermissions(
        permissions: Set<String>,
        onResult: (Set<String>) -> Unit,
    )

    /** Launches the POST_NOTIFICATIONS prompt (API 33+). */
    fun requestNotifications(onResult: (Boolean) -> Unit)

    /**
     * Health Connect's permission screen of this app; where that cannot be opened, Health Connect's data screen or
     * settings; where Health Connect is missing, [openStore]. Never silent: a short message says when nothing opened.
     */
    fun openHealthConnect()

    /**
     * The store page that installs or updates Health Connect: Google Play, any app that handles `market://`, then the
     * Play web page (a browser; the app itself still needs no INTERNET). A short message says when nothing opened.
     */
    fun openStore()

    /** The system notification settings of this app. */
    fun openNotificationSettings()

    /** Opens the source app so it can sync (`E-SYNC` "Open {source}"). */
    fun openApp(packageName: String)

    /** Does nothing (screenshots, previews). */
    object None : SystemActions {
        override fun requestHealthPermissions(
            permissions: Set<String>,
            onResult: (Set<String>) -> Unit,
        ) = Unit

        override fun requestNotifications(onResult: (Boolean) -> Unit) = Unit

        override fun openHealthConnect() = Unit

        override fun openStore() = Unit

        override fun openNotificationSettings() = Unit

        override fun openApp(packageName: String) = Unit
    }
}

/** The real [SystemActions] of the current activity, with result launchers registered in composition. */
@Composable
fun rememberSystemActions(): SystemActions {
    val context = LocalContext.current
    val callbacks = remember { Callbacks() }
    val health =
        rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            callbacks.health?.invoke(granted)
            callbacks.health = null
        }
    val notifications =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            callbacks.notifications?.invoke(granted)
            callbacks.notifications = null
        }
    return remember(context, health, notifications) {
        object : SystemActions {
            override fun requestHealthPermissions(
                permissions: Set<String>,
                onResult: (Set<String>) -> Unit,
            ) {
                callbacks.health = onResult
                try {
                    health.launch(permissions)
                } catch (_: ActivityNotFoundException) {
                    callbacks.health = null
                    openHealthConnect()
                }
            }

            override fun requestNotifications(onResult: (Boolean) -> Unit) {
                callbacks.notifications = onResult
                notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            override fun openHealthConnect() {
                val opened = HealthConnectIntents.forPermissions(context, Build.VERSION.SDK_INT).any { context.startSafely(it) }
                if (!opened) openStore()
            }

            override fun openStore() {
                val opened = HealthConnectIntents.store().any { context.startSafely(it) }
                if (!opened) Toast.makeText(context, R.string.hc_store_unavailable, Toast.LENGTH_LONG).show()
            }

            override fun openNotificationSettings() {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                context.startSafely(intent)
            }

            override fun openApp(packageName: String) {
                context.packageManager.getLaunchIntentForPackage(packageName)?.let { context.startSafely(it) }
            }
        }
    }
}

private class Callbacks {
    var health: ((Set<String>) -> Unit)? = null
    var notifications: ((Boolean) -> Unit)? = null
}

/** The intents [SystemActions] tries in order (pure, so the order is unit-tested). */
internal object HealthConnectIntents {
    /** Health Connect's app package (Android 13 and lower; the store id everywhere). */
    const val PACKAGE: String = "com.google.android.apps.healthdata"

    /** `HealthConnectManager.ACTION_MANAGE_HEALTH_PERMISSIONS` (Android 14+, the framework's Health Connect). */
    const val ACTION_MANAGE_PERMISSIONS_FRAMEWORK: String = "android.health.connect.action.MANAGE_HEALTH_PERMISSIONS"

    /** The same screen of the Health Connect app (Android 13 and lower). */
    const val ACTION_MANAGE_PERMISSIONS_APK: String = "androidx.health.ACTION_MANAGE_HEALTH_PERMISSIONS"

    private const val FRAMEWORK_SDK = 34

    /** NightBaseline's own permission page first, then Health Connect's data screen, then its settings. */
    fun forPermissions(
        context: Context,
        sdkInt: Int,
    ): List<Intent> {
        val own =
            if (sdkInt >= FRAMEWORK_SDK) {
                Intent(ACTION_MANAGE_PERMISSIONS_FRAMEWORK)
            } else {
                Intent(ACTION_MANAGE_PERMISSIONS_APK).setPackage(PACKAGE)
            }.putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
        val manage = runCatching { HealthConnectClient.getHealthConnectManageDataIntent(context) }.getOrNull()
        return listOfNotNull(own, manage, Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
    }

    /** Google Play, any `market://` handler, then the Play web page. */
    fun store(): List<Intent> {
        val market = Uri.parse("market://details?id=$PACKAGE&url=healthconnect%3A%2F%2Fonboarding")
        val web = Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE&url=healthconnect%3A%2F%2Fonboarding")
        return listOf(
            Intent(Intent.ACTION_VIEW, market).setPackage("com.android.vending"),
            Intent(Intent.ACTION_VIEW, market),
            Intent(Intent.ACTION_VIEW, web),
        )
    }
}

/** Starts [intent]; false when no app handles it. */
private fun Context.startSafely(intent: Intent): Boolean =
    try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
