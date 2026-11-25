package io.github.alien2003.nightbaseline.nav

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.alien2003.nightbaseline.testing.TestApp
import io.github.alien2003.nightbaseline.ui.layout.LayoutInputs
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import io.github.alien2003.nightbaseline.ui.material.EffectsDecision
import io.github.alien2003.nightbaseline.ui.sample.SampleData
import io.github.alien2003.nightbaseline.ui.settings.SettingsPage
import io.github.alien2003.nightbaseline.ui.theme.NightBaselineTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The integrated navigation graph on Robolectric with fake repositories (synthetic nights): start destination,
 * deep links (night and fix), tabs, and the list-detail hand-over when the window changes between one and two panes
 * (`layouts.md` 8.2 rule 4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w412dp-h915dp-port-420dpi")
class NavigationTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val compact = resolveLayout(LayoutInputs(412f, 915f))
    private val expanded = resolveLayout(LayoutInputs(1000f, 800f))

    private lateinit var nav: NavHostController
    private val layout = mutableStateOf(compact)
    private val link = mutableStateOf<DeepLink?>(null)

    private fun launch(
        app: TestApp = TestApp(),
        onboardingDone: Boolean = true,
        start: LayoutSpec = compact,
    ) {
        layout.value = start
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            nav = rememberNavController()
            CompositionLocalProvider(LocalViewModelFactory provides app.factory) {
                NightBaselineTheme(darkTheme = false, effects = EffectsDecision.ForcedSolid) {
                    AppNavHost(
                        layout = layout.value,
                        onboardingDone = onboardingDone,
                        system = SystemActions.None,
                        newestNight = SampleData.LAST_WAKE.toString(),
                        deepLink = link.value,
                        onDeepLinkHandled = { link.value = null },
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

    private fun route(): String =
        nav.currentBackStackEntry
            ?.destination
            ?.route
            .orEmpty()

    @Test
    fun `starts at Home after onboarding and at onboarding before`() {
        launch()
        assertTrue(route(), route().contains("HomeRoute"))
    }

    @Test
    fun `first launch opens onboarding and keeps a deep link until it is done`() {
        link.value = DeepLink.Night("2026-11-20")
        launch(app = TestApp(nights = 0), onboardingDone = false)
        assertTrue(route(), route().contains("OnboardingRoute"))
        assertEquals(DeepLink.Night("2026-11-20"), link.value)
    }

    @Test
    fun `a night link opens Night detail above Home, the newest night opens Home`() {
        launch()
        val date = SampleData.LAST_WAKE.minusDays(3).toString()
        compose.runOnIdle { link.value = DeepLink.Night(date) }
        idle()
        assertTrue(route(), route().contains("NightRoute"))
        assertEquals(date, nav.currentBackStackEntry!!.toRoute<NightRoute>().date)
        assertTrue(
            nav.previousBackStackEntry!!
                .destination.route!!
                .contains("HomeRoute"),
        )
        compose.runOnIdle { nav.popBackStack() }
        idle()
        assertTrue(route().contains("HomeRoute"))
        compose.runOnIdle { link.value = DeepLink.Night(SampleData.LAST_WAKE.toString()) }
        idle()
        assertTrue(route(), route().contains("HomeRoute"))
        assertEquals(null, link.value)
    }

    @Test
    fun `fix links open the source picker or Home`() {
        launch()
        compose.runOnIdle { link.value = DeepLink.Fix(FixProblem.SourceSync) }
        idle()
        assertEquals(SettingsPage.SourcePicker.name, nav.currentBackStackEntry!!.toRoute<SettingsRoute>().page)
        compose.runOnIdle { link.value = DeepLink.Fix(FixProblem.HealthConnect) }
        idle()
        assertTrue(route(), route().contains("HomeRoute"))
    }

    @Test
    fun `the bar opens the top-level sections`() {
        launch()
        compose.onAllNodesWithText("History")[0].performClick()
        idle()
        assertTrue(route(), route().contains("HistoryRoute"))
        compose.onAllNodesWithText("Settings")[0].performClick()
        idle()
        assertTrue(route(), route().contains("SettingsRoute"))
        compose.onAllNodesWithText("Last night")[0].performClick()
        idle()
        assertTrue(route(), route().contains("HomeRoute"))
    }

    @Test
    fun `metric detail moves into the detail pane on unfold and back on top on fold`() {
        launch()
        compose.runOnIdle { nav.navigate(MetricRoute("respiratory_rate")) }
        idle()
        assertTrue(route().contains("MetricRoute"))
        compose.runOnIdle { layout.value = expanded }
        idle()
        assertTrue(route(), route().contains("HomeRoute"))
        compose.runOnIdle { layout.value = compact }
        idle()
        assertTrue(route(), route().contains("MetricRoute"))
        assertEquals("respiratory_rate", nav.currentBackStackEntry!!.toRoute<MetricRoute>().metric)
    }
}
