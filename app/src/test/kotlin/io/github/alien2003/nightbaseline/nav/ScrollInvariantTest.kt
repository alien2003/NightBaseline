package io.github.alien2003.nightbaseline.nav

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.alien2003.nightbaseline.testing.TestApp
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.interaction.NbMotionProbe
import io.github.alien2003.nightbaseline.ui.interaction.NbScrollContainer
import io.github.alien2003.nightbaseline.ui.layout.LayoutInputs
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import io.github.alien2003.nightbaseline.ui.material.EffectsDecision
import io.github.alien2003.nightbaseline.ui.sample.SampleData
import io.github.alien2003.nightbaseline.ui.theme.NightBaselineTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/**
 * Scrolling is one behaviour everywhere (ADR 0011 I4): on every screen, the main framework scroll container (found
 * through [NbScrollContainer], so a new screen is covered without being listed) answers the same injected swipe with
 * the same distance and a fling; and a flick that lands within 50 or 150 ms of an edge bounce still scrolls and
 * flings (report d: before the framework it scrolled 0 px and its fling was dropped).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w412dp-h915dp-port-420dpi")
class ScrollInvariantTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var nav: NavHostController
    private var controller: ActivityController<ComponentActivity>? = null
    private val flings = CopyOnWriteArrayList<Float>()

    @Before
    fun setUp() {
        NbMotionProbe.flings = { _, v -> flings += v }
    }

    @After
    fun tearDown() {
        NbMotionProbe.flings = null
        controller?.pause()?.stop()?.destroy()
    }

    private fun launch() {
        controller?.pause()?.stop()?.destroy()
        idle()
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller!!.get().setContent {
            nav = rememberNavController()
            CompositionLocalProvider(LocalViewModelFactory provides TestApp().factory) {
                NightBaselineTheme(darkTheme = true, effects = EffectsDecision.ForcedSolid) {
                    AppNavHost(
                        layout = resolveLayout(LayoutInputs(412f, 915f)),
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

    private val screens: List<Pair<String, () -> Unit>> =
        listOf(
            "Home" to {},
            "History" to { compose.onAllNodesWithText("History")[0].performClick() },
            "Settings" to { compose.onAllNodesWithText("Settings")[0].performClick() },
            "Night" to { compose.runOnIdle { nav.navigate(NightRoute(SampleData.LAST_WAKE.minusDays(3).toString(), TopLevel.Home.name)) } },
            "Metric" to { compose.runOnIdle { nav.navigate(MetricRoute("respiratory_rate")) } },
        )

    private val container = SemanticsMatcher.keyIsDefined(NbScrollContainer)

    /** The largest framework scroll container on screen. */
    private fun main(): SemanticsNode =
        compose
            .onAllNodes(container)
            .fetchSemanticsNodes()
            .maxBy { it.boundsInRoot.height * it.boundsInRoot.width }

    private fun offset(): Float = main().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.value?.invoke() ?: 0f

    private fun max(): Float = main().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.maxValue?.invoke() ?: 0f

    /** A flick of [dy] px (negative = finger up) over [ms] in the middle of the main container. */
    private fun flick(
        dy: Float,
        ms: Long,
    ) {
        val bounds = main().boundsInRoot
        val x = bounds.center.x
        val y0 = bounds.center.y - dy / 2
        compose.onAllNodes(container).fetchSemanticsNodes()
        compose.onRoot().performTouchInput { swipe(androidx.compose.ui.geometry.Offset(x, y0), androidx.compose.ui.geometry.Offset(x, y0 + dy), ms) }
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onRoot() = onNode(SemanticsMatcher("root") { it.parent == null })

    @Test
    fun `I4 - the same flick scrolls the same and flings, at rest and right after an edge bounce, on every screen`() {
        val report = StringBuilder()
        var rest: Float? = null
        var afterBounce: Float? = null
        for ((name, open) in screens) {
            launch()
            open()
            idle()
            assertTrue("$name: scrolls", max() > 0f)
            // At rest, from the top.
            flings.clear()
            val start = offset()
            flick(-600f, 150)
            idle()
            val atRest = offset() - start
            assertTrue("$name: the flick flings", flings.isNotEmpty())
            if (rest == null) rest = atRest
            // The same distance on every screen that can take it (the extent limits the short ones). A lazy list
            // reports an estimated offset (by items), so only exact columns are compared in px.
            val exact = main().config.getOrNull(NbScrollContainer) != "list"
            if (exact && max() - start > atRest + 1f) assertTrue("$name: $atRest vs $rest", abs(atRest - rest!!) <= 1f)
            for (delay in longArrayOf(50, 150)) {
                // To the end with a bounce, then a flick back into the content right after it.
                repeat(4) {
                    flick(-900f, 80)
                    idle()
                }
                compose.mainClock.autoAdvance = false
                flick(-900f, 80)
                compose.mainClock.advanceTimeBy(delay)
                flings.clear()
                val before = offset()
                flick(300f, 100)
                compose.mainClock.autoAdvance = true
                idle()
                val back = before - offset()
                report.append("$name: at rest %.0f px; flick %d ms after the bounce: %.0f px, fling %s\n".format(atRest, delay, back, flings.isNotEmpty()))
                assertTrue("$name $delay ms: the flick scrolls ($back px)", back > if (exact) SLOP_PX else 0f)
                assertTrue("$name $delay ms: the fling is not dropped", flings.isNotEmpty())
                if (exact) {
                    // And the same distance on every screen and at every delay: the bounce takes nothing away.
                    if (afterBounce == null) afterBounce = back
                    assertTrue("$name $delay ms: $back vs $afterBounce", abs(back - afterBounce!!) <= 1f)
                }
            }
        }
        println("I4:\n$report")
    }

    private companion object {
        /** A flick of 300 px must move the content by more than the touch slop. */
        const val SLOP_PX = 42f
    }
}
