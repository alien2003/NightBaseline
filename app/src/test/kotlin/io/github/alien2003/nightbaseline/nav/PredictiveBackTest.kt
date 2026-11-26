package io.github.alien2003.nightbaseline.nav

import android.app.Application
import android.os.Looper
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
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
import io.github.alien2003.nightbaseline.ui.layout.LayoutSpec
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import io.github.alien2003.nightbaseline.ui.material.EffectsDecision
import io.github.alien2003.nightbaseline.ui.motion.NB_BACK_CORNER_DP
import io.github.alien2003.nightbaseline.ui.motion.NB_BACK_SCALE
import io.github.alien2003.nightbaseline.ui.motion.NB_BACK_TRAVEL
import io.github.alien2003.nightbaseline.ui.sample.SampleData
import io.github.alien2003.nightbaseline.ui.settings.DetailPaneBackKey
import io.github.alien2003.nightbaseline.ui.settings.SettingsPage
import io.github.alien2003.nightbaseline.ui.shell.BackPose
import io.github.alien2003.nightbaseline.ui.shell.BackPreviewProbe
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Predictive back for screens (ADR 0009, `coloros-conformance.md` P3), driven through the activity's
 * OnBackPressedDispatcher with BackEventCompat start / progress / cancel / commit as the system sends them: the page
 * that a gesture would leave scales, rounds its corners and follows the finger in the draw layer (read here through
 * [BackPreviewProbe]), the page underneath is composed in its covered pose, a cancel restores the rest pose exactly
 * and keeps the destination, a commit goes back one level, and at the root nothing takes Back (the system's
 * back-to-home animation plays).
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
    private val poses = ConcurrentHashMap<Any, BackPose>()
    private val unders = ConcurrentHashMap<Any, Float>()
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    @Before
    fun setUp() {
        BackPreviewProbe.sink = { key, pose -> poses[key] = pose }
        BackPreviewProbe.underSink = { key, under -> unders[key] = under }
        PerfProbe.sink = { name -> counts.getOrPut(name) { AtomicInteger() }.incrementAndGet() }
    }

    @After
    fun tearDown() {
        BackPreviewProbe.sink = null
        BackPreviewProbe.underSink = null
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

    /** The pose of [key] at [progress] with a finger moving right (a swipe from the left edge). */
    private fun assertPose(
        key: Any,
        progress: Float,
        direction: Int = 1,
    ) {
        assertNotNull("no pose for $key", poses[key])
        val pose = poses.getValue(key)
        assertEquals("scale of $key", 1f - (1f - NB_BACK_SCALE) * progress, pose.scale, EPS)
        assertEquals("travel of $key", direction * NB_BACK_TRAVEL * progress, pose.travel, EPS)
        assertEquals("corner of $key", NB_BACK_CORNER_DP * progress, pose.cornerDp, EPS)
    }

    private fun assertRest(key: Any) {
        assertEquals("$key back at rest", BackPose.Rest, poses[key])
    }

    private fun openNight(): String {
        compose.runOnIdle { nav.navigate(NightRoute(SampleData.LAST_WAKE.minusDays(3).toString(), TopLevel.Home.name)) }
        idle()
        assertTrue(route(), route().contains("NightRoute"))
        return nav.currentBackStackEntry!!.id
    }

    @Test
    fun `detail route previews the pop, cancel restores it exactly, commit pops`() {
        launch()
        val home = nav.currentBackStackEntry!!.id
        val night = openNight()
        assertTrue("Back is taken on a pushed page", back.hasEnabledCallbacks())
        poses.clear()
        startGesture()
        progress(0.25f)
        val screens = count("NightDetailScreen") + count("HomeScreen")
        progress(0.5f)
        progress(0.75f)
        assertPose(night, 0.75f)
        assertEquals("progress frames recompose no page content", screens, count("NightDetailScreen") + count("HomeScreen"))
        // The page underneath is composed for the preview (in its covered pose, from the pop transition).
        assertRest(home)
        assertTrue(route(), route().contains("NightRoute"))
        cancel()
        assertRest(night)
        assertTrue(route(), route().contains("NightRoute"))
        assertTrue(back.hasEnabledCallbacks())
        // Again, from the right edge: the page follows the finger to the left; this time it is committed.
        startGesture(BackEventCompat.EDGE_RIGHT)
        progress(0.4f, BackEventCompat.EDGE_RIGHT)
        assertPose(night, 0.4f, direction = -1)
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
        assertPose(metric, 0.5f)
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
        assertRest(night)
        startGesture()
        progress(0.6f)
        assertPose(night, 0.6f)
        commit()
        assertTrue(route(), route().contains("HomeRoute"))
    }

    @Test
    fun `tab to Home previews the section over Home in its covered pose`() {
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
        assertPose(TopLevel.History, 0.6f)
        assertEquals("Home waits in the covered pose", 1f, unders[TopLevel.Home])
        assertEquals("no recomposition per progress frame", home, count("HomeScreen"))
        cancel()
        assertRest(TopLevel.History)
        assertTrue(route(), route().contains("HistoryRoute"))
        startGesture()
        progress(0.5f)
        commit()
        assertTrue(route(), route().contains("HomeRoute"))
        assertRest(TopLevel.History)
        assertEquals("Home at rest after the commit", 0f, unders[TopLevel.Home])
        assertFalse(back.hasEnabledCallbacks())
    }

    @Test
    fun `settings sub-page previews over the list and goes back to it`() {
        launch()
        compose.onAllNodesWithText("Settings")[0].performClick()
        idle()
        compose.onNode(hasText("Balanced") and hasClickAction()).performScrollTo().performClick()
        idle()
        assertTrue(shown("Fewer notes"))
        poses.clear()
        startGesture()
        progress(0.5f)
        assertPose(SettingsPage.Sensitivity, 0.5f)
        // The list is composed under the sub-page for the preview.
        assertRest(SettingsPage.Main)
        assertTrue("the section itself does not preview", poses[TopLevel.Settings]?.atRest != false)
        cancel()
        assertRest(SettingsPage.Sensitivity)
        assertTrue(shown("Fewer notes"))
        startGesture()
        progress(0.5f)
        commit()
        assertEquals(TopLevel.Settings, nav.currentBackStackEntry?.topLevelTab())
        assertFalse("the sub-page is closed", shown("Fewer notes"))
        assertRest(SettingsPage.Main)
    }

    @Test
    fun `onboarding step goes back to the previous step with the preview`() {
        launch(app = TestApp(nights = 0), onboardingDone = false)
        assertFalse("first step: nothing to go back to", back.hasEnabledCallbacks())
        compose.onAllNodesWithText("Get started")[0].performClick()
        idle()
        assertFalse(shown("Get started"))
        startGesture()
        progress(0.5f)
        assertPose(OnboardingBackKey, 0.5f)
        cancel()
        assertRest(OnboardingBackKey)
        assertFalse(shown("Get started"))
        startGesture()
        progress(0.5f)
        commit()
        assertTrue("back on the first step", shown("Get started"))
        assertRest(OnboardingBackKey)
    }

    @Test
    @Config(qualifiers = "en-rUS-w1000dp-h800dp-land-420dpi")
    fun `two panes - only the detail pane previews`() {
        launch(start = expanded)
        compose.runOnIdle { nav.navigate(SettingsRoute(SettingsPage.Licenses.name)) }
        idle()
        val entry = nav.currentBackStackEntry!!.id
        assertTrue("Licenses open in the pane", shown(LICENSE))
        startGesture()
        progress(0.5f)
        assertPose(DetailPaneBackKey, 0.5f)
        assertTrue("the window does not preview", poses[entry]?.atRest != false)
        cancel()
        assertRest(DetailPaneBackKey)
        startGesture()
        progress(0.5f)
        commit()
        assertTrue("still in Settings", route().contains("SettingsRoute"))
        assertFalse("Licenses closed: About in the pane", shown(LICENSE))
        assertRest(DetailPaneBackKey)
    }

    @Test
    fun `a window change during the gesture drops the preview`() {
        launch()
        val night = openNight()
        startGesture()
        progress(0.5f)
        assertPose(night, 0.5f)
        compose.runOnIdle { layout.value = resolveLayout(LayoutInputs(500f, 915f)) }
        idle()
        assertRest(night)
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
        val night = openNight()
        startGesture()
        progress(0.5f)
        assertTrue("no pose under reduced motion", poses[night]?.atRest != false)
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

    private companion object {
        const val EPS = 1e-4f
        const val LICENSE = "Jetpack Compose and Material 3"
        const val TOUCH_Y = 900f
        const val WIDTH_PX = 1080f
    }
}
