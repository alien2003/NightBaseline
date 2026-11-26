package io.github.alien2003.nightbaseline.nav

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.alien2003.nightbaseline.testing.TestApp
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.layout.LayoutInputs
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import io.github.alien2003.nightbaseline.ui.material.EffectsDecision
import io.github.alien2003.nightbaseline.ui.sample.SampleData
import io.github.alien2003.nightbaseline.ui.theme.NightBaselineTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Settings sub-pages on the owner's outer screen (Compact, review round 5): Back at any moment of the push (even in
 * the first frame after the tap, before the screen state arrives from the compute dispatcher) closes the sub-page, never the
 * Settings section, and real touches work on the list afterwards (the row opens the sub-page again).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w482dp-h1107dp-port-378dpi")
class SettingsSubPageBackTest {
    @get:Rule
    val compose = createEmptyComposeRule()
    private lateinit var nav: NavHostController
    private lateinit var activity: ComponentActivity

    private fun launch() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            nav = rememberNavController()
            CompositionLocalProvider(LocalViewModelFactory provides TestApp().factory) {
                NightBaselineTheme(darkTheme = false, effects = EffectsDecision.ForcedSolid) {
                    AppNavHost(
                        resolveLayout(LayoutInputs(482f, 1107f)),
                        true,
                        SystemActions.None,
                        newestNight = SampleData.LAST_WAKE.toString(),
                        navController = nav,
                    )
                }
            }
        }
        idle()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private fun sensitivityShown(): Boolean = compose.onAllNodesWithText("Fewer notes").fetchSemanticsNodes().isNotEmpty()

    @Test
    fun backAtAnyMomentOfThePushReturnsToTheListAndTouchesWork() {
        launch()
        compose.onAllNodesWithText("Settings")[0].performClick()
        idle()
        for (ms in listOf(16L, 50L, 120L, 250L, 1000L)) {
            compose.mainClock.autoAdvance = false
            compose.onNode(hasText("Balanced") and hasClickAction()).performScrollTo().performClick()
            compose.mainClock.advanceTimeBy(ms)
            activity.onBackPressedDispatcher.onBackPressed()
            compose.mainClock.autoAdvance = true
            idle()
            assertEquals("Back at $ms ms", TopLevel.Settings, nav.currentBackStackEntry?.topLevelTab())
            assertEquals("Back at $ms ms closes the sub-page", false, sensitivityShown())
            // A real touch on the list opens the sub-page again.
            compose.onNode(hasText("Balanced") and hasClickAction()).performScrollTo().performClick()
            idle()
            assertEquals("touch after Back at $ms ms", true, sensitivityShown())
            activity.onBackPressedDispatcher.onBackPressed()
            idle()
        }
    }
}
