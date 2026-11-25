// Synthetic sample values for previews and screenshot tests.
@file:Suppress("MagicNumber")

package io.github.alien2003.nightbaseline.ui.settings

import io.github.alien2003.nightbaseline.algo.MetricId
import io.github.alien2003.nightbaseline.ui.material.EffectsReason
import io.github.alien2003.nightbaseline.ui.material.GlassPreference
import io.github.alien2003.nightbaseline.ui.sample.SampleData

/**
 * Settings states built from [SampleData] (synthetic source names, no package of a real app, no health data) for the
 * screenshot tests and previews. One builder per state of `states.md` 7.4.
 */
object SettingsSamples {
    /** Synthetic sources: the sample source with every metric, a ring without SpO2, and an app with no recent sleep. */
    val sources: List<SourceOption> =
        listOf(
            SourceOption(
                "sample.demo.watch",
                SampleData.SOURCE,
                SampleData.LAST_WAKE,
                listOf(MetricId.SLEEPING_HR, MetricId.RESPIRATORY_RATE, MetricId.SPO2, MetricId.SLEEP_DURATION, MetricId.HRV),
            ),
            SourceOption(
                "sample.demo.ring",
                "Demo ring",
                SampleData.LAST_WAKE.minusDays(4),
                listOf(MetricId.SLEEPING_HR, MetricId.SLEEP_DURATION, MetricId.SKIN_TEMPERATURE),
            ),
            SourceOption("sample.sleep.diary", "Sleep diary", null, listOf(MetricId.SLEEP_DURATION)),
        )

    /** Everything on, glass automatic and on, Balanced with a preview. */
    val default: SettingsState =
        SettingsState(
            sourceLabel = sources[0].label,
            sourcePackage = sources[0].packageName,
            sources = sources,
            lastRead = "07:42",
            previewNotes = 1,
            version = "0.1.0",
        )

    /** `ST-NOTIF-BLOCKED`: the system permission is denied; glass off by battery saver. */
    val blocked: SettingsState =
        default.copy(
            notificationAccess = NotificationAccess.Blocked,
            glassReason = EffectsReason.BatterySaver,
        )

    /** Notifications not asked yet, never read, learning (no preview), glass off by the person, dark theme. */
    val fresh: SettingsState =
        default.copy(
            notificationAccess = NotificationAccess.NotAsked,
            lastRead = null,
            previewNotes = null,
            glass = GlassPreference.Off,
            glassReason = EffectsReason.UserOff,
            theme = ThemeChoice.Dark,
        )

    /** A page of [base] (with its section selected for two-pane layouts). */
    fun page(
        page: SettingsPage,
        base: SettingsState = default,
    ): SettingsState = base.copy(page = page, section = page.section ?: base.section)

    /** The sensitivity sub-screen with Fewer notes selected. */
    val sensitivityFewer: SettingsState =
        page(SettingsPage.Sensitivity, default.copy(sensitivity = SensitivityPreset.Fewer, previewNotes = 0))

    /** `ST-SOURCE-CHANGE`: the source picker with the confirmation dialog for the second source. */
    val sourceChange: SettingsState = page(SettingsPage.SourcePicker).copy(pendingSource = sources[1])
}
