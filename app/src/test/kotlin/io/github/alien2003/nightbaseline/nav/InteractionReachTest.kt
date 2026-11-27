package io.github.alien2003.nightbaseline.nav

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.alien2003.nightbaseline.PermissionsRationaleActivity
import io.github.alien2003.nightbaseline.testing.TestApp
import io.github.alien2003.nightbaseline.ui.interaction.MODAL_PANEL_TAG
import io.github.alien2003.nightbaseline.ui.interaction.NbMotionProbe
import io.github.alien2003.nightbaseline.ui.interaction.NbPressable
import io.github.alien2003.nightbaseline.ui.interaction.NbScrollContainer
import io.github.alien2003.nightbaseline.ui.layout.FoldInfo
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/**
 * The reach of the interaction registries (ADR 0011, review V10): the sweeps enumerate screens from [AppScreens] and
 * containers from [NbScrollContainer], but only in each screen's first state. This test goes one tap deeper, at the
 * three window sizes of the sweeps (phone, fold closed, fold open in two panes): every pressable of every screen is
 * tapped, and every state it opens (a sheet, a dialog, a view toggle, a detail pane) is enumerated too.
 *
 * - Completeness: every scroll container id written in the app's sources is reached by some screen or state (a
 *   container that exists in the sources but in no reachable state fails the test until it is reachable or removed);
 *   the permission rationale (its own activity) counts as a screen of its own.
 * - Modals: every scroll container inside a sheet or a dialog flings for a flick at the panel's side margin, as in
 *   its middle (V2-7: the explainer sheet did not scroll from its side padding).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class InteractionReachTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var nav: NavHostController
    private var controller: ActivityController<ComponentActivity>? = null
    private val flings = CopyOnWriteArrayList<Pair<String, Float>>()
    private val report = StringBuilder()

    private class Size(
        val name: String,
        val qualifiers: String,
        val inputs: LayoutInputs,
    )

    private val sizes =
        listOf(
            Size("phone", "en-rUS-w412dp-h915dp-port-420dpi", LayoutInputs(412f, 915f)),
            Size("fold closed", "en-rUS-w380dp-h872dp-port-480dpi", LayoutInputs(380f, 872f)),
            Size(
                "fold open",
                "en-rUS-w749dp-h826dp-port-480dpi",
                LayoutInputs(749f, 826f, verticalFold = FoldInfo(374.5f, separating = true)),
            ),
        )

    /** Every other layout mode (`LayoutMode`): their screens' own containers, in each screen's first state. */
    private val otherModes =
        listOf(
            Size("glance", "en-rUS-w300dp-h460dp-port-420dpi", LayoutInputs(300f, 460f)),
            Size("compact short", "en-rUS-w560dp-h400dp-land-420dpi", LayoutInputs(560f, 400f)),
            Size("wide short", "en-rUS-w915dp-h412dp-land-420dpi", LayoutInputs(915f, 412f)),
            Size(
                "tabletop",
                "en-rUS-w749dp-h826dp-port-480dpi",
                LayoutInputs(749f, 826f, horizontalFold = FoldInfo(413f, separating = true)),
            ),
            Size("medium", "en-rUS-w700dp-h900dp-port-420dpi", LayoutInputs(700f, 900f)),
            Size("expanded", "en-rUS-w1000dp-h800dp-land-420dpi", LayoutInputs(1000f, 800f)),
            Size("large", "en-rUS-w1300dp-h900dp-land-420dpi", LayoutInputs(1300f, 900f)),
        )

    @Before
    fun setUp() {
        NbMotionProbe.flings = { id, v -> flings += id to v }
    }

    @After
    fun tearDown() {
        NbMotionProbe.flings = null
        controller?.pause()?.stop()?.destroy()
        println("InteractionReachTest:\n$report")
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private fun launch(
        size: Size,
        nights: Int?,
    ) {
        controller?.pause()?.stop()?.destroy()
        idle()
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val app = if (nights != null) TestApp(nights = nights) else TestApp()
        controller!!.get().setContent {
            nav = rememberNavController()
            CompositionLocalProvider(LocalViewModelFactory provides app.factory) {
                NightBaselineTheme(darkTheme = true, effects = EffectsDecision.ForcedSolid) {
                    AppNavHost(
                        layout = resolveLayout(size.inputs),
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

    private fun open(
        size: Size,
        screen: AppScreen,
    ) {
        launch(size, screen.nights)
        compose.runOnIdle { screen.open(nav, SampleData.LAST_WAKE.minusDays(3), InteractionSweep.METRIC) }
        idle()
    }

    private fun containers(): List<SemanticsNode> =
        compose
            .onAllNodes(SemanticsMatcher.keyIsDefined(NbScrollContainer), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { it.layoutInfo.isPlaced }

    private fun pressables(): List<SemanticsNode> =
        compose
            .onAllNodes(SemanticsMatcher.keyIsDefined(NbPressable), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { it.layoutInfo.isPlaced }

    private fun panels(): List<Rect> =
        compose
            .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, MODAL_PANEL_TAG), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }

    /** What is shown: the stack depth, the top entry, the open panels and the scroll containers on screen. */
    private data class Look(
        val depth: Int,
        val entry: String?,
        val panels: Int,
        val containers: Set<String>,
    )

    private fun look(): Look =
        Look(
            nav.currentBackStack.value.size,
            nav.currentBackStackEntry?.id,
            panels().size,
            containers().map { it.config[NbScrollContainer] }.toSet(),
        )

    /** Back to [base] with Back presses (a sheet, a toggle that Back undoes); false when Back does not get there. */
    @Suppress("ReturnCount") // There, no Back left, after the presses.
    private fun restore(base: Look): Boolean {
        val back = controller!!.get().onBackPressedDispatcher
        repeat(RESTORE_BACKS) {
            if (look() == base) return true
            if (!back.hasEnabledCallbacks()) return false
            back.onBackPressed()
            idle()
        }
        return look() == base
    }

    /** True when [this] is inside a modal panel (an ancestor carries the panel tag), not a page behind it. */
    private fun SemanticsNode.inPanel(): Boolean {
        var n: SemanticsNode? = parent
        while (n != null) {
            if (n.config.getOrNull(SemanticsProperties.TestTag) == MODAL_PANEL_TAG) return true
            n = n.parent
        }
        return false
    }

    private fun SemanticsNode.maxScroll(): Float = config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.maxValue?.invoke() ?: 0f

    /** The ids of the scroll containers written in the app's sources (each call passes a literal id). */
    private fun sourceIds(): Set<String> {
        val call = Regex("""(nbVerticalScroll|nbHorizontalScroll|NbLazyColumn)\(""")
        val id = Regex("""id = "([^"]+)"""")
        return File("src/main/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.path.contains("/ui/interaction/") }
            .flatMap { f ->
                val lines = f.readLines()
                lines.indices.filter { call.containsMatchIn(lines[it]) }.mapNotNull { i ->
                    (i until minOf(i + ID_LOOKAHEAD, lines.size)).firstNotNullOfOrNull { id.find(lines[it])?.groupValues?.get(1) }
                }
            }.toSet()
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth", "LoopWithTooManyJumpStatements")
    @Test
    fun `every container of the sources is reached one tap deep at three sizes, and every modal container flings from its margin`() {
        val reached = HashMap<String, String>()
        val flicked = HashSet<String>()
        var modalContainers = 0
        for (size in sizes) {
            RuntimeEnvironment.setQualifiers(size.qualifiers)
            for (screen in AppScreens.all) {
                open(size, screen)
                containers().forEach { reached.putIfAbsent(it.config[NbScrollContainer], "${size.name}/${screen.name}") }
                val base = look()
                val count = pressables().size
                for (i in 0 until count) {
                    if (look() != base && !restore(base)) open(size, screen)
                    val node = pressables().getOrNull(i) ?: break
                    runCatching {
                        compose
                            .onAllNodes(SemanticsMatcher("node ${node.id}") { it.id == node.id }, useUnmergedTree = true)[0]
                            .performSemanticsAction(SemanticsActions.OnClick)
                    }
                    idle()
                    val after = look()
                    // Another page of the stack is another registered screen: not a state of this one.
                    if (after == base || after.depth != base.depth) continue
                    val now = containers()
                    val ids = now.map { it.config[NbScrollContainer] }.toSet()
                    val label =
                        node.config.getOrNull(SemanticsProperties.ContentDescription)?.firstOrNull()
                            ?: node.config
                                .getOrNull(SemanticsProperties.Text)
                                ?.firstOrNull()
                                ?.text ?: "#$i"
                    ids.forEach { reached.putIfAbsent(it, "${size.name}/${screen.name} -> $label") }
                    // A sheet or dialog: each of its scroll containers flings from the panel's side margin too.
                    for (panel in panels()) {
                        for (c in now.filter { it.inPanel() && panel.contains(it.boundsInRoot.center) && it.maxScroll() > 0f }) {
                            val cid = c.config[NbScrollContainer]
                            if (!flicked.add("${size.name}/$cid")) continue
                            val b = c.boundsInRoot.intersect(panel)
                            for ((where, x) in listOf("middle" to b.center.x, "margin" to panel.left + MARGIN_PX)) {
                                flings.clear()
                                val y = b.top + b.height * 0.7f
                                compose.onAllNodes(SemanticsMatcher("root") { it.parent == null })[0].performTouchInput {
                                    swipe(Offset(x, y), Offset(x, y - FLICK_PX), FLICK_MS)
                                }
                                idle()
                                val v = flings.filter { it.first == cid }.maxOfOrNull { abs(it.second) } ?: 0f
                                report.append("${size.name}/${screen.name} -> $label: $cid $where v=%.0f\n".format(v))
                                assertTrue("${size.name}/${screen.name} -> $label: $cid flings from its $where ($v px/s)", v >= MIN_FLING)
                                modalContainers++
                            }
                        }
                    }
                }
            }
        }
        for (size in otherModes) {
            RuntimeEnvironment.setQualifiers(size.qualifiers)
            for (screen in AppScreens.all) {
                open(size, screen)
                containers().forEach { reached.putIfAbsent(it.config[NbScrollContainer], "${size.name}/${screen.name}") }
            }
        }
        // The permission rationale is an activity of its own: a screen of the registry's reach as well.
        RuntimeEnvironment.setQualifiers(sizes.first().qualifiers)
        controller?.pause()?.stop()?.destroy()
        controller = null
        val rationale = Robolectric.buildActivity(PermissionsRationaleActivity::class.java).setup()
        idle()
        containers().forEach { reached.putIfAbsent(it.config[NbScrollContainer], "rationale") }
        rationale.pause().stop().destroy()
        val sources = sourceIds()
        report.append("reached ${reached.size} containers: ${reached.toSortedMap()}\nmodal container flicks: $modalContainers\n")
        val missing = sources - reached.keys - UNREACHED_BY_SAMPLE.keys
        assertTrue("scroll containers of the sources that no screen or state reaches: $missing", missing.isEmpty())
        val stale = UNREACHED_BY_SAMPLE.keys.filter { it !in sources || it in reached }
        assertTrue("listed as unreached but reached or gone (remove them): $stale", stale.isEmpty())
    }

    private companion object {
        const val ID_LOOKAHEAD = 6
        const val RESTORE_BACKS = 3

        /**
         * Containers of states the registries cannot open with the sample data, each with the reason (reviewed; the list may
         * only shrink). Every other container must be reached.
         */
        val UNREACHED_BY_SAMPLE =
            mapOf(
                "metric-8" to "the not-recorded sheet: opens only for a night whose source did not record an extra measurement",
                "night-2" to "Night in Book opened from Home: the registry opens nights from History, which fold into its pane",
                "night-3" to "Night in Book opened from Home (its end pane), as night-2",
            )
        const val MIN_FLING = 1000f
        const val MARGIN_PX = 6f
        const val FLICK_PX = 400f
        const val FLICK_MS = 100L
    }
}
