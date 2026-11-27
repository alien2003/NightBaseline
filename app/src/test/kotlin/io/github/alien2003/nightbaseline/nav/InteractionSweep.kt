package io.github.alien2003.nightbaseline.nav

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
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import io.github.alien2003.nightbaseline.testing.TestApp
import io.github.alien2003.nightbaseline.ui.interaction.NbFrame
import io.github.alien2003.nightbaseline.ui.interaction.NbKind
import io.github.alien2003.nightbaseline.ui.interaction.NbMotionProbe
import io.github.alien2003.nightbaseline.ui.interaction.NbPhysics
import io.github.alien2003.nightbaseline.ui.interaction.NbPressable
import io.github.alien2003.nightbaseline.ui.interaction.NbScrollContainer
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
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/**
 * The interaction invariants of ADR 0011 over the whole app, enumerated from the registries and never from a list
 * in the test: every screen of [AppScreens], every scroll container of it ([NbScrollContainer]) and every pressable
 * of it ([NbPressable]), at one window size per subclass (phone, fold closed, fold open in two panes).
 *
 * - Registration: every destination of the navigation graph is in [AppScreens]; container ids are unique per screen.
 * - Fling occurrence: the same flick flings (velocity above [MIN_FLING]) in every container that can scroll, at rest,
 *   50 and 150 ms after an edge bounce, and when it starts on a pressable (V2, report d).
 * - Fling distance: the same flick flings the same distance in every container with room for it (epsilon
 *   [TRAVEL_EPS] px), measured as what the content consumed (exact for lazy lists too).
 * - No bump: a fling that comes to rest inside the content draws no overscroll offset (V1).
 * - A swipe is never a tap: a horizontal swipe that starts on any pressable fires no click (V3).
 * - Same kind, same curve: every pressable that changes what is shown runs its host's kind on the Nav spring, and every
 *   trigger of one kind draws the identical p sequence, frame by frame (I1 over every trigger, review 7).
 */
abstract class InteractionSweep(
    private val inputs: LayoutInputs,
) {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var nav: NavHostController
    private var controller: ActivityController<ComponentActivity>? = null
    private val frames = CopyOnWriteArrayList<NbFrame>()
    private val flings = CopyOnWriteArrayList<Pair<String, Float>>()
    private val flingEnds = CopyOnWriteArrayList<Pair<String, Float>>()
    private val overscroll = CopyOnWriteArrayList<Float>()
    private val report = StringBuilder()

    /** The vertical overscroll translation now, px (signed: + the content is moved down). */
    @Volatile
    private var overscrollY = 0f

    @Before
    fun setUp() {
        NbMotionProbe.frames = { frames += it }
        NbMotionProbe.flings = { id, v -> flings += id to v }
        NbMotionProbe.flingEnds = { id, d -> flingEnds += id to d }
        NbMotionProbe.overscroll = { x, y -> overscroll += maxOf(abs(x), abs(y)) }
        NbMotionProbe.overscrollNow = { _, y -> overscrollY = y }
    }

    @After
    fun tearDown() {
        NbMotionProbe.frames = null
        NbMotionProbe.flings = null
        NbMotionProbe.flingEnds = null
        NbMotionProbe.overscroll = null
        NbMotionProbe.overscrollNow = null
        controller?.pause()?.stop()?.destroy()
        println("${javaClass.simpleName}:\n$report")
    }

    private fun launch(nights: Int? = null) {
        controller?.pause()?.stop()?.destroy()
        idle()
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val app = if (nights != null) TestApp(nights = nights) else TestApp()
        controller!!.get().setContent {
            nav = rememberNavController()
            CompositionLocalProvider(LocalViewModelFactory provides app.factory) {
                NightBaselineTheme(darkTheme = true, effects = EffectsDecision.ForcedSolid) {
                    AppNavHost(
                        layout = resolveLayout(inputs),
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

    private fun open(screen: AppScreen) {
        launch(screen.nights)
        compose.runOnIdle { screen.open(nav, SampleData.LAST_WAKE.minusDays(3), METRIC) }
        idle()
        frames.clear()
    }

    /** Back to the app's start the way a person goes (Back until nothing takes it), then [screen] again. */
    private fun reopen(screen: AppScreen) {
        val back = controller!!.get().onBackPressedDispatcher
        var guard = 0
        while (back.hasEnabledCallbacks() && guard++ < MAX_BACKS) {
            back.onBackPressed()
            idle()
        }
        if (back.hasEnabledCallbacks()) {
            open(screen)
            return
        }
        compose.runOnIdle { screen.open(nav, SampleData.LAST_WAKE.minusDays(3), METRIC) }
        idle()
        frames.clear()
    }

    private fun ComposeTestRule.onRoot() = onNode(SemanticsMatcher("root") { it.parent == null })

    private val window: Rect get() = compose.onRoot().fetchSemanticsNode().boundsInRoot

    // On screen only: the page below the top of a stack is composed and measured ahead (parked) but not placed.
    private fun containers(): List<SemanticsNode> =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(NbScrollContainer)).fetchSemanticsNodes().filter { it.layoutInfo.isPlaced }

    private fun SemanticsNode.id(): String = config[NbScrollContainer]

    private fun SemanticsNode.range(): Pair<Float, Float> {
        val r = config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) ?: return 0f to 0f
        return r.value() to r.maxValue()
    }

    private fun container(id: String): SemanticsNode? = containers().firstOrNull { it.id() == id }

    /** The visible part of [node] inside the window. */
    private fun visible(node: SemanticsNode): Rect = node.boundsInRoot.intersect(window)

    private fun flick(
        from: Offset,
        dy: Float,
        ms: Long,
    ) {
        compose.onRoot().performTouchInput { swipe(from, Offset(from.x, from.y + dy), ms) }
    }

    private fun swipeX(
        from: Offset,
        dx: Float,
    ) {
        compose.onRoot().performTouchInput { swipe(from, Offset(from.x + dx, from.y), SWIPE_MS) }
    }

    /** A point in the upper middle of [node]'s visible part (clear of a floating bar at the bottom). */
    private fun flickPoint(node: SemanticsNode): Offset {
        val v = visible(node)
        return Offset(v.center.x, v.top + v.height * 0.4f)
    }

    /** The platform's touch slop, px (a drag on resting content starts after it). */
    private val slop: Float get() =
        android.view.ViewConfiguration
            .get(controller!!.get())
            .scaledTouchSlop
            .toFloat()

    private fun maxFling(id: String): Float = flings.filter { it.first == id }.maxOfOrNull { abs(it.second) } ?: 0f

    @Test
    fun `every destination of the graph is a registered screen`() {
        launch()
        val graph = nav.graph
        val unregistered = graph.filter { d -> AppScreens.routes.none { d.hasRoute(it) } }.map { it.route }
        assertTrue("destinations not in AppScreens: $unregistered", unregistered.isEmpty())
        assertEquals("every registered route is a destination", AppScreens.routes.size, graph.count())
        // And the other direction (review): every registered route is opened by some screen of the registry, so the
        // sweeps below run over it.
        val opened = HashSet<Any>()
        for (screen in AppScreens.all) {
            launch(screen.nights)
            // Every destination the screen passes through counts: in two panes a night opens and is folded back into
            // History's detail pane right away (layouts.md 8.2).
            val listener =
                NavController.OnDestinationChangedListener { _, d, _ ->
                    AppScreens.routes.filter { d.hasRoute(it) }.forEach {
                        opened +=
                            it
                    }
                }
            compose.runOnIdle { nav.addOnDestinationChangedListener(listener) }
            compose.runOnIdle { screen.open(nav, SampleData.LAST_WAKE.minusDays(3), METRIC) }
            idle()
            compose.runOnIdle { nav.removeOnDestinationChangedListener(listener) }
        }
        assertEquals("routes no registered screen opens", AppScreens.routes, opened)
    }

    // One sweep: screens x containers x cases.
    @Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth", "LoopWithTooManyJumpStatements")
    @Test
    fun `scrolling - every container flings, the same distance, with no bump, also after a bounce and from a pressable`() {
        var reference: Float? = null
        var scrolling = 0
        for (screen in AppScreens.all) {
            open(screen)
            val ids = containers().map { it.id() }
            assertEquals("${screen.name}: container ids are unique ($ids)", ids.toSet().size, ids.size)
            for (id in ids) {
                open(screen)
                val node = container(id) ?: continue
                val (start, max) = node.range()
                val room = max - start
                if (max <= 0f || visible(node).height < MIN_HEIGHT_PX) continue
                scrolling++
                // At rest: a flick of 600 px over 150 ms.
                flings.clear()
                flingEnds.clear()
                overscroll.clear()
                flick(flickPoint(node), -FLICK_PX, FLICK_MS)
                idle()
                val v = maxFling(id)
                assertTrue("${screen.name}/$id: the flick flings ($v px/s)", v >= MIN_FLING)
                val travel = flingEnds.filter { it.first == id }.sumOf { it.second.toDouble() }.toFloat()
                if (room > FLICK_PX + travel + EDGE_MARGIN_PX) {
                    // Inside the content: the fling is conserved and nothing is thrown past an edge.
                    assertTrue("${screen.name}/$id: no bump away from an edge (${overscroll.maxOrNull()})", overscroll.isEmpty())
                    val ref = reference ?: travel.also { reference = it }
                    assertEquals("${screen.name}/$id: the same fling distance as every container", ref, travel, TRAVEL_EPS)
                }
                report.append("${screen.name}/$id: v=%.0f travel=%.1f room=%.0f\n".format(v, travel, room))
                if (room < BOUNCE_ROOM_PX) continue
                // The same flick back from the end of the content, at rest: the reference distance on screen.
                open(screen)
                val restRef =
                    container(id)?.let { n ->
                        val p = flickPoint(n)
                        repeat(BOUNCE_FLICKS) {
                            flick(p, -BOUNCE_PX, BOUNCE_MS)
                            idle()
                        }
                        val s0 = container(id)!!.range().first
                        flick(p, BACK_PX, BACK_MS)
                        idle()
                        s0 - container(id)!!.range().first
                    } ?: continue
                for (delay in longArrayOf(50, 150)) {
                    open(screen)
                    val n = container(id) ?: break
                    val p = flickPoint(n)
                    repeat(BOUNCE_FLICKS) {
                        flick(p, -BOUNCE_PX, BOUNCE_MS)
                        idle()
                    }
                    compose.mainClock.autoAdvance = false
                    flick(p, -BOUNCE_PX, BOUNCE_MS)
                    compose.mainClock.advanceTimeBy(delay)
                    flings.clear()
                    val o0 = overscrollY
                    val s0 = container(id)!!.range().first
                    flick(p, BACK_PX, BACK_MS)
                    compose.mainClock.autoAdvance = true
                    idle()
                    val back = maxFling(id)
                    assertTrue("${screen.name}/$id $delay ms after a bounce: the flick flings ($back px/s)", back >= MIN_FLING)
                    // Report d / R1-4: the same flick moves the content the same distance on screen as from rest (the
                    // scrolled distance plus the offset it took back), not that plus a return spring running on. The
                    // one difference is the platform's catch: a finger landing on moving content takes it at once,
                    // while at rest the first touch slop of its travel only decides that it is a drag.
                    val onScreen = (s0 - container(id)!!.range().first) - o0
                    val expected = restRef + slop
                    report.append(
                        "${screen.name}/$id $delay ms after a bounce: o0=%.1f travel=%.1f rest=%.1f + slop %.1f\n".format(
                            o0,
                            onScreen,
                            restRef,
                            slop,
                        ),
                    )
                    assertEquals(
                        "${screen.name}/$id $delay ms after a bounce: the distance of the same flick at rest",
                        expected,
                        onScreen,
                        BOUNCE_EPS,
                    )
                }
                // Starting on a pressable inside the container: the flick still flings and fires no click.
                open(screen)
                val n = container(id) ?: continue
                val area = visible(n)
                val pressable =
                    compose
                        .onAllNodes(SemanticsMatcher.keyIsDefined(NbPressable), useUnmergedTree = true)
                        .fetchSemanticsNodes()
                        .filter { it.layoutInfo.isPlaced }
                        .map { it.boundsInRoot }
                        .firstOrNull { b -> area.contains(b.center) && b.center.y < area.top + area.height * 0.6f } ?: continue
                val depth = nav.currentBackStack.value.size
                flings.clear()
                frames.clear()
                flick(pressable.center, -FLICK_PX, FLICK_MS)
                idle()
                assertTrue("${screen.name}/$id from a pressable: the flick flings", maxFling(id) >= MIN_FLING)
                assertEquals("${screen.name}/$id from a pressable: no click", depth, nav.currentBackStack.value.size)
                assertTrue("${screen.name}/$id from a pressable: nothing opens", frames.none { it.kind in OPENING })
            }
        }
        assertTrue("some containers scroll", scrolling > 0)
    }

    private class Look(
        val depth: Int,
        val top: String?,
        val states: List<String>,
    )

    private fun look(): Look {
        val states =
            compose
                .onAllNodes(SemanticsMatcher.keyIsDefined(NbPressable), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .filter { it.layoutInfo.isPlaced }
                .map {
                    "${it.config.getOrNull(SemanticsProperties.ToggleableState)}/${it.config.getOrNull(SemanticsProperties.Selected)}"
                }
        return Look(nav.currentBackStack.value.size, nav.currentBackStackEntry?.id, states)
    }

    private fun pressables(): List<SemanticsNode> {
        val w = window
        return compose
            .onAllNodes(SemanticsMatcher.keyIsDefined(NbPressable), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { it.layoutInfo.isPlaced && w.contains(it.boundsInRoot.center) }
    }

    // One sweep: screens x pressables.
    @Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth", "LoopWithTooManyJumpStatements")
    @Test
    fun `pressables - a swipe never clicks, and every trigger of one kind draws the same curve on the Nav spring`() {
        val curves = HashMap<NbKind, Pair<String, List<Float>>>()
        var triggers = 0
        for (screen in AppScreens.all) {
            open(screen)
            val count = pressables().size
            var dirty = false
            for (i in 0 until count) {
                if (dirty) {
                    reopen(screen)
                    dirty = false
                }
                val node = pressables().getOrNull(i) ?: break
                val name = "${screen.name}#$i ${node.config.getOrNull(SemanticsProperties.ContentDescription) ?: ""}".trim()
                // A horizontal swipe from the element: never its click (a step area may step: the Pager kind).
                val before = look()
                frames.clear()
                val c = node.boundsInRoot.center
                swipeX(c, if (c.x > window.center.x) -SWIPE_PX else SWIPE_PX)
                idle()
                val after = look()
                assertEquals("$name: a swipe does not navigate", before.depth to before.top, after.depth to after.top)
                assertTrue("$name: a swipe opens nothing", frames.none { it.kind in OPENING })
                if (frames.any { it.kind == NbKind.Pager }) {
                    dirty = true
                    continue
                }
                assertEquals("$name: a swipe toggles or selects nothing", before.states, after.states)
                // The tap: if it changes what is shown, its kind runs on the Nav spring on the curve of that kind.
                val again = pressables().getOrNull(i) ?: break
                frames.clear()
                runCatching {
                    compose
                        .onAllNodes(
                            SemanticsMatcher("node ${again.id}") {
                                it.id == again.id
                            },
                            useUnmergedTree = true,
                        )[0]
                        .performSemanticsAction(SemanticsActions.OnClick)
                }
                idle()
                val drawn = frames.toList()
                if (drawn.isEmpty() && look().let { it.depth == after.depth && it.top == after.top }) continue
                dirty = true
                for ((host, hostFrames) in drawn.groupBy { it.host }) {
                    val kind = hostFrames.first().kind
                    assertTrue("$name: one kind on $host", hostFrames.all { it.kind == kind })
                    assertTrue("$name: the Nav spring", hostFrames.all { it.spring === NbPhysics.Nav })
                    val curve =
                        hostFrames.filter { !it.gesture }.map { it.p }.fold(mutableListOf<Float>()) { acc, p ->
                            if (acc.lastOrNull() !=
                                p
                            ) {
                                acc += p
                            }
                            ; acc
                        }
                    if (curve.size < MIN_CURVE_FRAMES) continue
                    triggers++
                    val ref = curves.getOrPut(kind) { name to curve }
                    assertEquals("$name ($host/$kind) draws the curve of ${ref.first}", ref.second.size, curve.size)
                    ref.second.zip(curve).forEach { (a, b) -> assertEquals("$name ($host/$kind) vs ${ref.first}", a, b, CURVE_EPS) }
                }
            }
        }
        report.append("triggers with a change: $triggers; kinds: ${curves.keys}\n")
        assertTrue("some triggers change what is shown", triggers > 0)
    }

    companion object {
        const val METRIC = "respiratory_rate"
        const val MAX_BACKS = 8

        /** A fling: the flick's release velocity is at least this, px/s (the flick itself is about 4000 px/s). */
        const val MIN_FLING = 1000f

        /** Fling distance epsilon across containers, px. */
        const val TRAVEL_EPS = 1f

        /** On-screen distance epsilon of a flick after an edge bounce against the same flick at rest, px. */
        const val BOUNCE_EPS = 2f

        const val FLICK_PX = 600f
        const val FLICK_MS = 150L
        const val EDGE_MARGIN_PX = 48f
        const val MIN_HEIGHT_PX = 300f
        const val BOUNCE_ROOM_PX = 900f
        const val BOUNCE_FLICKS = 4
        const val BOUNCE_PX = 900f
        const val BOUNCE_MS = 80L
        const val BACK_PX = 300f
        const val BACK_MS = 100L
        const val SWIPE_PX = 200f
        const val SWIPE_MS = 120L
        const val MIN_CURVE_FRAMES = 3
        const val CURVE_EPS = 1e-4f

        /** Kinds a click opens (a step swipe may run the Pager kind; nothing else may move). */
        val OPENING = setOf(NbKind.Page, NbKind.Tab, NbKind.Sheet, NbKind.Dialog, NbKind.DockedDialog, NbKind.Swap)
    }
}
