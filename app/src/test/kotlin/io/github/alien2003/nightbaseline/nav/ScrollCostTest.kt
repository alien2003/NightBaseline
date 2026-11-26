package io.github.alien2003.nightbaseline.nav

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.alien2003.nightbaseline.testing.TestApp
import io.github.alien2003.nightbaseline.ui.components.TopLevel
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
 * Scrolling never recomposes a screen (ADR 0008, docs/development.md "Performance"): on a phone layout with synthetic
 * nights, flinging Last night, History and Settings up and back down must not compose the screen bodies again. The
 * top bar reads the scroll offset in its layers and derived state only. Counted through [PerfProbe].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w412dp-h915dp-port-420dpi")
class ScrollCostTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val counts = ConcurrentHashMap<String, AtomicInteger>()
    private lateinit var nav: NavHostController

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
        val layout = resolveLayout(LayoutInputs(PHONE_W, PHONE_H))
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
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
    }

    private fun count(name: String): Int = counts[name]?.get() ?: 0

    private fun scrollUpAndBack() {
        repeat(FLINGS) {
            compose.onRoot().performTouchInput { swipeUp() }
            idle()
        }
        repeat(FLINGS) {
            compose.onRoot().performTouchInput { swipeDown() }
            idle()
        }
    }

    @Test
    fun `scrolling a screen does not compose its body again`() {
        launch()
        for ((tab, probe) in listOf(
            TopLevel.Home to "HomeScreen",
            TopLevel.History to "HistoryScreen",
            TopLevel.Settings to "SettingsScreen",
        )) {
            open(tab)
            val before = count(probe)
            val rows = count("NightRow")
            assertTrue("$probe was composed", before > 0)
            scrollUpAndBack()
            assertEquals("$probe compositions while scrolling", before, count(probe))
            // The swipes really scrolled: the History feed composed the rows that came into view.
            if (tab == TopLevel.History) assertTrue("History rows scrolled into view", count("NightRow") > rows)
        }
    }

    private companion object {
        const val PHONE_W = 412f
        const val PHONE_H = 915f
        const val FLINGS = 3
    }
}
