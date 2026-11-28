package io.github.alien2003.nightbaseline.ui.interaction

import android.app.Application
import android.os.Looper
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.navigation.NavHostController
import io.github.alien2003.nightbaseline.ui.material.EffectsDecision
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
import kotlin.math.abs

/**
 * Round 7 of ADR 0011 in real hosts (the back dispatcher, the router, [NbStackHost], [NbNavStack], [NbSectionHost] and
 * a stack nested in a section as the Settings sub-pages are), each stated with the failure it shows without its fix.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w412dp-h915dp-port-420dpi")
class NavRound7Test {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var controller: ActivityController<ComponentActivity>? = null
    private val activity: ComponentActivity get() = controller!!.get()
    private val frames = CopyOnWriteArrayList<NbFrame>()

    @Before
    fun setUp() {
        NbMotionProbe.frames = { frames += it }
        NbClock.nowNanos = { compose.mainClock.currentTime * NANOS_PER_MS }
        NbClock.framePeriodNanos = NbClock.DEFAULT_FRAME_NANOS
    }

    @After
    fun tearDown() {
        NbMotionProbe.frames = null
        NbClock.nowNanos = System::nanoTime
        close()
    }

    private fun close() {
        compose.mainClock.autoAdvance = true
        controller?.pause()?.stop()?.destroy()
        controller = null
        frames.clear()
    }

    private fun show(content: @Composable () -> Unit) {
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity.setContent { NightBaselineTheme(darkTheme = true, effects = EffectsDecision.ForcedSolid) { content() } }
        idle()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private fun frame() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.mainClock.advanceTimeByFrame()
    }

    private val width: Float get() =
        activity.resources.displayMetrics.widthPixels
            .toFloat()

    private val back get() = activity.onBackPressedDispatcher

    /** The edge of [page] in [host]'s latest frame (NaN when the page is in no frame). */
    private fun edgeOf(
        host: String,
        page: Any,
    ): Float {
        val f = frames.lastOrNull { it.host == host } ?: return Float.NaN
        return when (page) {
            f.from -> f.fromPose.tx
            f.to -> f.toPose.tx
            else -> Float.NaN
        }
    }

    /** The edges of a gesture's drawn frames: under the finger, then after the release. */
    private class Edges(
        val held: List<Float>,
        val after: List<Float>,
    ) {
        val all: List<Float> get() = held + after

        override fun toString(): String = "held $held, after $after"
    }

    /**
     * A back event at [x]. [timed]: with the frame time of the test clock (API 36); else with none, so the receipt
     * time is used and events delivered in one pass share it (the platform before API 36, a busy main thread).
     */
    private fun event(
        x: Float,
        progress: Float,
        edge: Int,
        timed: Boolean,
    ): BackEventCompat = BackEventCompat(x, TOUCH_Y, progress, edge, if (timed) compose.mainClock.currentTime else 0L)

    /** Draws one frame and records [page]'s edge in [host] into [into] when the frame drew the host's motion. */
    private fun record(
        host: String,
        page: Any,
        into: MutableList<Float>,
    ) {
        val before = frames.size
        frame()
        val x = if (frames.size > before) edgeOf(host, page) else Float.NaN
        if (!x.isNaN()) into += x
    }

    /**
     * A back gesture from [edge] on [host] that holds [page]: start at the edge, moves inwards over [travel] of the
     * width, then a release ([commit] or cancel) and the spring's frames.
     */
    @Suppress("LongParameterList")
    private fun gesture(
        host: String,
        page: Any,
        edge: Int,
        commit: Boolean,
        timed: Boolean,
        travel: List<Float> = listOf(0.08f, 0.2f, 0.33f, 0.45f),
    ): Edges {
        val inward = if (edge == BackEventCompat.EDGE_LEFT) 1f else -1f
        val x0 = if (edge == BackEventCompat.EDGE_LEFT) 0f else width - 1f
        val held = ArrayList<Float>()
        val after = ArrayList<Float>()
        back.dispatchOnBackStarted(event(x0, 0f, edge, timed))
        record(host, page, held)
        for (t in travel) {
            back.dispatchOnBackProgressed(event(x0 + inward * t * width, t, edge, timed))
            record(host, page, held)
        }
        if (commit) back.onBackPressed() else back.dispatchOnBackCancelled()
        repeat(MOTION_FRAMES) { record(host, page, after) }
        return Edges(held, after)
    }

    /**
     * The page follows the finger on the finger's side, then leaves ([commit]) or returns from exactly there: no frame
     * moves it by more than one frame's step, it never shows on the other side, and it is drawn over several frames
     * after the release (round 6: it switched sides or left in one frame).
     */
    private fun assertContinuous(
        what: String,
        edges: Edges,
        side: Float,
        commit: Boolean,
        caught: Boolean = false,
    ) {
        val all = edges.all
        // A caught page may start on the other side; from where it reached the finger's side it stays there.
        val onSide = if (caught) all.drop(all.indexOfFirst { it * side >= 0f }.coerceAtLeast(0)) else all
        assertTrue("$what: the page follows the finger: $edges", edges.held.isNotEmpty() && edges.held.last() * side > width * FOLLOWED)
        val step = width * FRAME_STEP
        all.zipWithNext().forEach { (a, b) -> assertTrue("$what: a frame moved ${b - a} px: $edges", abs(b - a) <= step) }
        onSide.forEach { assertTrue("$what: the page is on the finger's side: $edges", it * side >= -1f) }
        assertTrue("$what: drawn over frames after the release: $edges", edges.after.size >= MIN_FRAMES)
        val first = edges.after.first() - edges.held.last()
        if (commit) {
            assertTrue("$what: it leaves to the finger's side: $edges", edges.after.last() * side > width * OUT)
            assertTrue("$what: the first frame continues outwards: $edges", first * side >= -0.5f)
            edges.after.zipWithNext().forEach { (a, b) -> assertTrue("$what: outwards: $edges", (b - a) * side >= -0.5f) }
        } else {
            assertTrue("$what: the first frame continues from the release: $edges", abs(first) <= step)
        }
    }

    private fun sideOf(edge: Int): Float = if (edge == BackEventCompat.EDGE_LEFT) 1f else -1f

    @Test
    @Suppress("NestedBlockDepth") // Variants x release x edge.
    fun `item 1 - a back gesture commits and cancels to the side the page moves, from both edges, on Page`() {
        for (timed in listOf(true, false)) {
            for (commit in listOf(true, false)) {
                for (edge in listOf(BackEventCompat.EDGE_RIGHT, BackEventCompat.EDGE_LEFT)) {
                    var stack by mutableStateOf(listOf("a", "b"))
                    show {
                        NbStackHost(stack, key = { it }, onBack = { stack = stack.dropLast(1) }, id = "s") { Box(Modifier.fillMaxSize()) }
                    }
                    compose.mainClock.autoAdvance = false
                    val edges = gesture("s", "b", edge, commit, timed)
                    assertContinuous("page edge $edge commit $commit timed $timed", edges, sideOf(edge), commit)
                    if (commit) assertEquals(listOf("a"), stack)
                    close()
                }
            }
        }
    }

    @Test
    @Suppress("NestedBlockDepth") // Variants x release x edge.
    fun `item 1 - a back gesture commits and cancels to the side the page moves, on the app's page stack`() {
        for (timed in listOf(true, false)) {
            for (commit in listOf(true, false)) {
                for (edge in listOf(BackEventCompat.EDGE_RIGHT, BackEventCompat.EDGE_LEFT)) {
                    var nav: NavHostController? = null
                    show {
                        val c = rememberNbNavController()
                        nav = c
                        NbNavStack(c, R6Home) {
                            page<R6Home> { Box(Modifier.fillMaxSize()) }
                            page<R6Page> { Box(Modifier.fillMaxSize()) }
                        }
                    }
                    compose.runOnUiThread { nav!!.nbPush(R6Page(1)) }
                    idle()
                    compose.mainClock.autoAdvance = false
                    val top = nav!!.currentBackStackEntry!!.id
                    val edges = gesture("pages", top, edge, commit, timed)
                    assertContinuous("pages edge $edge commit $commit timed $timed", edges, sideOf(edge), commit)
                    close()
                }
            }
        }
    }

    @Test
    @Suppress("NestedBlockDepth") // Variants x release x edge.
    fun `item 1 - a back gesture commits and cancels to the side the section moves, from both edges, on Tab`() {
        for (timed in listOf(true, false)) {
            for (commit in listOf(true, false)) {
                for (edge in listOf(BackEventCompat.EDGE_RIGHT, BackEventCompat.EDGE_LEFT)) {
                    var selected by mutableStateOf(1)
                    show {
                        NbSectionHost(selected, listOf(0, 1), onBackToFirst = { selected = 0 }, id = "sec") { _, _ ->
                            Box(Modifier.fillMaxSize())
                        }
                    }
                    compose.mainClock.autoAdvance = false
                    val edges = gesture("sec", 1, edge, commit, timed)
                    assertContinuous("tab edge $edge commit $commit timed $timed", edges, sideOf(edge), commit)
                    if (commit) assertEquals(0, selected)
                    close()
                }
            }
        }
    }

    @Test
    @Suppress("NestedBlockDepth") // Variants x release x edge.
    fun `item 1 - a back gesture commits and cancels continuously on the Settings sub-page host`() {
        for (timed in listOf(true, false)) {
            for (commit in listOf(true, false)) {
                for (edge in listOf(BackEventCompat.EDGE_RIGHT, BackEventCompat.EDGE_LEFT)) {
                    var section by mutableStateOf(1)
                    var sub by mutableStateOf(listOf("main", "detail"))
                    show {
                        NbStackHost(listOf("home"), key = { it }, onBack = {}, id = "pages") {
                            NbSectionHost(section, listOf(0, 1), onBackToFirst = { section = 0 }, id = "sec") { s, _ ->
                                if (s == 1) {
                                    NbStackHost(sub, key = { it }, onBack = { sub = sub.dropLast(1) }, id = "settings") {
                                        Box(Modifier.fillMaxSize())
                                    }
                                } else {
                                    Box(Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                    compose.mainClock.autoAdvance = false
                    val edges = gesture("settings", "detail", edge, commit, timed)
                    assertContinuous("settings edge $edge commit $commit timed $timed", edges, sideOf(edge), commit)
                    if (commit) assertEquals(listOf("main"), sub)
                    close()
                }
            }
        }
    }

    /**
     * Start, move, cancel, then a second gesture [wait] frames into the cancel's return; [noCancel]: the platform
     * never reports the first gesture's cancel (it ran its own cancel animation) and starts the second one.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun secondGesture(
        edge: Int,
        wait: Int,
        timed: Boolean,
        noCancel: Boolean,
        tab: Boolean,
    ) {
        var stack by mutableStateOf(listOf("a", "b", "c"))
        var selected by mutableStateOf(1)
        var pops = 0
        show {
            if (tab) {
                NbSectionHost(selected, listOf(0, 1), onBackToFirst = {
                    pops++
                    selected = 0
                }, id = "s") { _, _ -> Box(Modifier.fillMaxSize()) }
            } else {
                NbStackHost(stack, key = { it }, onBack = {
                    pops++
                    stack = stack.dropLast(1)
                }, id = "s") { Box(Modifier.fillMaxSize()) }
            }
        }
        val page: Any = if (tab) 1 else "c"
        val what = "edge $edge wait $wait timed $timed late $noCancel tab $tab"
        compose.mainClock.autoAdvance = false
        val inward = sideOf(edge)
        val x0 = if (edge == BackEventCompat.EDGE_LEFT) 0f else width - 1f
        back.dispatchOnBackStarted(event(x0, 0f, edge, timed))
        frame()
        for (t in listOf(0.1f, 0.25f, 0.4f)) {
            back.dispatchOnBackProgressed(event(x0 + inward * t * width, t, edge, timed))
            frame()
        }
        val edges = ArrayList<Float>()
        if (!noCancel) back.dispatchOnBackCancelled()
        repeat(wait) { record("s", page, edges) }
        // The second gesture starts a little inside the edge.
        val x1 = x0 + inward * 20f
        val held = edges.lastOrNull() ?: edgeOf("s", page)
        assertTrue("$what: the cancel is returning ($held)", abs(held) > 1f)
        back.dispatchOnBackStarted(event(x1, 0f, edge, timed))
        record("s", page, edges)
        assertEquals("$what: the start holds the page where it is: $edges", held, edges.last(), width * FRAME_STEP)
        val start = edges.last()
        for (d in listOf(60f, 150f, 300f)) {
            back.dispatchOnBackProgressed(event(x1 + inward * d, d / width, edge, timed))
            record("s", page, edges)
            assertEquals("$what: the page follows the finger: $edges", start + inward * d, edges.last(), FINGER_PX)
        }
        val released = edges.size
        back.onBackPressed()
        repeat(MOTION_FRAMES) { record("s", page, edges) }
        compose.mainClock.autoAdvance = true
        idle()
        val step = width * FRAME_STEP
        edges.zipWithNext().forEach { (a, b) -> assertTrue("$what: a frame moved ${b - a}: $edges", abs(b - a) <= step) }
        assertTrue("$what: the commit is drawn over frames: $edges", edges.size - released >= MIN_FRAMES)
        assertTrue("$what: it leaves: $edges", edges.last() * inward > width * OUT)
        assertEquals("$what: a single pop", 1, pops)
        if (tab) assertEquals(0, selected) else assertEquals(listOf("a", "b"), stack)
        close()
    }

    @Test
    @Suppress("NestedBlockDepth") // Variants x release x edge.
    fun `item 2 - a second gesture during the cancel's return takes the page at its edge, follows the finger, one pop`() {
        // Several points of the return spring. Round 6: the page sat still under the second finger and then went from
        // its edge to the end in one frame.
        for (tab in listOf(false, true)) {
            for (edge in listOf(BackEventCompat.EDGE_LEFT, BackEventCompat.EDGE_RIGHT)) {
                for (timed in listOf(true, false)) {
                    for (wait in listOf(1, 2, 4, 8)) secondGesture(edge, wait, timed, noCancel = false, tab = tab)
                }
            }
        }
    }

    @Test
    fun `item 2 - hypotheses P3 and P4 - the take-over holds on a longer path and a stale gesture id never resolves it`() {
        // P3: the motion's path is longer than the pair (a double push still entering): a gesture whose pair q lies in
        // takes it at its position. P4: a release of an older gesture id does not resolve the finger that took over.
        val w = 1000f
        val env = NbNavEnv()
        var s = NbNavState(listOf<Any>("a"))
        s = NbNavReducer.reduce(s, NbNavEvent.Go(listOf("a", "b"), NbKind.Page, true, 1, 0L), env).state
        s = NbNavReducer.reduce(s, NbNavEvent.Go(listOf("a", "b", "c"), NbKind.Page, true, 1, 0L), env).state
        var now = 0L
        repeat(12) {
            now += NbClock.DEFAULT_FRAME_NANOS
            s = NbNavReducer.reduce(s, NbNavEvent.Frame(now), env).state
        }
        val m0 = s.motion!!
        assertTrue("a path over three pages: ${m0.path}", m0.path.size == 3 && m0.q > 1f && m0.q < 2f)
        s = NbNavReducer.reduce(s, NbNavEvent.GestureStart(listOf("a", "b"), NbKind.Page, false, -1, w, w, now, id = 7), env).state
        val held = s.motion!!
        assertEquals("P3: taken by the finger", NbDrive.Finger, held.drive)
        assertEquals("P3: at its position", m0.q, held.q, 1e-5f)
        s = NbNavReducer.reduce(s, NbNavEvent.GestureMove(w - 100f, 0.1f, now / NANOS_PER_MS + 16, now), env).state
        val stale = NbNavReducer.reduce(s, NbNavEvent.GestureEnd(commit = true, now = now, id = 6), env).state
        assertEquals("P4: a stale id does nothing", s, stale)
        val released = NbNavReducer.reduce(s, NbNavEvent.GestureEnd(commit = true, now = now, id = 7), env).state
        assertEquals("its own id commits", listOf<Any>("a", "b"), released.stack)
        val edge0 = NbNavReducer.poseOf(s.motion!!, "c", w, false).tx
        assertEquals("from its position", edge0, NbNavReducer.poseOf(released.motion!!, "c", w, false).tx, 1e-2f)
    }

    @Test
    fun `item 1 - moves that share one time stamp never release the page across the screen in one frame`() {
        // Where the platform gives no frame time, moves delivered in one pass share their receipt time. Round 6 spaced
        // them 1 ms apart: the tracker reported tens of times the finger's speed and the commit left in one frame.
        for (e in listOf(1, -1)) {
            val w = 1000f
            val env = NbNavEnv()
            val x0 = if (e == 1) 0f else w
            var s = NbNavState(listOf<Any>("a", "b"))
            s = NbNavReducer.reduce(s, NbNavEvent.GestureStart(listOf("a"), NbKind.Page, false, e, x0, w, 0L, dir = e), env).state
            var now = 0L
            for (k in 1..4) {
                now += NbClock.DEFAULT_FRAME_NANOS
                // Two moves per pass, both stamped with the pass's time.
                for (half in 0..1) {
                    val x = x0 + e * (k * 2 + half) * 25f
                    s = NbNavReducer.reduce(s, NbNavEvent.GestureMove(x, 0f, now / NANOS_PER_MS, now), env).state
                }
                s = NbNavReducer.reduce(s, NbNavEvent.Frame(now), env).state
            }
            val released = NbNavReducer.reduce(s, NbNavEvent.GestureEnd(commit = true, now = now), env).state
            var g = released
            val edges = arrayListOf(NbNavReducer.poseOf(s.motion!!, "b", w, false).tx)
            while (g.motion != null && edges.size < 200) {
                now += NbClock.DEFAULT_FRAME_NANOS
                g = NbNavReducer.reduce(g, NbNavEvent.Frame(now), env).state
                g.motion?.let { edges += NbNavReducer.poseOf(it, "b", w, false).tx }
            }
            assertTrue("e $e: the commit is drawn over frames: $edges", edges.size >= MIN_FRAMES)
            edges.zipWithNext().forEach { (a, b) -> assertTrue("e $e: a frame moved ${b - a}: $edges", abs(b - a) <= w * FRAME_STEP) }
            // The velocity it leaves with is the finger's (50 px per 16.7 ms frame, 3 W/s), not tens of times it.
            val v = released.motion!!.v0
            assertTrue("e $e: the release velocity $v is the finger's order", abs(v) < 10f)
        }
    }

    @Test
    fun `item 1 - however fast the finger, a commit is drawn over at least two frames before the page is out`() {
        // A release faster than its remaining distance per frame left in its first frame (lemma 2 ends a pop at its
        // first crossing): the page vanished at the release. Round 7 caps the release at two frames for the distance.
        val w = 1000f
        val env = NbNavEnv()
        var s = NbNavState(listOf<Any>("a", "b"))
        s = NbNavReducer.reduce(s, NbNavEvent.GestureStart(listOf("a"), NbKind.Page, false, -1, w, w, 0L, dir = -1), env).state
        var now = 0L
        for ((ms, x) in listOf(0L to 990f, 2L to 900f, 4L to 600f)) {
            now = ms * NANOS_PER_MS
            s = NbNavReducer.reduce(s, NbNavEvent.GestureMove(x, 0f, ms, now), env).state
        }
        s = NbNavReducer.reduce(s, NbNavEvent.Frame(now), env).state
        var g = NbNavReducer.reduce(s, NbNavEvent.GestureEnd(commit = true, now = now), env).state
        var drawn = 0
        while (g.motion != null && drawn < 200) {
            now += NbClock.DEFAULT_FRAME_NANOS
            g = NbNavReducer.reduce(g, NbNavEvent.Frame(now), env).state
            if (g.motion != null) drawn++
        }
        assertTrue("the commit draws $drawn frames before it is out", drawn >= 1)
        assertEquals(listOf<Any>("a"), g.stack)
    }

    @Test
    fun `item 1 - a right-edge gesture during the tail of a push takes the finger's side and leaves to it`() {
        // Round 6: the caught push kept the side it entered from (right), so a right-edge finger could not move it
        // (screen space) and its commit left to the right, the other side of the finger.
        for (tail in listOf(14, 20)) {
            var stack by mutableStateOf(listOf("a"))
            show { NbStackHost(stack, key = { it }, onBack = { stack = stack.dropLast(1) }, id = "s") { Box(Modifier.fillMaxSize()) } }
            compose.mainClock.autoAdvance = false
            stack = listOf("a", "b")
            repeat(tail) { frame() }
            val m = frames.lastOrNull { it.host == "s" }
            assertTrue("tail $tail: the push still runs", m != null && m.p < 1f)
            frames.clear()
            val edges = gesture("s", "b", BackEventCompat.EDGE_RIGHT, commit = true, timed = true)
            assertContinuous("tail $tail", edges, -1f, commit = true, caught = true)
            assertEquals(listOf("a"), stack)
            close()
        }
    }

    @Test
    fun `item 3 - a parked page is composed and placed ahead but never drawn while hidden, at rest and in motion`() {
        val drawn = CopyOnWriteArrayList<String>()
        val composed = CopyOnWriteArrayList<String>()
        var stack by mutableStateOf(listOf("a", "b", "c"))
        show {
            NbStackHost(stack, key = { it }, onBack = { stack = stack.dropLast(1) }, id = "s") { page ->
                androidx.compose.runtime.SideEffect { composed += page }
                Box(Modifier.fillMaxSize().drawBehind { drawn += page })
            }
        }
        drawWindow()
        assertTrue("the page below is composed ahead: $composed", "b" in composed)
        assertTrue("only one page below is parked: $composed", "a" !in composed)
        assertTrue("the top is drawn: $drawn", "c" in drawn)
        assertTrue("zero draws of the parked page at rest: $drawn", "b" !in drawn)
        // In a push the page below the leaving one stays parked and undrawn.
        drawn.clear()
        compose.mainClock.autoAdvance = false
        stack = listOf("a", "b", "c", "d")
        repeat(6) {
            frame()
            drawWindow()
        }
        assertTrue("the push draws its pair: $drawn", "c" in drawn && "d" in drawn)
        assertTrue("zero draws of the parked page in motion: $drawn", "b" !in drawn)
        close()
    }

    @Test
    @Suppress("LongMethod") // A tap and a release, each to its settle.
    fun `item 3 - with slow frames a motion settles in the wall time of its closed form, the release's first frame bounded`() {
        val period = NbClock.DEFAULT_FRAME_NANOS
        val env = NbNavEnv(framePeriod = period)
        val duration =
            TargetBasedAnimation(
                NbPhysics.Nav.spec(NbPhysics.P_EPS),
                Float.VectorConverter,
                0f,
                1f,
                AnimationVector1D(0f),
            ).durationNanos
        // A tap from rest, with three slow frames (120, 200 and 80 ms) in its middle.
        var s = NbNavReducer.reduce(NbNavState(listOf<Any>("a")), NbNavEvent.Go(listOf("a", "b"), NbKind.Page, true, 1, 0L), env).state
        var now = 0L
        var k = 0
        var before = 0L
        while (s.motion != null && k < 400) {
            before = now
            now +=
                when (k) {
                    4 -> 120_000_000L
                    7 -> 200_000_000L
                    10 -> 80_000_000L
                    else -> period
                }
            s = NbNavReducer.reduce(s, NbNavEvent.Frame(now), env).state
            k++
        }
        // Round 6 stretched each slow frame to two display frames: this run settled about 330 ms late.
        // It ends in the first frame at or after the closed form's end (within a millisecond of Compose's rounding).
        assertTrue(
            "settled at ${now / 1e6} ms after ${before / 1e6}, closed form ${duration / 1e6} ms",
            before < duration + MS && now >= duration - MS,
        )
        // A release whose first frame is slow: that frame moves at most one display frame, the rest is time-true.
        var g = NbNavState(listOf<Any>("a", "b"))
        var t = 0L
        g = NbNavReducer.reduce(g, NbNavEvent.GestureStart(listOf("a"), NbKind.Page, false, 1, 0f, 1000f, t), env).state
        for (i in 1..6) {
            t += period
            g = NbNavReducer.reduce(g, NbNavEvent.GestureMove(i * 40f, 0f, t / NANOS_PER_MS, t), env).state
            g = NbNavReducer.reduce(g, NbNavEvent.Frame(t), env).state
        }
        g = NbNavReducer.reduce(g, NbNavEvent.GestureEnd(commit = false, now = t), env).state
        val origin = t
        t += 250_000_000L
        g = NbNavReducer.reduce(g, NbNavEvent.Frame(t), env).state
        assertTrue("the first frame after the release is at most one display frame in", g.motion!!.play <= period)
        val lag = (t - origin) - g.motion!!.play
        val start = g.motion!!
        val back =
            TargetBasedAnimation(
                NbPhysics.Nav.spec(NbPhysics.P_EPS),
                Float.VectorConverter,
                start.start,
                start.target,
                AnimationVector1D(start.v0),
            ).durationNanos
        var n = 0
        var prior = t
        while (g.motion != null && n < 400) {
            prior = t
            t += if (n == 5) 150_000_000L else period
            g = NbNavReducer.reduce(g, NbNavEvent.Frame(t), env).state
            n++
        }
        val wall = t - origin - lag
        val previous = prior - origin - lag
        assertTrue(
            "the release settles at ${wall / 1e6} ms of its spring, closed form ${back / 1e6} ms",
            previous < back + MS && wall >= back - MS,
        )
    }

    private fun drawWindow() {
        val view = activity.window.decorView
        val bitmap =
            android.graphics.Bitmap.createBitmap(
                view.width.coerceAtLeast(1),
                view.height.coerceAtLeast(1),
                android.graphics.Bitmap.Config.ARGB_8888,
            )
        view.draw(android.graphics.Canvas(bitmap))
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val MS = 1_000_000L
        const val TOUCH_Y = 600f
        const val MOTION_FRAMES = 40
        const val MIN_FRAMES = 4

        /** The page has followed the finger by at least this share of the width before the release. */
        const val FOLLOWED = 0.2f

        /** A committed page ends at least this share of the width out. */
        const val OUT = 0.9f

        /** The edge equals the finger's travel within this, px. */
        const val FINGER_PX = 1.5f

        /**
         * One frame's step of the page edge, as a share of the width: the fastest frame of the Nav spring moves q by
         * about 0.12 and the upper page's edge by at most 2.2 W per unit of q, so 0.3 W bounds every frame of a
         * continuous motion and is far below a jump to the other side (more than W).
         */
        const val FRAME_STEP = 0.3f
    }
}
