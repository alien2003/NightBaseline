package io.github.alien2003.nightbaseline.ui.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.ui.material.EffectsReason
import io.github.alien2003.nightbaseline.ui.material.GlassPreference
import java.time.LocalDate

/**
 * The three sensitivity presets of `screens.md` 7.7, mapped onto the robust z threshold of ALGORITHM.md section 6
 * (`AlgoParams.zThreshold`); the notification rule (two measurements) is the same for all three.
 */
enum class SensitivityPreset(
    /** Robust z beyond which a measurement counts as outside. */
    val zThreshold: Double,
    /** Name. */
    @param:StringRes val title: Int,
    /** One plain-language line. */
    @param:StringRes val description: Int,
) {
    /** z 3.0: only larger changes. */
    Fewer(3.0, R.string.sens_fewer, R.string.sens_fewer_desc),

    /** z 2.5: the default. */
    Balanced(2.5, R.string.sens_balanced, R.string.sens_balanced_desc),

    /** z 2.0: smaller changes count too. */
    More(2.0, R.string.sens_more, R.string.sens_more_desc),
}

/** Settings > Appearance > Theme. */
enum class ThemeChoice(
    /** Label. */
    @param:StringRes val label: Int,
) {
    /** Follow the system. */
    System(R.string.theme_system),

    /** Always light. */
    Light(R.string.theme_light),

    /** Always dark. */
    Dark(R.string.theme_dark),
}

/** The system side of notifications (POST_NOTIFICATIONS on API 33+, and the `night_results` channel). */
enum class NotificationAccess {
    /** Allowed (or not needed below API 33). */
    Allowed,

    /** Not requested yet: the row asks for it. */
    NotAsked,

    /** Denied, or the channel is blocked: the switches are disabled and the row opens system settings. */
    Blocked,
}

/** A Health Connect source app and what it writes. Labels come from the package manager at runtime. */
@Immutable
data class SourceOption(
    /** Package name (the selection key). */
    val packageName: String,
    /** App label. */
    val label: String,
    /** Wake date of its newest sleep session in the last 30 days, or null. */
    val lastSleep: LocalDate?,
    /** Metrics it writes, registry order. */
    val metrics: List<MetricId>,
)

/** Sections of Settings (`screens.md` 7.7), in display order. */
enum class SettingsSection(
    /** Group title. */
    @param:StringRes val title: Int,
) {
    /** Data source. */
    Source(R.string.settings_group_source),

    /** Notifications. */
    Notifications(R.string.settings_group_notifications),

    /** Sensitivity. */
    Sensitivity(R.string.settings_group_sensitivity),

    /** Appearance. */
    Appearance(R.string.settings_group_appearance),

    /** Data and privacy. */
    Privacy(R.string.setup_group_privacy),

    /** About. */
    About(R.string.settings_group_about),
}

/** Pages of Settings: the grouped list and its sub-screens (`screens.md` 2). */
enum class SettingsPage(
    /** Section the page belongs to (selected in two-pane layouts). */
    val section: SettingsSection?,
) {
    /** The grouped list. */
    Main(null),

    /** `settings/source`. */
    SourcePicker(SettingsSection.Source),

    /** `settings/sensitivity`. */
    Sensitivity(SettingsSection.Sensitivity),

    /** `settings/about`. */
    About(SettingsSection.About),

    /** `settings/licenses`. */
    Licenses(SettingsSection.About),

    /** The in-app privacy policy. */
    Privacy(SettingsSection.Privacy),
    ;

    /** The page Back returns to: About from Licenses, the list from the other sub-pages; null on the list. */
    fun parent(): SettingsPage? =
        when (this) {
            Main -> null
            Licenses -> About
            else -> Main
        }
}

/** An open-source component listed on the licenses page (names are proper nouns, not translated). */
@Immutable
data class LicenseEntry(
    /** Library. */
    val name: String,
    /** License name. */
    val license: String,
)

/** What Settings shows. Stateless input of [SettingsScreen]; the ViewModel builds it later. */
@Immutable
data class SettingsState(
    /** Page shown (single pane) or the sub-page in the detail pane (two panes). */
    val page: SettingsPage = SettingsPage.Main,
    /** Section selected in the two-pane list. */
    val section: SettingsSection = SettingsSection.Source,
    /** Selected source label. */
    val sourceLabel: String,
    /** Selected source package. */
    val sourcePackage: String,
    /** Sources found in Health Connect. */
    val sources: List<SourceOption> = emptyList(),
    /** Last successful daily run, already formatted for the locale; null when never read. */
    val lastRead: String? = null,
    /** "Night results" switch. */
    val notifyResults: Boolean = true,
    /** "Problems reading data" switch. */
    val notifyProblems: Boolean = true,
    /** System permission / channel state. */
    val notificationAccess: NotificationAccess = NotificationAccess.Allowed,
    /** Sensitivity preset. */
    val sensitivity: SensitivityPreset = SensitivityPreset.Balanced,
    /** Preview of the preset: notes over the last [previewNights] nights; null while learning (`ST-SENS-LEARNING`). */
    val previewNotes: Int? = null,
    /** Nights the preview looks at. */
    val previewNights: Int = PREVIEW_NIGHTS,
    /** Theme. */
    val theme: ThemeChoice = ThemeChoice.System,
    /** Glass effects preference. */
    val glass: GlassPreference = GlassPreference.Automatic,
    /** Why the effects resolver chose its mode (the "Off now: ..." summary when Automatic resolved to solid). */
    val glassReason: EffectsReason = EffectsReason.Automatic,
    /** App version name. */
    val version: String,
    /** Licenses page entries. */
    val licenses: List<LicenseEntry> = DefaultLicenses,
    /** A source the person picked that waits for the `ST-SOURCE-CHANGE` confirmation dialog. */
    val pendingSource: SourceOption? = null,
) {
    /** Notification switches can change only when the system allows notifications. */
    val notificationsBlocked: Boolean get() = notificationAccess == NotificationAccess.Blocked

    /** Defaults. */
    companion object {
        /** Nights of the sensitivity preview. */
        const val PREVIEW_NIGHTS: Int = 30
    }
}

/** Callbacks of [SettingsScreen]; every action is a callback, the screen keeps no data state. */
@Immutable
data class SettingsActions(
    /** Open a page (sub-screen, or the detail pane content). */
    val onOpenPage: (SettingsPage) -> Unit = {},
    /** Select a section in the two-pane list. */
    val onSelectSection: (SettingsSection) -> Unit = {},
    /** Back from a sub-page. */
    val onBack: () -> Unit = {},
    /** Pick a source (opens the confirmation when it differs from the current one). */
    val onPickSource: (SourceOption) -> Unit = {},
    /** Confirm the pending source change. */
    val onConfirmSource: () -> Unit = {},
    /** Cancel the pending source change. */
    val onCancelSource: () -> Unit = {},
    /** Open the Health Connect permission screen of the app. */
    val onOpenHealthConnect: () -> Unit = {},
    /** Restart onboarding at the source step. */
    val onSetupAgain: () -> Unit = {},
    /** Ask for the notification permission (API 33+). */
    val onRequestNotifications: () -> Unit = {},
    /** Open the system notification settings of the app. */
    val onOpenNotificationSettings: () -> Unit = {},
    /** Toggle "Night results". */
    val onNotifyResults: (Boolean) -> Unit = {},
    /** Toggle "Problems reading data". */
    val onNotifyProblems: (Boolean) -> Unit = {},
    /** Choose a sensitivity preset. */
    val onSensitivity: (SensitivityPreset) -> Unit = {},
    /** Choose a theme. */
    val onTheme: (ThemeChoice) -> Unit = {},
    /** Choose the glass preference. */
    val onGlass: (GlassPreference) -> Unit = {},
)

/** Libraries the app and its modules ship (the Gradle dependencies), for the licenses page. */
val DefaultLicenses: List<LicenseEntry> =
    listOf(
        LicenseEntry("AndroidX (Core, Activity, Lifecycle, Room, DataStore, WorkManager)", "Apache License 2.0"),
        LicenseEntry("Jetpack Compose and Material 3", "Apache License 2.0"),
        LicenseEntry("Health Connect client", "Apache License 2.0"),
        LicenseEntry("Hilt and Dagger", "Apache License 2.0"),
        LicenseEntry("Kotlin and kotlinx.coroutines", "Apache License 2.0"),
        LicenseEntry("Haze", "Apache License 2.0"),
    )
