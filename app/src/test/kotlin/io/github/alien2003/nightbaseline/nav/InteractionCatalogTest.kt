package io.github.alien2003.nightbaseline.nav

import android.app.Application
import android.os.Looper
import android.view.KeyEvent
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import io.github.alien2003.nightbaseline.ui.material.EffectsDecision
import io.github.alien2003.nightbaseline.ui.sample.SampleData
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
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The trigger catalog of ADR 0011 (I1, I7, I8) through the real app on Robolectric: every user trigger of the
 * inventory's table 2 that changes what is shown runs a change of its kind on its host, on the one Nav spring object,
 * with at least three frames in between and a settled end; triggers of the same intent (bar, link, keyboard; round
 * Back button, Esc, 3-button Back) draw the identical sequence of p, frame by frame; and under reduced motion every
 * one of them snaps (no frame strictly between the two states).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w412dp-h915dp-port-420dpi")
class InteractionCatalogTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var nav: NavHostController
    private lateinit var activity: ComponentActivity
    private var controller: ActivityController<ComponentActivity>? = null
    private val frames = CopyOnWriteArrayList<NbFrame>()
    private val shortcuts = KeyShortcuts()

    @Before
    fun setUp() {
        NbMotionProbe.frames = { frames += it }
    }

    @After
    fun tearDown() {
        NbMotionProbe.frames = null
        controller?.pause()?.stop()?.destroy()
    }

    private fun launch(reduced: Boolean? = null) {
        // One app at a time: the test rule finds nodes in every window.
        controller?.pause()?.stop()?.destroy()
        idle()
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity = controller!!.get()
        activity.setContent {
            nav = rememberNavController()
            CompositionLocalProvider(LocalViewModelFactory provides TestApp().factory, LocalKeyShortcuts provides shortcuts) {
                NightBaselineTheme(darkTheme = true, effects = EffectsDecision.ForcedSolid, reducedMotion = reduced) {
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

    /** One trigger: its name, the host and kind it must run, and the action. */
    private class Trigger(
        val name: String,
        val host: String,
        val kind: NbKind,
        val setup: InteractionCatalogTest.() -> Unit = {},
        val action: InteractionCatalogTest.() -> Unit,
    )

    private fun key(
        code: Int,
        meta: Int = 0,
    ) {
        val superDispatch: (KeyEvent) -> Boolean = { activity.window.superDispatchKeyEvent(it) }
        shortcuts.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, meta), superDispatch)
        shortcuts.dispatch(KeyEvent(0, 0, KeyEvent.ACTION_UP, code, 0, meta), superDispatch)
    }

    private fun tap(node: SemanticsNodeInteraction) {
        // Scrolled into view where it sits in a scrolling column; top-bar buttons do not scroll.
        runCatching { node.performScrollTo() }
        node.performClick()
    }

    private fun tapText(text: String) = tap(compose.onAllNodes(hasText(text) and hasClickAction()).onFirst())

    private fun tapDescription(text: String) = tap(compose.onAllNodes(hasContentDescription(text, substring = true) and hasClickAction()).onFirst())

    private fun openNight() {
        compose.runOnIdle { nav.navigate(NightRoute(SampleData.LAST_WAKE.minusDays(3).toString(), TopLevel.Home.name)) }
        idle()
    }

    private fun gestureBack() {
        val back = activity.onBackPressedDispatcher
        back.dispatchOnBackStarted(BackEventCompat(0f, 900f, 0f, BackEventCompat.EDGE_LEFT))
        back.dispatchOnBackProgressed(BackEventCompat(300f, 900f, 0.3f, BackEventCompat.EDGE_LEFT))
        idle()
        back.onBackPressed()
    }

    private val catalog =
        listOf(
            Trigger("bar History", "sections", NbKind.Tab) { compose.onAllNodesWithText("History")[0].performClick() },
            Trigger("See all", "sections", NbKind.Tab) { tapText("See all") },
            Trigger("Ctrl+2", "sections", NbKind.Tab) { key(KeyEvent.KEYCODE_2, KeyEvent.META_CTRL_ON) },
            Trigger("bar Settings", "sections", NbKind.Tab) { compose.onAllNodesWithText("Settings")[0].performClick() },
            Trigger("back gesture to Home", "sections", NbKind.Tab, setup = {
                compose.onAllNodesWithText("History")[0].performClick()
            }) { gestureBack() },
            Trigger("metric tile", "pages", NbKind.Page) { tapDescription("Respiratory rate") },
            Trigger("round Back", "pages", NbKind.Page, setup = { openNight() }) { tapDescription("Back") },
            Trigger("Esc", "pages", NbKind.Page, setup = { openNight() }) { key(KeyEvent.KEYCODE_ESCAPE) },
            Trigger("3-button Back", "pages", NbKind.Page, setup = { openNight() }) { activity.onBackPressedDispatcher.onBackPressed() },
            Trigger("back gesture on a page", "pages", NbKind.Page, setup = { openNight() }) { gestureBack() },
            Trigger("previous night", "night", NbKind.Pager, setup = { openNight() }) { tapDescription("Previous night") },
            Trigger("next night", "night", NbKind.Pager, setup = { openNight() }) { tapDescription("Next night") },
            Trigger("settings sub-page", "settings", NbKind.Page, setup = {
                compose.onAllNodesWithText("Settings")[0].performClick()
            }) { tapText("Balanced") },
            Trigger("What this means sheet", "modal", NbKind.Sheet) { tapText("What this means") },
            Trigger("sheet Back", "modal", NbKind.Sheet, setup = { tapText("What this means") }) {
                activity.onBackPressedDispatcher.onBackPressed()
            },
        )

    /** Runs [trigger] after its setup and returns the frames of its host. */
    private fun run(trigger: Trigger): List<NbFrame> {
        trigger.setup(this)
        idle()
        frames.clear()
        trigger.action(this)
        idle()
        return frames.filter { it.host == trigger.host }
    }

    @Test
    fun `I1 and I7 - every trigger runs its kind on the Nav spring and settles`() {
        val report = StringBuilder()
        for (trigger in catalog) {
            launch()
            val seen = run(trigger)
            val between = seen.filter { it.p > 0f && it.p < 1f }
            report.append("${trigger.name}: ${trigger.host}/${trigger.kind} ${between.size} frames\n")
            assertTrue("${trigger.name}: frames of ${trigger.host}", seen.isNotEmpty())
            assertTrue("${trigger.name}: one kind", seen.all { it.kind == trigger.kind })
            assertTrue("${trigger.name}: the one spring object", seen.all { it.spring === NbPhysics.Nav })
            assertTrue("${trigger.name}: at least 3 frames in between (${between.size})", between.size >= 3)
            // Settled: nothing of the change is drawn any more.
            frames.clear()
            compose.mainClock.advanceTimeByFrame()
            idle()
            assertTrue("${trigger.name}: settled", frames.none { it.host == trigger.host && it.kind == trigger.kind && it.p < 1f && it.p > 0f })
        }
        println("I7 catalog:\n$report")
    }

    /** The p sequence a trigger draws, frame by frame (distinct consecutive values). */
    private fun sequence(trigger: Trigger): List<Float> {
        launch()
        return run(trigger).filter { !it.gesture }.map { it.p }.fold(mutableListOf()) { acc, p ->
            if (acc.lastOrNull() != p) acc += p
            acc
        }
    }

    @Test
    fun `I1 - the same intent from different triggers is the same motion, frame by frame`() {
        val byName = catalog.associateBy { it.name }
        val tab = listOf("bar History", "See all", "Ctrl+2").map { sequence(byName.getValue(it)) }
        tab.drop(1).forEach { assertEquals("a section change is one motion", tab[0], it) }
        val pop = listOf("round Back", "Esc", "3-button Back").map { sequence(byName.getValue(it)) }
        pop.drop(1).forEach { assertEquals("a pop is one motion", pop[0], it) }
        println("I1: section change ${tab[0].size} frames, pop ${pop[0].size} frames, identical across triggers")
    }

    @Test
    fun `I8 - under reduced motion every trigger snaps`() {
        for (trigger in catalog) {
            launch(reduced = true)
            val seen = run(trigger)
            assertTrue("${trigger.name}: no frame in between", seen.none { it.p > 0f && it.p < 1f })
        }
    }
}
