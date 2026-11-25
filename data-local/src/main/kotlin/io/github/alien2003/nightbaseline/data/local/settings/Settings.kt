package io.github.alien2003.nightbaseline.data.local.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.alien2003.nightbaseline.algo.AlgoParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** How readily a measurement counts as outside the usual range. */
enum class SensitivityLevel(
    /** Robust z beyond which a metric deviates ([AlgoParams.zThreshold]). */
    val zThreshold: Double,
) {
    /** Fewer notifications: only clear changes. */
    LOW(3.0),

    /** The default (evidence: docs/research/metrics-matrix.md §6). */
    STANDARD(2.5),

    /** More notifications: smaller changes count too. */
    HIGH(2.0),
}

/** Translucent ("glass") surfaces of the UI. */
enum class GlassMode {
    /** On when the device renders blur well (decided by the app). */
    AUTO,

    /** Always off: plain surfaces. */
    OFF,
}

/** Settings > Appearance > Theme. */
enum class ThemeMode {
    /** Follow the system. */
    SYSTEM,

    /** Always light. */
    LIGHT,

    /** Always dark. */
    DARK,
}

/** The user's settings. */
data class AppSettings(
    /** Package of the Health Connect source app the nights are read from. */
    val sourcePackage: String = DEFAULT_SOURCE_PACKAGE,
    /** Whether the app may notify about nights with deviations. */
    val notificationsEnabled: Boolean = true,
    /** Sensitivity of the evaluation. */
    val sensitivity: SensitivityLevel = SensitivityLevel.STANDARD,
    /** The onboarding was finished. */
    val onboardingDone: Boolean = false,
    /** Glass surfaces. */
    val glassMode: GlassMode = GlassMode.AUTO,
    /** Whether data-access problems ("Problems reading data") may be notified. */
    val problemNotificationsEnabled: Boolean = true,
    /** Light, dark or the system theme. */
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** When the "notifications are off" banner was dismissed, epoch ms (it stays hidden for 30 days). */
    val notificationBannerDismissedAtMs: Long? = null,
    /** The one-time "your usual range is ready" banner was dismissed. */
    val rangeReadySeen: Boolean = false,
) {
    /** Algorithm parameters for these settings: [base] with the sensitivity's z threshold. */
    fun algoParams(base: AlgoParams = AlgoParams()): AlgoParams = base.copy(zThreshold = sensitivity.zThreshold)

    /** Defaults. */
    companion object {
        /** OHealth, the first verified source. */
        const val DEFAULT_SOURCE_PACKAGE: String = "com.heytap.health.international"
    }
}

/** Settings as a flow plus suspend setters. */
interface SettingsRepository {
    /** Current settings; emits on every change. */
    val settings: Flow<AppSettings>

    /** Sets [AppSettings.sourcePackage]. */
    suspend fun setSourcePackage(packageName: String)

    /** Sets [AppSettings.notificationsEnabled]. */
    suspend fun setNotificationsEnabled(enabled: Boolean)

    /** Sets [AppSettings.sensitivity]. */
    suspend fun setSensitivity(level: SensitivityLevel)

    /** Sets [AppSettings.onboardingDone]. */
    suspend fun setOnboardingDone(done: Boolean)

    /** Sets [AppSettings.glassMode]. */
    suspend fun setGlassMode(mode: GlassMode)

    /** Sets [AppSettings.problemNotificationsEnabled]. */
    suspend fun setProblemNotificationsEnabled(enabled: Boolean)

    /** Sets [AppSettings.theme]. */
    suspend fun setTheme(mode: ThemeMode)

    /** Sets [AppSettings.notificationBannerDismissedAtMs]. */
    suspend fun setNotificationBannerDismissedAt(timeMs: Long?)

    /** Sets [AppSettings.rangeReadySeen]. */
    suspend fun setRangeReadySeen(seen: Boolean)
}

/**
 * [SettingsRepository] over a Preferences DataStore. Unknown or missing values read as the defaults; a read error
 * (corrupted file) yields the defaults instead of failing the flow.
 */
@Singleton
class DataStoreSettingsRepository
    @Inject
    constructor(
        private val store: DataStore<Preferences>,
    ) : SettingsRepository {
        override val settings: Flow<AppSettings> =
            store.data
                .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
                .map(::read)
                .distinctUntilChanged()

        override suspend fun setSourcePackage(packageName: String) {
            require(packageName.isNotBlank()) { "the source package must not be blank" }
            store.edit { it[Keys.SOURCE] = packageName }
        }

        override suspend fun setNotificationsEnabled(enabled: Boolean) {
            store.edit { it[Keys.NOTIFICATIONS] = enabled }
        }

        override suspend fun setSensitivity(level: SensitivityLevel) {
            store.edit { it[Keys.SENSITIVITY] = level.name }
        }

        override suspend fun setOnboardingDone(done: Boolean) {
            store.edit { it[Keys.ONBOARDING] = done }
        }

        override suspend fun setGlassMode(mode: GlassMode) {
            store.edit { it[Keys.GLASS] = mode.name }
        }

        override suspend fun setProblemNotificationsEnabled(enabled: Boolean) {
            store.edit { it[Keys.PROBLEM_NOTIFICATIONS] = enabled }
        }

        override suspend fun setTheme(mode: ThemeMode) {
            store.edit { it[Keys.THEME] = mode.name }
        }

        override suspend fun setNotificationBannerDismissedAt(timeMs: Long?) {
            store.edit { if (timeMs == null) it.remove(Keys.NOTIF_BANNER_DISMISSED) else it[Keys.NOTIF_BANNER_DISMISSED] = timeMs }
        }

        override suspend fun setRangeReadySeen(seen: Boolean) {
            store.edit { it[Keys.RANGE_READY_SEEN] = seen }
        }

        private fun read(p: Preferences): AppSettings {
            val defaults = AppSettings()
            return AppSettings(
                sourcePackage = p[Keys.SOURCE]?.takeIf { it.isNotBlank() } ?: defaults.sourcePackage,
                notificationsEnabled = p[Keys.NOTIFICATIONS] ?: defaults.notificationsEnabled,
                sensitivity = SensitivityLevel.entries.firstOrNull { it.name == p[Keys.SENSITIVITY] } ?: defaults.sensitivity,
                onboardingDone = p[Keys.ONBOARDING] ?: defaults.onboardingDone,
                glassMode = GlassMode.entries.firstOrNull { it.name == p[Keys.GLASS] } ?: defaults.glassMode,
                problemNotificationsEnabled = p[Keys.PROBLEM_NOTIFICATIONS] ?: defaults.problemNotificationsEnabled,
                theme = ThemeMode.entries.firstOrNull { it.name == p[Keys.THEME] } ?: defaults.theme,
                notificationBannerDismissedAtMs = p[Keys.NOTIF_BANNER_DISMISSED],
                rangeReadySeen = p[Keys.RANGE_READY_SEEN] ?: defaults.rangeReadySeen,
            )
        }

        private object Keys {
            val SOURCE = stringPreferencesKey("source_package")
            val NOTIFICATIONS = booleanPreferencesKey("notifications_enabled")
            val SENSITIVITY = stringPreferencesKey("sensitivity")
            val ONBOARDING = booleanPreferencesKey("onboarding_done")
            val GLASS = stringPreferencesKey("glass_mode")
            val PROBLEM_NOTIFICATIONS = booleanPreferencesKey("problem_notifications_enabled")
            val THEME = stringPreferencesKey("theme")
            val NOTIF_BANNER_DISMISSED = longPreferencesKey("notification_banner_dismissed_at")
            val RANGE_READY_SEEN = booleanPreferencesKey("range_ready_seen")
        }

        /** Constants. */
        companion object {
            /** Name of the Preferences DataStore file. */
            const val FILE_NAME: String = "settings"
        }
    }
