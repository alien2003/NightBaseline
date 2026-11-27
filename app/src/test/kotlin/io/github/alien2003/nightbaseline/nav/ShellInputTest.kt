package io.github.alien2003.nightbaseline.nav

import android.app.Application
import android.os.Looper
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.alien2003.nightbaseline.data.hc.HcPermissions
import io.github.alien2003.nightbaseline.testing.TestApp
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.interaction.NbPageNavigator
import io.github.alien2003.nightbaseline.ui.layout.LayoutInputs
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import io.github.alien2003.nightbaseline.ui.material.EffectsDecision
import io.github.alien2003.nightbaseline.ui.onboarding.OnboardingStep
import io.github.alien2003.nightbaseline.ui.sample.SampleData
import io.github.alien2003.nightbaseline.ui.theme.NightBaselineTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Input on the integrated shell (Robolectric, synthetic nights): a fast double tap pushes one page, the background
 * banner reaches Health Connect when its dialog is no longer shown, the keyboard shortcuts work with nothing focused,
 * and "Set up again" asks first and keeps a way back to Settings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w412dp-h915dp-port-420dpi")
class ShellInputTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var nav: NavHostController
    private lateinit var activity: ComponentActivity
    private val shortcuts = KeyShortcuts()
    private val system = RecordingSystemActions()
    private var checks = 0

    private fun launch(app: TestApp = TestApp()) {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            nav = rememberNavController()
            CompositionLocalProvider(LocalViewModelFactory provides app.factory, LocalKeyShortcuts provides shortcuts) {
                NightBaselineTheme(darkTheme = false, effects = EffectsDecision.ForcedSolid) {
                    AppNavHost(
                        layout = resolveLayout(LayoutInputs(412f, 915f)),
                        onboardingDone = app.settings.current.value.onboardingDone,
                        system = system,
                        newestNight = SampleData.LAST_WAKE.toString(),
                        onCheckNow = { checks++ },
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

    private fun shown(text: String): Boolean = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun routes(): List<String> =
        nav.currentBackStack.value
            .filter { it.destination.navigatorName == NbPageNavigator.NAME }
            .map { it.topLevelTab()?.let { tab -> "${tab.name}Route" } ?: it.destination.route.orEmpty() }

    /** Two taps at the centre of the first clickable node described by [text], two frames apart (no idle between). */
    private fun doubleTap(text: String) {
        val node =
            compose
                .onAllNodes(hasContentDescription(text, substring = true) and hasClickAction())
                .onFirst()
                .performScrollTo()
        val bounds = node.fetchSemanticsNode().boundsInRoot
        val centre = Offset(bounds.center.x, bounds.center.y)
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput { click(centre) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.onRoot().performTouchInput { click(centre) }
        compose.mainClock.autoAdvance = true
        idle()
    }

    @Test
    fun `a fast double tap on a metric card pushes one page`() {
        launch()
        compose.runOnIdle { nav.navigate(NightRoute(SampleData.LAST_WAKE.minusDays(3).toString(), TopLevel.History.name)) }
        idle()
        doubleTap("Respiratory rate")
        val stack = routes()
        assertEquals(stack.toString(), 1, stack.count { it.contains("MetricRoute") })
        assertTrue(stack.toString(), stack.last().contains("MetricRoute"))
        activity.onBackPressedDispatcher.onBackPressed()
        idle()
        assertTrue("one Back returns to Night detail", routes().last().contains("NightRoute"))
    }

    @Test
    fun `background Allow opens Health Connect when no dialog came up`() {
        val core = HcPermissions.ALL - HcPermissions.READ_HEALTH_DATA_IN_BACKGROUND
        launch(TestApp(access = TestApp.allowed(granted = core)))
        system.answer = core
        compose.onNode(hasText("Allow") and hasClickAction()).performScrollTo().performClick()
        idle()
        assertEquals("the request ran", 1, system.requests)
        assertEquals("Health Connect opened, where background access can be turned on", 1, system.healthConnectOpened)
    }

    @Test
    fun `Ctrl+R and Esc work with nothing focused`() {
        launch()
        val superDispatch: (KeyEvent) -> Boolean = { activity.window.superDispatchKeyEvent(it) }
        val ctrlR = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_R, 0, KeyEvent.META_CTRL_ON)
        assertFalse("nothing focused: the window does not take the key", activity.window.superDispatchKeyEvent(ctrlR))
        assertTrue(shortcuts.dispatch(ctrlR, superDispatch))
        assertEquals(1, checks)
        compose.runOnIdle { nav.navigate(NightRoute(SampleData.LAST_WAKE.minusDays(3).toString(), TopLevel.Home.name)) }
        idle()
        val escDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE)
        val escUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE)
        assertTrue(shortcuts.dispatch(escDown, superDispatch))
        assertTrue("the key-up of a used shortcut is consumed too", shortcuts.dispatch(escUp, superDispatch))
        idle()
        assertTrue(routes().toString(), routes().last().contains("HomeRoute"))
        // At the root Esc is left to the system (nothing to go back to in the app).
        assertFalse(shortcuts.dispatch(escDown, superDispatch))
    }

    @Test
    fun `Set up again asks first, then runs from the source step with a way back to Settings`() {
        val app = TestApp()
        launch(app)
        compose.onAllNodesWithText("Settings")[0].performClick()
        idle()
        compose.onNode(hasText("Set up again") and hasClickAction()).performScrollTo().performClick()
        idle()
        assertTrue("the confirmation is shown", shown("Set up again?"))
        compose.onNode(hasText("Cancel") and hasClickAction()).performClick()
        idle()
        assertFalse(shown("Set up again?"))
        assertTrue(routes().last().contains("SettingsRoute"))
        compose.onNode(hasText("Set up again") and hasClickAction()).performScrollTo().performClick()
        idle()
        compose.onAllNodes(hasText("Set up again") and hasClickAction()).fetchSemanticsNodes().let { nodes ->
            assertEquals("the row and the dialog button", 2, nodes.size)
        }
        // The dialog's button is composed last (it is drawn above the page).
        compose.onAllNodes(hasText("Set up again") and hasClickAction())[1].performClick()
        idle()
        val top = nav.currentBackStackEntry!!
        assertTrue(routes().toString(), routes().last().contains("OnboardingRoute"))
        assertEquals(OnboardingStep.Source.name, top.toRoute<OnboardingRoute>().step)
        assertTrue("onboarding stays done during the rerun", app.settings.current.value.onboardingDone)
        activity.onBackPressedDispatcher.onBackPressed()
        idle()
        assertEquals(TopLevel.Settings, nav.currentBackStackEntry?.topLevelTab())
        assertTrue(app.settings.current.value.onboardingDone)
    }
}

/** [SystemActions] that records calls and answers the Health Connect request at once with [answer]. */
private class RecordingSystemActions : SystemActions {
    var answer: Set<String> = emptySet()
    var requests = 0
    var healthConnectOpened = 0

    override fun requestHealthPermissions(
        permissions: Set<String>,
        onResult: (Set<String>) -> Unit,
    ) {
        requests++
        onResult(answer)
    }

    override fun requestNotifications(onResult: (Boolean) -> Unit) = Unit

    override fun openHealthConnect() {
        healthConnectOpened++
    }

    override fun openStore() = Unit

    override fun openNotificationSettings() = Unit

    override fun openApp(packageName: String) = Unit
}
