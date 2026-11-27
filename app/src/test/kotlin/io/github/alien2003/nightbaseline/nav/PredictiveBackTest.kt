package io.github.alien2003.nightbaseline.nav

import android.app.Application
import android.os.Looper
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.alien2003.nightbaseline.testing.TestApp
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.interaction.NbFrame
import io.github.alien2003.nightbaseline.ui.interaction.NbKind
import io.github.alien2003.nightbaseline.ui.interaction.NbMotionProbe
import io.github.alien2003.nightbaseline.ui.interaction.NbPhysics
import io.github.alien2003.nightbaseline.ui.layout.LayoutInputs
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import io.github.alien2003.nightbaseline.ui.material.EffectsDecision
import io.github.alien2003.nightbaseline.ui.onboarding.OnboardingStep
import io.github.alien2003.nightbaseline.ui.sample.SampleData
import io.github.alien2003.nightbaseline.ui.settings.SettingsPage
import io.github.alien2003.nightbaseline.ui.shell.PerfProbe
import io.github.alien2003.nightbaseline.ui.theme.NightBaselineTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.pow

/**
 * Predictive back on the interaction framework (ADR 0011), driven through the activity's OnBackPressedDispatcher
 * with BackEventCompat start / progress / cancel / commit as the system sends them. Every level (pushed page, section,
 * settings sub-page, onboarding step, detail pane) is the same Page geometry on the same p: the page leaving stays
 * exactly under the finger (`tx = e W g`), the page underneath comes from a third of the width (`tx = -e W/3 s`,
 * `s = (1 - g)^(1/gamma)`), a cancel brings everything back to rest, a commit goes back one level, and at the root
 * nothing takes Back (the system's back-to-home). Read through [NbMotionProbe].
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w412dp-h915dp-port-420dpi")
class PredictiveBackTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val compact = resolveLayout(LayoutInputs(412f, 915f))
    private val expanded = resolveLayout(LayoutInputs(1000f, 800f))

    private lateinit var nav: NavHostController
    private lateinit var activity: ComponentActivity
    private val layout = mutableStateOf(compact)
    private val frames = CopyOnWriteArrayList<NbFrame>()
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    @Before
    fun setUp() {
        NbMotionProbe.frames = { frames += it }
        PerfProbe.sink = { name -> counts.getOrPut(name) { AtomicInteger() }.incrementAndGet() }
    }

    @After
    fun tearDown() {
        NbMotionProbe.frames = null
        PerfProbe.sink = null
    }

    private fun launch(
        app: TestApp = TestApp(),
        onboardingDone: Boolean = true,
        start: LayoutSpec = compact,
        reducedMotion: Boolean? = null,
    ) {
        layout.value = start
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            nav = rememberNavController()
            CompositionLocalProvider(LocalViewModelFactory provides app.factory) {
                NightBaselineTheme(darkTheme = true, effects = EffectsDecision.ForcedSolid, reducedMotion = reducedMotion) {
                    AppNavHost(
                        layout = layout.value,
                        onboardingDone = onboardingDone,
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

    private val back get() = activity.onBackPressedDispatcher

    private fun startGesture(edge: Int = BackEventCompat.EDGE_LEFT) {
        frames.clear()
        back.dispatchOnBackStarted(BackEventCompat(0f, TOUCH_Y, 0f, edge))
        idle()
    }

    private fun progress(
        fraction: Float,
        edge: Int = BackEventCompat.EDGE_LEFT,
    ) {
        back.dispatchOnBackProgressed(BackEventCompat(fraction * WIDTH_PX, TOUCH_Y, fraction, edge))
        idle()
    }

    private fun cancel() {
        back.dispatchOnBackCancelled()
        idle()
    }

    private fun commit() {
        back.onBackPressed()
        idle()
    }

    private fun count(name: String): Int = counts[name]?.get() ?: 0

    private fun route(): String =
        nav.currentBackStackEntry?.topLevelTab()?.let { "${it.name}Route" } ?: nav.currentBackStackEntry
            ?.destination
            ?.route
            .orEmpty()

    private fun shown(text: String): Boolean = compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun last(host: String): NbFrame? = frames.lastOrNull { it.host == host }

    /**
     * The last frame of [host] is a back gesture at finger [g] from [leaving] to [under]: the leaving page exactly
     * under the finger, the page underneath at `-e W/3 s`.
     */
    private fun assertUnderFinger(
        host: String,
        leaving: Any,
        under: Any,
        g: Float,
        e: Int = 1,
        kind: NbKind = NbKind.Page,
    ) {
        val frame = last(host)
        assertNotNull("no frame of $host", frame)
        frame!!
        assertEquals("kind of $host", kind, frame.kind)
        assertTrue("$host is driven by the finger", frame.gesture)
        assertTrue("same spring object for every kind", frame.spring === NbPhysics.Nav)
        assertEquals("leaving page of $host", leaving, frame.from)
        assertEquals("page under $host", under, frame.to)
        val w = frame.width
        assertEquals("the leaving page is under the finger", e * w * g, frame.fromPose.tx, PX)
        val s = (1f - g).pow(1f / NbPhysics.PAGE_GAMMA)
        assertEquals("the page underneath in its covered pose", -e * w * NbPhysics.PARALLAX * s, frame.toPose.tx, PX)
        assertEquals("no scale", 1f, frame.fromPose.scale, 0f)
    }

    private fun openNight(): String {
        compose.runOnIdle { nav.navigate(NightRoute(SampleData.LAST_WAKE.minusDays(3).toString(), TopLevel.Home.name)) }
        idle()
        assertTrue(route(), route().contains("NightRoute"))
        return nav.currentBackStackEntry!!.id
    }

    @Test
    fun `detail route follows the finger, cancel restores it exactly, commit pops`() {
        launch()
        val home = nav.currentBackStackEntry!!.id
        val night = openNight()
        assertTrue("Back is taken on a pushed page", back.hasEnabledCallbacks())
        val backButton = hasContentDescription("Back") and hasClickAction()
        val rest = compose.onNode(backButton).fetchSemanticsNode().boundsInRoot
        startGesture()
        progress(0.25f)
        val screens = count("NightDetailScreen") + count("HomeScreen")
        progress(0.5f)
        progress(0.75f)
        assertUnderFinger("pages", night, home, 0.75f)
        assertEquals("progress frames recompose no page content", screens, count("NightDetailScreen") + count("HomeScreen"))
        assertTrue(route(), route().contains("NightRoute"))
        cancel()
        assertEquals("back at rest exactly", rest, compose.onNode(backButton).fetchSemanticsNode().boundsInRoot)
        assertTrue(route(), route().contains("NightRoute"))
        assertTrue(back.hasEnabledCallbacks())
        // Again, from the right edge: the page follows the finger to the left; this time it is committed.
        startGesture(BackEventCompat.EDGE_RIGHT)
        progress(0.4f, BackEventCompat.EDGE_RIGHT)
        assertUnderFinger("pages", night, home, 0.4f, e = -1)
        commit()
        assertTrue(route(), route().contains("HomeRoute"))
        assertFalse("at the root nothing takes Back (system back-to-home)", back.hasEnabledCallbacks())
    }

    @Test
    fun `a second gesture during the settle of the first one`() {
        launch()
        openNight()
        compose.runOnIdle { nav.navigate(MetricRoute("respiratory_rate", SampleData.LAST_WAKE.toString(), TopLevel.Home.name)) }
        idle()
        val metric = nav.currentBackStackEntry!!.id
        startGesture()
        progress(0.5f)
        assertEquals(metric, last("pages")?.from)
        // Commit and start the next gesture before the pop has settled.
        compose.mainClock.autoAdvance = false
        back.onBackPressed()
        compose.mainClock.advanceTimeBy(100)
        val night = nav.currentBackStackEntry!!.id
        assertTrue(route(), route().contains("NightRoute"))
        back.dispatchOnBackStarted(BackEventCompat(0f, TOUCH_Y, 0f, BackEventCompat.EDGE_LEFT))
        back.dispatchOnBackProgressed(BackEventCompat(100f, TOUCH_Y, 0.3f, BackEventCompat.EDGE_LEFT))
        compose.mainClock.advanceTimeBy(100)
        // Cancel while the first pop is still settling: everything comes to rest on Night.
        back.dispatchOnBackCancelled()
        compose.mainClock.autoAdvance = true
        idle()
        assertTrue(route(), route().contains("NightRoute"))
        startGesture()
        progress(0.6f)
        assertEquals(night, last("pages")?.from)
        commit()
        assertTrue(route(), route().contains("HomeRoute"))
    }

    @Test
    fun `a second gesture during a cancel over the History tab pops once and keeps the tab`() {
        launch()
        compose.onAllNodesWithText("History")[0].performClick()
        idle()
        compose.runOnIdle { nav.navigate(NightRoute(SampleData.LAST_WAKE.minusDays(3).toString(), TopLevel.History.name)) }
        idle()
        val night = nav.currentBackStackEntry!!.id
        val depth = nav.currentBackStack.value.size
        startGesture()
        progress(0.5f)
        assertEquals(night, last("pages")?.from)
        // Cancel, and start the next gesture while the page is still on its way back to rest: the History section
        // underneath is composed, and its own tab -> Home target must not take this gesture.
        compose.mainClock.autoAdvance = false
        back.dispatchOnBackCancelled()
        compose.mainClock.advanceTimeBy(48)
        frames.clear()
        back.dispatchOnBackStarted(BackEventCompat(0f, TOUCH_Y, 0f, BackEventCompat.EDGE_LEFT))
        back.dispatchOnBackProgressed(BackEventCompat(400f, TOUCH_Y, 0.4f, BackEventCompat.EDGE_LEFT))
        compose.mainClock.advanceTimeBy(32)
        assertTrue("the second gesture moves the Night page", frames.any { it.host == "pages" && it.gesture && it.from == night })
        assertTrue("the section host does not take it", frames.none { it.host == "sections" })
        back.onBackPressed()
        compose.mainClock.autoAdvance = true
        idle()
        assertEquals("one pop", depth - 1, nav.currentBackStack.value.size)
        assertEquals("back on the History tab", TopLevel.History, nav.currentBackStackEntry?.topLevelTab())
        assertTrue("History still takes Back (to Home)", back.hasEnabledCallbacks())
    }

    @Test
    fun `a gesture during the cancel of the same page resumes where the page is`() {
        launch()
        openNight()
        compose.runOnIdle { nav.navigate(MetricRoute("respiratory_rate", SampleData.LAST_WAKE.toString(), TopLevel.Home.name)) }
        idle()
        startGesture()
        progress(0.8f)
        compose.mainClock.autoAdvance = false
        back.dispatchOnBackCancelled()
        compose.mainClock.advanceTimeBy(32)
        val during = last("pages")!!.p
        assertTrue("on its way back: $during", during > 0.05f && during < 0.95f)
        back.dispatchOnBackStarted(BackEventCompat(0f, TOUCH_Y, 0f, BackEventCompat.EDGE_LEFT))
        back.dispatchOnBackProgressed(BackEventCompat(0f, TOUCH_Y, 0f, BackEventCompat.EDGE_LEFT))
        compose.mainClock.advanceTimeByFrame()
        val resumed = last("pages")!!.p
        assertEquals("no jump: the gesture starts where the page is", during, resumed, 0.05f)
        back.dispatchOnBackProgressed(BackEventCompat(1080f, TOUCH_Y, 1f, BackEventCompat.EDGE_LEFT))
        compose.mainClock.advanceTimeByFrame()
        assertEquals("the end of the gesture is the end of the pop", 1f, last("pages")!!.p, 1e-4f)
        back.onBackPressed()
        compose.mainClock.autoAdvance = true
        idle()
        assertTrue(route(), route().contains("NightRoute"))
    }

    @Test
    fun `tab to Home is the same Page geometry as a pop`() {
        launch()
        assertFalse("Home: no handler", back.hasEnabledCallbacks())
        compose.onAllNodesWithText("History")[0].performClick()
        idle()
        assertTrue(route(), route().contains("HistoryRoute"))
        assertTrue(back.hasEnabledCallbacks())
        startGesture()
        progress(0.3f)
        val home = count("HomeScreen")
        progress(0.6f)
        assertUnderFinger("sections", TopLevel.History, TopLevel.Home, 0.6f, kind = NbKind.Tab)
        assertEquals("no recomposition per progress frame", home, count("HomeScreen"))
        cancel()
        assertTrue(route(), route().contains("HistoryRoute"))
        // A right-edge swipe still moves the section the bar-order way (Home is at the start).
        startGesture(BackEventCompat.EDGE_RIGHT)
        progress(0.5f, BackEventCompat.EDGE_RIGHT)
        assertUnderFinger("sections", TopLevel.History, TopLevel.Home, 0.5f, kind = NbKind.Tab)
        commit()
        assertTrue(route(), route().contains("HomeRoute"))
        assertFalse(back.hasEnabledCallbacks())
    }

    @Test
    fun `settings sub-page follows the finger over the list and goes back to it`() {
        launch()
        compose.onAllNodesWithText("Settings")[0].performClick()
        idle()
        compose.onNode(hasText("Balanced") and hasClickAction()).performScrollTo().performClick()
        idle()
        assertTrue(shown("Fewer notes"))
        startGesture()
        progress(0.5f)
        assertUnderFinger("settings", SettingsPage.Sensitivity, SettingsPage.Main, 0.5f)
        assertTrue("the section does not move", frames.none { it.host == "sections" })
        cancel()
        assertTrue(shown("Fewer notes"))
        startGesture()
        progress(0.5f)
        commit()
        assertEquals(TopLevel.Settings, nav.currentBackStackEntry?.topLevelTab())
        assertFalse("the sub-page is closed", shown("Fewer notes"))
    }

    @Test
    fun `settings sub-page moves its top bar with the page`() {
        launch()
        compose.onAllNodesWithText("Settings")[0].performClick()
        idle()
        compose.onNode(hasText("Balanced") and hasClickAction()).performScrollTo().performClick()
        idle()
        val backButton = hasContentDescription("Back") and hasClickAction()
        val rest = compose.onNode(backButton).fetchSemanticsNode().boundsInRoot
        startGesture()
        progress(0.5f)
        val moved = compose.onNode(backButton, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the back button moves with the page: $rest -> $moved", moved.left > rest.left + 1f)
        cancel()
        assertEquals(rest, compose.onNode(backButton).fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun `onboarding step goes back to the previous step under the finger`() {
        launch(app = TestApp(nights = 0), onboardingDone = false)
        assertFalse("first step: nothing to go back to", back.hasEnabledCallbacks())
        compose.onAllNodesWithText("Get started")[0].performClick()
        idle()
        assertFalse(shown("Get started"))
        startGesture()
        progress(0.5f)
        assertUnderFinger("onboarding", OnboardingStep.HowItWorks, OnboardingStep.Welcome, 0.5f)
        cancel()
        assertFalse(shown("Get started"))
        startGesture()
        progress(0.5f)
        commit()
        assertTrue("back on the first step", shown("Get started"))
    }

    @Test
    @Config(qualifiers = "en-rUS-w1000dp-h800dp-land-420dpi")
    fun `two panes - the detail pane's own stack goes back, not the window`() {
        launch(start = expanded)
        compose.runOnIdle { nav.navigate(SettingsRoute(SettingsPage.Licenses.name)) }
        idle()
        assertTrue("Licenses open in the pane", shown(LICENSE))
        startGesture()
        progress(0.5f)
        assertTrue("the pane's stack moves", frames.any { it.host == "settings-detail" && it.gesture })
        assertTrue("the window does not", frames.none { it.host == "pages" && it.gesture })
        cancel()
        startGesture()
        progress(0.5f)
        commit()
        assertTrue("still in Settings", route().contains("SettingsRoute"))
        assertFalse("Licenses closed: About in the pane", shown(LICENSE))
    }

    @Test
    fun `a window change during the gesture drops it`() {
        launch()
        openNight()
        startGesture()
        progress(0.5f)
        compose.runOnIdle { layout.value = resolveLayout(LayoutInputs(500f, 915f)) }
        idle()
        frames.clear()
        compose.mainClock.advanceTimeByFrame()
        assertTrue("nothing is moving", frames.isEmpty())
        // The rest of the dropped gesture does nothing.
        commit()
        assertTrue(route(), route().contains("NightRoute"))
        // The next one works.
        startGesture()
        progress(0.5f)
        commit()
        assertTrue(route(), route().contains("HomeRoute"))
    }

    @Test
    fun `reduced motion - no preview, Back goes back at once`() {
        launch(reducedMotion = true)
        openNight()
        startGesture()
        progress(0.5f)
        assertTrue("no gesture frame under reduced motion", frames.none { it.gesture })
        commit()
        assertTrue(route(), route().contains("HomeRoute"))
    }

    @Test
    fun `keyboard Back without a gesture goes back one level`() {
        launch()
        openNight()
        commit()
        assertTrue(route(), route().contains("HomeRoute"))
        assertFalse(back.hasEnabledCallbacks())
    }

    @Test
    fun `a back gesture during a push reverses it continuously and never draws the old leave`() {
        launch()
        val home = nav.currentBackStackEntry!!.id
        for (delay in longArrayOf(32, 48, 160, 304, 496)) {
            frames.clear()
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { nav.navigate(NightRoute(SampleData.LAST_WAKE.minusDays(3).toString(), TopLevel.Home.name)) }
            compose.mainClock.advanceTimeBy(delay)
            val night = nav.currentBackStackEntry!!.id
            val before = frames.lastOrNull { it.host == "pages" }
            back.dispatchOnBackStarted(BackEventCompat(0f, TOUCH_Y, 0f, BackEventCompat.EDGE_LEFT))
            back.dispatchOnBackProgressed(BackEventCompat(0f, TOUCH_Y, 0f, BackEventCompat.EDGE_LEFT))
            compose.mainClock.advanceTimeByFrame()
            val after = frames.last { it.host == "pages" }
            assertEquals("$delay ms: the gesture holds the Night page", night, after.from)
            if (before != null && before.to == night) {
                // The same pose in the frame the gesture takes over (R1): the page does not jump.
                assertEquals("$delay ms: continuous", before.toPose.tx, after.fromPose.tx, abs(before.p - after.p) * after.width + PX)
            }
            back.onBackPressed()
            compose.mainClock.autoAdvance = true
            idle()
            assertEquals("$delay ms: back on Home", home, nav.currentBackStackEntry!!.id)
            val leave = frames.filter { it.host == "pages" && it.from == night }
            assertTrue("$delay ms: the leaving page only ever moves towards the end edge", leave.all { it.fromPose.tx >= -PX })
            frames.clear()
            compose.mainClock.advanceTimeByFrame()
            assertTrue("$delay ms: nothing is drawn after the settle", frames.none { it.host == "pages" })
        }
    }

    private companion object {
        const val PX = 0.75f
        const val LICENSE = "Jetpack Compose and Material 3"
        const val TOUCH_Y = 900f
        const val WIDTH_PX = 1080f
    }
}
