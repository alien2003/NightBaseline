package io.github.alien2003.nightbaseline.app

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.alien2003.nightbaseline.BuildConfig
import io.github.alien2003.nightbaseline.data.hc.HcAvailability
import io.github.alien2003.nightbaseline.data.hc.HcFeature
import io.github.alien2003.nightbaseline.data.hc.HealthDataReader
import io.github.alien2003.nightbaseline.data.local.settings.SettingsRepository
import io.github.alien2003.nightbaseline.ui.settings.NotificationAccess
import io.github.alien2003.nightbaseline.work.notify.AndroidNightNotifier
import io.github.alien2003.nightbaseline.work.schedule.NightWorkRequests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The platform facts the screens need besides the stored nights: is Health Connect usable, what is granted, is the
 * source app still installed, may the app notify. Never health data. Checked on start, on every resume and after a
 * permission result ([AccessMonitor.refresh]); Health Connect *data* is read only by the daily work and "Check now".
 */
data class AccessStatus(
    /** `HealthConnectClient.getSdkStatus()`. */
    val healthConnect: HcAvailability,
    /** Granted Health Connect permissions (empty when Health Connect is unavailable). */
    val granted: Set<String>,
    /** READ_HEALTH_DATA_IN_BACKGROUND exists on this device. */
    val backgroundFeature: Boolean,
    /** Package of the configured source. */
    val sourcePackage: String,
    /** Its label from the package manager, or null when it is no longer installed (`E-SOURCE-GONE`). */
    val sourceLabel: String?,
    /** POST_NOTIFICATIONS and the `night_results` channel. */
    val notifications: NotificationAccess,
)

/** Package-manager and notification facts (an interface so the ViewModel tests and screenshots use fakes). */
interface AppPlatform {
    /** Label of [packageName], or null when it is not installed. */
    fun appLabel(packageName: String): String?

    /** Whether the app may post night result notifications. */
    fun notificationAccess(): NotificationAccess

    /** App version name. */
    val versionName: String

    /** Android API level (POST_NOTIFICATIONS exists from 33). */
    val sdkInt: Int

    /** Remembers that the system notification prompt was shown (after a denial it is not shown again). */
    fun markNotificationsAsked()

    /**
     * Emits when the time zone, the clock or the date changes while the app runs, so times and dates on screen are
     * formatted again ("Last read", "Night of ..."). Nothing by default (tests, previews).
     */
    fun timeChanges(): Flow<Unit> = emptyFlow()
}

/** [AppPlatform] over the package manager and the notification manager. */
@Singleton
class AndroidAppPlatform
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : AppPlatform {
        override fun appLabel(packageName: String): String? =
            try {
                val pm = context.packageManager
                pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }

        override fun notificationAccess(): NotificationAccess {
            val manager = NotificationManagerCompat.from(context)
            val channelBlocked =
                manager.getNotificationChannel(AndroidNightNotifier.CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE
            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            val granted =
                !needsPermission ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            return when {
                granted && manager.areNotificationsEnabled() && !channelBlocked -> NotificationAccess.Allowed
                !granted && !asked() -> NotificationAccess.NotAsked
                else -> NotificationAccess.Blocked
            }
        }

        override val versionName: String = BuildConfig.VERSION_NAME

        override val sdkInt: Int = Build.VERSION.SDK_INT

        override fun timeChanges(): Flow<Unit> =
            callbackFlow {
                val receiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(
                            context: Context,
                            intent: Intent,
                        ) {
                            trySend(Unit)
                        }
                    }
                val filter =
                    IntentFilter().apply {
                        addAction(Intent.ACTION_TIMEZONE_CHANGED)
                        addAction(Intent.ACTION_TIME_CHANGED)
                        addAction(Intent.ACTION_DATE_CHANGED)
                    }
                // Protected system broadcasts: delivered to a not-exported receiver too.
                ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                awaitClose { context.unregisterReceiver(receiver) }
            }

        private fun asked(): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ASKED, false)

        override fun markNotificationsAsked() {
            context
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ASKED, true)
                .apply()
        }

        private companion object {
            const val PREFS = "notification_prompt"
            const val KEY_ASKED = "asked"
        }
    }

/** The [AccessStatus] as a flow that the screens observe. */
interface AccessMonitor {
    /** Current status; null until the first check finished. */
    val status: StateFlow<AccessStatus?>

    /** Checks again (start, resume, permission result, "Check again"). */
    suspend fun refresh()
}

/** [AccessMonitor] over the Health Connect reader, the settings and the [AppPlatform]; checks run on IO. */
@Singleton
class DefaultAccessMonitor
    @Inject
    constructor(
        private val reader: HealthDataReader,
        private val settings: SettingsRepository,
        private val platform: AppPlatform,
    ) : AccessMonitor {
        private val state = MutableStateFlow<AccessStatus?>(null)
        override val status: StateFlow<AccessStatus?> = state.asStateFlow()

        @Suppress("TooGenericExceptionCaught") // A failing status check must never crash the UI: it reads as unavailable.
        override suspend fun refresh() {
            state.value =
                withContext(Dispatchers.IO) {
                    val source = settings.settings.first().sourcePackage
                    val availability = reader.availability()
                    val usable = availability == HcAvailability.AVAILABLE
                    val granted = if (usable) runCatching { reader.grantedPermissions() }.getOrDefault(emptySet()) else emptySet()
                    val background =
                        usable && runCatching { reader.isFeatureAvailable(HcFeature.READ_IN_BACKGROUND) }.getOrDefault(false)
                    AccessStatus(availability, granted, background, source, platform.appLabel(source), platform.notificationAccess())
                }
        }
    }

/** Whether a night check (the daily work or "Check now") is running right now (`LOADING` before the first result). */
fun interface CheckState {
    /** True while a check runs. */
    fun running(): Flow<Boolean>
}

/** [CheckState] from WorkManager's unique night-check chain. */
@Singleton
class WorkCheckState
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : CheckState {
        override fun running(): Flow<Boolean> =
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWorkFlow(NightWorkRequests.UNIQUE_NAME)
                .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } }
    }
