package io.github.alien2003.nightbaseline.nav

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController

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

    /** Health Connect's own permission screen for this app (or Health Connect settings). */
    fun openHealthConnect()

    /** The Play Store page that installs or updates Health Connect. */
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
                val manage =
                    runCatching { HealthConnectClient.getHealthConnectManageDataIntent(context) }
                        .getOrElse { Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS) }
                context.startSafely(manage) || context.startSafely(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
            }

            override fun openStore() {
                val uri = Uri.parse("market://details?id=$HEALTH_CONNECT_PACKAGE&url=healthconnect%3A%2F%2Fonboarding")
                context.startSafely(Intent(Intent.ACTION_VIEW, uri).setPackage("com.android.vending")) ||
                    context.startSafely(Intent(Intent.ACTION_VIEW, uri))
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

private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

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
