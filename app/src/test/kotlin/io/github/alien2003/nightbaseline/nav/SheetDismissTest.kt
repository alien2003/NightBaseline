package io.github.alien2003.nightbaseline.nav

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.alien2003.nightbaseline.testing.TestApp
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.interaction.MODAL_PANEL_TAG
import io.github.alien2003.nightbaseline.ui.interaction.MODAL_SCRIM_TAG
import io.github.alien2003.nightbaseline.ui.layout.LayoutInputs
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import io.github.alien2003.nightbaseline.ui.material.EffectsDecision
import io.github.alien2003.nightbaseline.ui.sample.SampleData
import io.github.alien2003.nightbaseline.ui.theme.NightBaselineTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Every sheet entry point x every way to close it (`screens.md` "Sheets", review round 5): open, close by system Back,
 * a scrim tap, a drag down, the panel's own option (close button or a chosen entry) and Back while it is still
 * opening; then the panel and the scrim are gone from the tree and a touch on the screen underneath works again (the
 * trigger opens the sheet once more). Real touch input, so a leftover overlay that swallows touches fails the test.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(application = Application::class)
class SheetDismissTest(
    private val entry: Entry,
    private val way: Way,
) {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var nav: NavHostController
    private lateinit var activity: ComponentActivity

    /** A sheet and how to open it. */
    enum class Entry(
        val width: Int,
        val height: Int,
    ) {
        /** Home "What this means" -> range explainer (the shared sheet host of the graph). */
        HomeExplainer(412, 915),

        /** Metric detail info action -> range explainer (the screen's own overlay). */
        MetricExplainer(412, 915),

        /** Glance "More" -> History / Settings sheet. */
        GlanceMore(340, 352),
    }

    /** A way to close it. */
    enum class Way { Back, Scrim, Drag, Option, BackWhileOpening }

    private fun launch() {
        RuntimeEnvironment.setQualifiers("en-rUS-w${entry.width}dp-h${entry.height}dp-port-420dpi")
        val layout: LayoutSpec = resolveLayout(LayoutInputs(entry.width.toFloat(), entry.height.toFloat()))
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            nav = rememberNavController()
            CompositionLocalProvider(LocalViewModelFactory provides TestApp().factory) {
                NightBaselineTheme(darkTheme = false, effects = EffectsDecision.ForcedSolid) {
                    AppNavHost(layout, true, SystemActions.None, newestNight = SampleData.LAST_WAKE.toString(), navController = nav)
                }
            }
        }
        idle()
        if (entry == Entry.MetricExplainer) {
            compose.runOnIdle { nav.navigate(MetricRoute("respiratory_rate")) }
            idle()
        }
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private fun trigger(): SemanticsNodeInteraction =
        when (entry) {
            Entry.HomeExplainer -> compose.onAllNodes(hasText("What this means") and hasClickAction()).onFirst().performScrollTo()
            Entry.MetricExplainer -> compose.onAllNodes(hasContentDescription("How the range works") and hasClickAction()).onFirst()
            Entry.GlanceMore -> compose.onAllNodes(hasContentDescription("More") and hasClickAction()).onFirst()
        }

    private fun assertOpen() {
        compose.onAllNodesWithTag(MODAL_PANEL_TAG).assertCountEquals(1)
        compose.onAllNodesWithTag(MODAL_SCRIM_TAG).assertCountEquals(1)
    }

    private fun assertGone() {
        compose.onAllNodesWithTag(MODAL_PANEL_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(MODAL_SCRIM_TAG).assertCountEquals(0)
    }

    private fun back() = compose.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }

    @Test
    fun closesAnimatedAndLeavesNothingBehind() {
        launch()
        if (way == Way.BackWhileOpening) {
            compose.mainClock.autoAdvance = false
            trigger().performClick()
            compose.mainClock.advanceTimeBy(OPENING_MS)
            compose.onAllNodesWithTag(MODAL_PANEL_TAG).assertCountEquals(1)
            activity.onBackPressedDispatcher.onBackPressed()
            compose.mainClock.autoAdvance = true
            idle()
        } else {
            trigger().performClick()
            idle()
            assertOpen()
            when (way) {
                Way.Back -> {
                    back()
                }

                Way.Scrim -> {
                    compose.onNodeWithTag(MODAL_SCRIM_TAG).performTouchInput { click(Offset(centerX, 4f)) }
                }

                Way.Drag -> {
                    compose.onNodeWithTag(MODAL_PANEL_TAG).performTouchInput {
                        swipe(Offset(centerX, top + 12f), Offset(centerX, bottom), durationMillis = 250)
                    }
                }

                Way.Option -> {
                    if (entry == Entry.GlanceMore) {
                        compose.onAllNodes(hasText("History") and hasClickAction()).onFirst().performClick()
                    } else {
                        compose.onAllNodes(hasContentDescription("Close") and hasClickAction()).onFirst().performClick()
                    }
                }

                Way.BackWhileOpening -> {
                    Unit
                }
            }
            idle()
        }
        assertGone()
        if (entry == Entry.GlanceMore && way == Way.Option) {
            assertEquals(TopLevel.History, nav.currentBackStackEntry?.topLevelTab())
        }
        // The screen underneath takes touches again: the trigger opens the sheet once more.
        trigger().performClick()
        idle()
        assertOpen()
    }

    /** The matrix. */
    companion object {
        private const val OPENING_MS = 48L

        /** Entry x way. */
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1}")
        fun parameters(): List<Array<Any>> = Entry.entries.flatMap { e -> Way.entries.map { arrayOf<Any>(e, it) } }
    }
}
