package io.github.alien2003.nightbaseline.ui.settings

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.alien2003.nightbaseline.R
import io.github.alien2003.nightbaseline.ui.layout.LayoutInputs
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import io.github.alien2003.nightbaseline.ui.material.EffectsDecision
import io.github.alien2003.nightbaseline.ui.onboarding.HealthConnectStatus
import io.github.alien2003.nightbaseline.ui.onboarding.OnboardingSamples
import io.github.alien2003.nightbaseline.ui.onboarding.OnboardingScreen
import io.github.alien2003.nightbaseline.ui.onboarding.SourceRecheck
import io.github.alien2003.nightbaseline.ui.theme.NightBaselineTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What Settings and onboarding show without a usable Health Connect, and the visible result of a "Check again" that
 * changed nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w393dp-h851dp-port")
class HealthConnectMissingUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private fun string(id: Int): String = ApplicationProvider.getApplicationContext<Application>().getString(id)

    private fun show(content: @Composable () -> Unit) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { NightBaselineTheme(darkTheme = false, effects = EffectsDecision.ForcedSolid) { content() } }
        compose.waitForIdle()
    }

    @Test
    fun sourceGroupSaysHealthConnectNeedsAnUpdateWithTheInstallAction() {
        var installs = 0
        val state = SettingsSamples.fresh.copy(healthConnect = HealthConnectStatus.NeedsUpdate)
        show { SourceGroup(state, SettingsActions(onInstallHealthConnect = { installs++ })) }
        compose.onNodeWithText(string(R.string.state_hc_update_title)).assertExists()
        compose.onAllNodesWithText(string(R.string.settings_never_read)).assertCountEquals(0)
        compose.onAllNodesWithText(string(R.string.settings_hc_permissions)).assertCountEquals(0)
        compose.onNodeWithText(string(R.string.settings_hc_install)).performClick()
        assertEquals(1, installs)
    }

    @Test
    fun sourcePageShowsHealthConnectMissingInsteadOfNoSleepData() {
        val state =
            SettingsSamples
                .page(SettingsPage.SourcePicker)
                .copy(sources = emptyList(), healthConnect = HealthConnectStatus.Unavailable, sourcesRechecked = true)
        show { SourcePickerContent(state, SettingsActions()) }
        compose.onNodeWithText(string(R.string.state_hc_none_title)).assertExists()
        compose.onAllNodesWithText(string(R.string.onb_nosource_title)).assertCountEquals(0)
        compose.onNodeWithText(string(R.string.hc_still_missing)).assertExists()
    }

    @Test
    fun emptySourcePageHasCheckAgainWithBusyStateAndResult() {
        var checks = 0
        val empty = SettingsSamples.page(SettingsPage.SourcePicker).copy(sources = emptyList())
        show { SourcePickerContent(empty, SettingsActions(onCheckSources = { checks++ })) }
        compose.onNodeWithText(string(R.string.onb_nosource_title)).assertExists()
        compose.onNodeWithText(string(R.string.action_check_again)).performClick()
        assertEquals(1, checks)
    }

    @Test
    fun emptySourcePageSaysWhenTheCheckFoundNothing() {
        val state = SettingsSamples.page(SettingsPage.SourcePicker).copy(sources = emptyList(), sourcesRechecked = true)
        show { SourcePickerContent(state, SettingsActions()) }
        compose.onNodeWithText(string(R.string.sources_still_none)).assertExists()
    }

    @Test
    fun emptySourcePageShowsCheckingWhileItRuns() {
        val state = SettingsSamples.page(SettingsPage.SourcePicker).copy(sources = emptyList(), checkingSources = true)
        show { SourcePickerContent(state, SettingsActions()) }
        compose.onNodeWithText(string(R.string.action_checking)).assertExists()
    }

    @Test
    fun onboardingSourceStepSaysHealthConnectIsStillMissing() {
        val state = OnboardingSamples.all.getValue("hc-update").copy(recheck = SourceRecheck.HealthConnectStillMissing)
        show { OnboardingScreen(state, resolveLayout(LayoutInputs(393f, 851f))) }
        compose.onNodeWithText(string(R.string.hc_still_missing)).assertExists()
    }
}
