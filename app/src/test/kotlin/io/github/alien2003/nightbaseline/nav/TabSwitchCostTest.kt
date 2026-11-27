package io.github.alien2003.nightbaseline.nav

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.alien2003.nightbaseline.testing.TestApp
import io.github.alien2003.nightbaseline.ui.components.TOP_BAR_TITLE_TAG
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.history.HistoryViewModel
import io.github.alien2003.nightbaseline.ui.home.HomeViewModel
import io.github.alien2003.nightbaseline.ui.layout.LayoutInputs
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import io.github.alien2003.nightbaseline.ui.material.EffectsDecision
import io.github.alien2003.nightbaseline.ui.sample.SampleData
import io.github.alien2003.nightbaseline.ui.shell.PerfProbe
import io.github.alien2003.nightbaseline.ui.theme.NightBaselineTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The cost of a tab switch (owner's measurement on the unfolded inner screen: about two slow UI-thread frames per
 * switch while every switch rebuilt the destination). On the owner's unfolded layout (952 x 1050 dp, rail, two panes)
 * with synthetic nights, switching Last night <-> History back and forth must not compose any screen or History row
 * again once both were shown, and must not map the ViewModels' inputs again: the sections stay composed in
 * `NbSectionHost` and keep their ViewModel subscriptions. Counted through [PerfProbe].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w952dp-h1050dp-port-378dpi")
class TabSwitchCostTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val counts = ConcurrentHashMap<String, AtomicInteger>()
    private lateinit var nav: NavHostController
    private lateinit var activity: ComponentActivity

    @Before
    fun setUp() {
        PerfProbe.sink = { name -> counts.getOrPut(name) { AtomicInteger() }.incrementAndGet() }
    }

    @After
    fun tearDown() {
        PerfProbe.sink = null
    }

    private fun launch() {
        val app = TestApp()
        val layout = resolveLayout(LayoutInputs(UNFOLDED_W, UNFOLDED_H))
        assertTrue("the owner's unfolded screen has two panes", layout.twoPanes)
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            nav = rememberNavController()
            CompositionLocalProvider(LocalViewModelFactory provides app.factory) {
                NightBaselineTheme(darkTheme = true, effects = EffectsDecision.ForcedSolid) {
                    AppNavHost(
                        layout = layout,
                        onboardingDone = true,
                        system = SystemActions.None,
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

    private fun open(tab: TopLevel) {
        compose.runOnIdle { nav.toTop(tab) }
        idle()
        assertEquals(tab, nav.currentBackStackEntry?.topLevelTab())
    }

    private fun snapshot(): Map<String, Int> = counts.mapValues { it.value.get() }.toSortedMap()

    /** The page titles in the semantics tree (only the shown section may contribute one). */
    private fun titles(): List<String> =
        compose
            .onAllNodes(hasTestTag(TOP_BAR_TITLE_TAG))
            .fetchSemanticsNodes()
            .mapNotNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text } }

    @Test
    fun `switching between shown tabs composes nothing again and maps nothing again`() {
        launch()
        open(TopLevel.History)
        val warm = snapshot()
        assertTrue("History rows were composed: $warm", (warm["NightRow"] ?: 0) > 0)
        assertTrue("Home was composed: $warm", (warm["HomeScreen"] ?: 0) > 0)
        assertTrue("Home mapped its inputs: $warm", (warm[HomeViewModel.MAP_PROBE] ?: 0) > 0)
        assertTrue("History mapped its nights: $warm", (warm[HistoryViewModel.MAP_PROBE] ?: 0) > 0)
        repeat(SWITCHES) {
            open(TopLevel.Home)
            assertEquals(listOf("Last night"), titles())
            open(TopLevel.History)
            assertEquals(listOf("History"), titles())
        }
        assertEquals("compositions or mappings after $SWITCHES warm switches", warm, snapshot())
        // Back from History returns to the still composed Home.
        compose.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
        idle()
        assertEquals(TopLevel.Home, nav.currentBackStackEntry?.topLevelTab())
        assertEquals("compositions or mappings after Back", warm, snapshot())
    }

    @Test
    fun `a section is composed on its first visit only`() {
        launch()
        assertEquals("Settings is not built before it is opened", null, snapshot()["SettingsScreen"])
        open(TopLevel.Settings)
        val first = snapshot()["SettingsScreen"] ?: 0
        assertTrue("Settings was composed", first > 0)
        open(TopLevel.Home)
        open(TopLevel.Settings)
        assertEquals(first, snapshot()["SettingsScreen"] ?: 0)
        assertEquals(listOf("Settings"), titles())
    }

    private companion object {
        const val UNFOLDED_W = 952f
        const val UNFOLDED_H = 1050f
        const val SWITCHES = 3
    }
}
