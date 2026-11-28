package io.github.alien2003.nightbaseline.ui.interaction

import android.app.Application
import android.os.Looper
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.ui.material.EffectsDecision
import io.github.alien2003.nightbaseline.ui.theme.NightBaselineTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Round 3 of the framework (ADR 0011 R7 to R9, P1, and the measured and reviewed defects of round 2), each test
 * written against the defect it fixes and failing without the fix (the failure it would show is stated in each).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w412dp-h915dp-port-420dpi")
class FrameworkRound3Test {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var controller: ActivityController<ComponentActivity>? = null
    private val activity: ComponentActivity get() = controller!!.get()
    private val frames = CopyOnWriteArrayList<NbFrame>()

    /** The test's own state when each frame was reported (what the host's caller had applied by then). */
    private val marks = CopyOnWriteArrayList<Int>()

    @Volatile
    private var mark = 0

    /** Shifts [NbClock.nowNanos] (ms): a request read while it is negative looks that much older than its frame. */
    @Volatile
    private var clockShiftMs = 0L

    @Before
    fun setUp() {
        NbMotionProbe.frames = {
            frames += it
            marks += mark
        }
        NbClock.nowNanos = {
            val once = shiftOnceMs
            shiftOnceMs = 0
            (compose.mainClock.currentTime + clockShiftMs + once) * NANOS_PER_MS
        }
    }

    /** Shifts the next read of [NbClock.nowNanos] only (ms). */
    @Volatile
    private var shiftOnceMs = 0L

    @After
    fun tearDown() {
        NbMotionProbe.frames = null
        NbMotionProbe.press = null
        NbMotionProbe.parked = null
        NbMotionProbe.flings = null
        NbClock.nowNanos = System::nanoTime
        controller?.pause()?.stop()?.destroy()
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

    private fun moving(host: String): List<NbFrame> = frames.filter { it.host == host && it.p > 0f && it.p < 1f }

    private val width: Float get() =
        activity.resources.displayMetrics.widthPixels
            .toFloat()

    private val back get() = activity.onBackPressedDispatcher

    private fun gesture(vararg shares: Float) {
        back.dispatchOnBackStarted(BackEventCompat(0f, TOUCH_Y, 0f, BackEventCompat.EDGE_LEFT))
        idle()
        for (share in shares) {
            back.dispatchOnBackProgressed(BackEventCompat(share * width, TOUCH_Y, share, BackEventCompat.EDGE_LEFT))
            idle()
        }
    }

    /** The Nav spring from rest towards 1 after [t] s (closed form, overdamped). */
    private fun spring(t: Double): Double {
        val z = NbPhysics.Nav.damping.toDouble()
        val w = sqrt(NbPhysics.Nav.stiffness.toDouble())
        val r1 = -w * (z - sqrt(z * z - 1))
        val r2 = -w * (z + sqrt(z * z - 1))
        val c2 = r1 / (r2 - r1)
        val c1 = -1.0 - c2
        return 1.0 + c1 * exp(r1 * t) + c2 * exp(r2 * t)
    }

    /** A stack host whose caller counts its own compositions in [mark] (a state change of the caller recomposes it). */
    @Composable
    private fun CountedStack(
        stack: List<String>,
        onBack: () -> Unit,
    ) {
        SideEffect { mark++ }
        NbStackHost(stack, key = { it }, onBack = onBack, id = "s") { Box(Modifier.fillMaxSize()) }
    }

    @Test
    fun `N2 - a committed back gesture pops once at the commit and moves to its end unbroken (V2-1)`() {
        var stack by mutableStateOf(listOf("a", "b"))
        var pops = 0
        show {
            CountedStack(stack) {
                pops++
                stack = stack.dropLast(1)
            }
        }
        gesture(0.2f, 0.4f)
        compose.mainClock.autoAdvance = false
        frames.clear()
        marks.clear()
        val before = mark
        back.onBackPressed()
        repeat(MOTION_FRAMES) { frame() }
        compose.mainClock.autoAdvance = true
        idle()
        val motion = frames.indices.filter { frames[it].host == "s" && frames[it].p < 1f && !frames[it].gesture }
        assertTrue("the release runs: ${frames.map { it.p }}", motion.size >= 3)
        // Round 5 (ADR 0011 N2): the machine and the caller change at the commit, once; the caller's composition is
        // done by the release's first moving frame and nothing composes again while the page moves.
        assertTrue(
            "the caller composes once, at the commit: ${motion.map { marks[it] - before }}",
            marks[motion.last()] - before <= 1,
        )
        assertEquals("popped exactly once, at the end", 1, pops)
        assertEquals(listOf("a"), stack)
    }

    @Test
    fun `N2 - a Back without a gesture runs the same pop, its first frame one display frame in`() {
        var stack by mutableStateOf(listOf("a", "b"))
        var router: NbBackRouter? = null
        show {
            router = LocalNbBackRouter.current
            CountedStack(stack) { stack = stack.dropLast(1) }
        }
        compose.mainClock.autoAdvance = false
        frames.clear()
        marks.clear()
        val before = mark
        compose.runOnUiThread { router!!.back() }
        repeat(MOTION_FRAMES) { frame() }
        compose.mainClock.autoAdvance = true
        idle()
        val motion = frames.indices.filter { frames[it].host == "s" && frames[it].p > 0f && frames[it].p < 1f }
        assertTrue("the pop runs: ${frames.map { it.p }}", motion.size >= 3)
        assertTrue("the state changes once, at the Back", marks[motion.last()] - before <= 1)
        assertTrue("the first frame is one display frame in", frames[motion.first()].p <= spring(FRAME_S) + 1e-3)
        assertEquals(listOf("a"), stack)
    }

    @Test
    fun `V2-2 - a Back that comes during a cancelled gesture's return continues it to the end, never a one-frame vanish`() {
        for (direct in listOf(false, true)) {
            var stack by mutableStateOf(listOf("a", "b"))
            var router: NbBackRouter? = null
            show {
                router = LocalNbBackRouter.current
                NbStackHost(stack, key = { it }, onBack = { stack = stack.dropLast(1) }, id = "s") { Box(Modifier.fillMaxSize()) }
            }
            gesture(0.2f, 0.5f)
            compose.mainClock.autoAdvance = false
            back.dispatchOnBackCancelled()
            repeat(3) { frame() }
            val atCancel = frames.last { it.host == "s" }.p
            frames.clear()
            // The platform's commit without a start (the second gesture of V2-2), or the app popping by itself.
            compose.runOnUiThread { if (direct) stack = listOf("a") else router!!.back() }
            repeat(MOTION_FRAMES) { frame() }
            compose.mainClock.autoAdvance = true
            idle()
            val p = frames.filter { it.host == "s" }.map { it.p }
            // Before the fix the change was "already running" (its cancel): the cancel ran on to 0 and the page then
            // vanished in one frame (p fell to 0, then no frame).
            assertTrue("direct=$direct: it moves on towards the end: $p", p.size >= 3 && p.last() > 0.9f)
            assertTrue("direct=$direct: from where the cancel was ($atCancel): $p", p.first() >= atCancel - 0.05f)
            p.zipWithNext().forEach { (a, b) -> assertTrue("direct=$direct: monotonic $p", b >= a - 1e-4f) }
            assertEquals(listOf("a"), stack)
            controller?.pause()?.stop()?.destroy()
            controller = null
        }
    }

    @Test
    fun `a second gesture that catches a cancelled one's return follows the finger from where the page is`() {
        var stack by mutableStateOf(listOf("a", "b"))
        show { NbStackHost(stack, key = { it }, onBack = { stack = stack.dropLast(1) }, id = "s") { Box(Modifier.fillMaxSize()) } }
        gesture(0.3f)
        compose.mainClock.autoAdvance = false
        back.dispatchOnBackCancelled()
        repeat(2) { frame() }
        val p0 = frames.last { it.host == "s" }.p
        back.dispatchOnBackStarted(BackEventCompat(0f, TOUCH_Y, 0f, BackEventCompat.EDGE_LEFT))
        frame()
        back.dispatchOnBackProgressed(BackEventCompat(0.25f * width, TOUCH_Y, 0.1f, BackEventCompat.EDGE_LEFT))
        frame()
        val p = frames.last { it.host == "s" }.p
        assertEquals("the page moves by the finger's travel from where it was", NbGestureMap.stackPop(0.25f, p0), p, 2e-3f)
        back.onBackPressed()
        compose.mainClock.autoAdvance = true
        idle()
        assertEquals(listOf("a"), stack)
    }

    @Test
    fun `R7 - a change set up by a slow first frame starts one display frame into the spring (V2-4)`() {
        var stack by mutableStateOf(listOf("a"))
        show { NbStackHost(stack, key = { it }, onBack = { stack = stack.dropLast(1) }, id = "s") { Box(Modifier.fillMaxSize()) } }
        compose.mainClock.autoAdvance = false
        frames.clear()
        // The request is read 200 ms before the frame after it (a set-up frame that took 200 ms).
        clockShiftMs = -200
        stack = listOf("a", "b")
        frame()
        clockShiftMs = 0
        repeat(MOTION_FRAMES) { frame() }
        compose.mainClock.autoAdvance = true
        idle()
        val p = moving("s").map { it.p }.distinct()
        assertTrue("the push runs: $p", p.size >= 3)
        // Before the fix the origin was the request: the first frame was the spring at 216 ms (p ~ 0.98).
        assertTrue("first frame ${p.first()} is one display frame in", p.first() <= spring(PERIOD_S) + 1e-3)
    }

    @Test
    fun `V1 - a modal its caller closes runs its hide from the close, however long it was open`() {
        var value by mutableStateOf<String?>("x")
        show { ModalHost(value, onDismiss = { value = null }) { Box(Modifier.modalPanel(state).fillMaxWidth().height(300.dp)) } }
        compose.mainClock.advanceTimeBy(5_000)
        idle()
        compose.mainClock.autoAdvance = false
        frames.clear()
        // The caller clears the value itself (no dismiss), as the More sheet does when it opens a tab.
        value = null
        repeat(MOTION_FRAMES) { frame() }
        compose.mainClock.autoAdvance = true
        idle()
        val p = moving("modal").map { it.p }.distinct()
        // Before the fix the hide ran from the open request 5 s earlier: its first frame was past the settle, a snap.
        assertTrue("the hide moves: $p", p.size >= 3)
        assertTrue("from the shown pose: ${p.first()}", p.first() >= 1f - spring(PERIOD_S) - 1e-3)
    }

    @Test
    fun `V5 and V12 - a back gesture moves a modal caught mid-show by the finger's travel, and a fold drops it`() {
        var value by mutableStateOf<String?>(null)
        var router: NbBackRouter? = null
        show {
            router = LocalNbBackRouter.current
            ModalHost(value, onDismiss = { value = null }) {
                modal = state
                Box(Modifier.modalPanel(state).fillMaxWidth().height(300.dp))
            }
        }
        compose.mainClock.autoAdvance = false
        value = "x"
        repeat(4) { frame() }
        val p0 = frames.last { it.host == "modal" }.p
        assertTrue("caught mid-show: $p0", p0 > 0.2f && p0 < 0.95f)
        back.dispatchOnBackStarted(BackEventCompat(0f, TOUCH_Y, 0f, BackEventCompat.EDGE_LEFT))
        frame()
        back.dispatchOnBackProgressed(BackEventCompat(0.2f * width, TOUCH_Y, 0.05f, BackEventCompat.EDGE_LEFT))
        frame()
        // Before the fix: p0 (1 - g), a move of p0 g instead of the finger's g.
        assertEquals("the panel moves by the finger's travel", p0 - 0.2f, frames.last { it.host == "modal" }.p, 2e-3f)
        // A fold: the gesture is void, the panel at rest open, further moves do nothing (before: it kept following).
        compose.runOnUiThread { router!!.abort() }
        frame()
        back.dispatchOnBackProgressed(BackEventCompat(0.6f * width, TOUCH_Y, 0.3f, BackEventCompat.EDGE_LEFT))
        repeat(3) { frame() }
        compose.mainClock.autoAdvance = true
        idle()
        compose.runOnIdle { assertEquals("open and at rest after the fold", ModalPhase.Shown, modal!!.phase) }
        assertEquals("x", value)
    }

    private var modal: ModalState? = null

    @Test
    fun `V4 - a step hand-over read by a discarded composition is still there for the applied one`() {
        val handoff = NbStepHandoff()
        handoff.hand(2400f)
        // A composition reads it and is thrown away; the next one reads it again and is applied.
        val discarded = handoff.peek()
        val applied = handoff.peek()
        assertEquals("a read changes nothing (before: take() cleared it)", discarded, applied)
        assertEquals(2400f, applied!!.second, 0f)
        handoff.consume(applied.first)
        assertEquals("consumed once applied", null, handoff.peek())
        // And everything a change set up in a discarded composition is rolled back with it.
        val t = NbTransition("t", CoroutineScope(Dispatchers.Unconfined), listOf("A"))
        val snapshot = Snapshot.takeMutableSnapshot()
        snapshot.enter { t.dispatch(NbNavEvent.Go(listOf("B"), NbKind.Page, toIsUpper = true, e = 1, now = 0L), inComposition = true) }
        snapshot.dispose()
        assertFalse("nothing of the discarded composition is left", t.running)
    }

    @Test
    fun `V6 - a page pushed over focus inside a step area takes the focus away from it, and its keys step nothing`() {
        var stack by mutableStateOf(listOf("a"))
        var stepped = 0
        var focused = false
        val requester = FocusRequester()
        show {
            NbStackHost(stack, key = { it }, onBack = {}, id = "s") { page ->
                Box(Modifier.fillMaxSize().testTag("page-$page")) {
                    if (page == "a") {
                        Box(
                            Modifier
                                .size(100.dp)
                                .nbStepArea(onPrevious = { stepped++ }, onNext = { stepped++ })
                                .onFocusChanged { focused = it.isFocused }
                                .focusRequester(requester)
                                .focusable(),
                        )
                    }
                }
            }
        }
        compose.runOnIdle { requester.requestFocus() }
        idle()
        assertTrue(focused)
        stack = listOf("a", "b")
        idle()
        // Before the fix the parked page kept the focus and its step handler answered the arrows.
        assertFalse("the parked page holds no focus", focused)
        compose.onNodeWithTag("page-b").performKeyInput { pressKey(Key.DirectionRight) }
        idle()
        assertEquals("no step of a page not shown", 0, stepped)
    }

    @Test
    fun `V8 - the parked page stays composed through a push, and a pop composes the next page below ahead`() {
        var stack by mutableStateOf(listOf("a", "b"))
        val disposed = CopyOnWriteArrayList<String>()
        val composedAt = CopyOnWriteArrayList<Pair<String, Int>>()
        var router: NbBackRouter? = null
        show {
            router = LocalNbBackRouter.current
            NbStackHost(stack, key = { it }, onBack = { stack = stack.dropLast(1) }, id = "s") { page ->
                DisposableEffect(page) {
                    composedAt += page to frames.size
                    onDispose { disposed += page }
                }
                Box(Modifier.fillMaxSize())
            }
        }
        compose.mainClock.autoAdvance = false
        frames.clear()
        stack = listOf("a", "b", "c")
        repeat(4) { frame() }
        // Before the fix the parked page was disposed in the push's first composition (cost in its first frame).
        assertFalse("the parked page is not disposed while the push runs: $disposed", "a" in disposed)
        compose.mainClock.autoAdvance = true
        idle()
        composedAt.clear()
        frames.clear()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { router!!.back() }
        repeat(MOTION_FRAMES) { frame() }
        compose.mainClock.autoAdvance = true
        idle()
        val motionFrames = moving("s").size
        val a = composedAt.firstOrNull { it.first == "a" }
        // Round 6 (H2): nothing is composed while the pop runs (round 5 composed the next page below in the pop's
        // first frame). Round 7: only one page is parked below the top, so the page that is below after the pop is
        // composed once the pop has settled, ahead of the next Back.
        assertTrue("the pop moved ($motionFrames frames)", motionFrames > 0)
        assertTrue("nothing is composed during the pop: $a", a == null || a.second >= motionFrames)
        assertTrue("the new page below is composed ahead once at rest: $composedAt", composedAt.any { it.first == "a" })
        assertEquals(listOf("a", "b"), stack)
    }

    @Test
    fun `V9 - the parked layer is placed ahead and its content is never drawn (round 7)`() {
        val parked = CopyOnWriteArrayList<Pair<String, Any>>()
        val drawn = CopyOnWriteArrayList<String>()
        NbMotionProbe.parked = { host, key -> parked += host to key }
        show {
            Box(Modifier.size(200.dp)) {
                NbStackHost(listOf("a", "b"), key = { it }, onBack = {}, id = "s") { page ->
                    Box(Modifier.fillMaxSize().drawBehind { drawn += page })
                }
            }
        }
        // Round 7: placed (its layer is updated) but not drawn; round 6 drew it, invisibly, in every frame.
        assertTrue("the parked page is placed: $parked", parked.contains("s" to "a"))
        drawWindow()
        drawWindow()
        assertTrue("the top page is drawn: $drawn", "b" in drawn)
        assertFalse("the parked page is never drawn: $drawn", "a" in drawn)
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

    @Test
    fun `V3 - a reveal runs on the hosts' driver - Nav spring, R7 clock, every frame reported`() {
        var value by mutableStateOf<String?>(null)
        show { Column { NbReveal(value) { Box(Modifier.fillMaxWidth().height(80.dp)) } } }
        compose.mainClock.autoAdvance = false
        frames.clear()
        clockShiftMs = -200
        value = "banner"
        frame()
        clockShiftMs = 0
        repeat(MOTION_FRAMES) { frame() }
        compose.mainClock.autoAdvance = true
        idle()
        val reveal = moving("reveal")
        // Before the fix the reveal ran on its own Animatable: no frame reached the probe.
        assertTrue("the reveal's frames are reported: ${reveal.map { it.p }}", reveal.size >= 3)
        assertTrue("kind Reveal on the Nav spring", reveal.all { it.kind == NbKind.Reveal && it.spring === NbPhysics.Nav })
        assertTrue("one display frame in at first", reveal.first().p <= spring(PERIOD_S) + 1e-3)
    }

    @Test
    fun `V13 and P1 - the default indication is observable, and a quick tap in a list shows its press before its click`() {
        val samples = CopyOnWriteArrayList<Triple<Any, NbPressSample, Float>>()
        NbMotionProbe.press = { element, sample, v -> samples += Triple(element, sample, v) }
        val source = MutableInteractionSource()
        var litAtClick: Boolean? = null
        show {
            Column {
                Box(Modifier.size(100.dp).clickable(source, LocalIndication.current) {})
                Box(
                    Modifier.size(300.dp).nbVerticalScroll(rememberNbScrollState(), id = "list"),
                ) {
                    Column {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .testTag("row")
                                .nbClickable(onClick = {
                                    litAtClick = samples.any { it.second == NbPressSample.Highlight && it.third > 0.5f }
                                }, shape = RectangleShape, pressScale = false),
                        )
                        Box(Modifier.fillMaxWidth().height(900.dp))
                    }
                }
            }
        }
        // V13: a press on the plain clickable reaches the probe (before: the default indication reported nothing).
        val press = PressInteraction.Press(Offset(5f, 5f))
        compose.runOnIdle { source.tryEmit(press) }
        idle()
        assertTrue("the default indication's highlight is reported", samples.any { it.second == NbPressSample.Highlight })
        compose.runOnIdle { source.tryEmit(PressInteraction.Release(press)) }
        idle()
        samples.clear()
        // P1: a quick tap on a row in a scroll container (press and release arrive together at the lift).
        compose.onNodeWithTag("row").performTouchInput {
            down(center)
            up()
        }
        idle()
        // Before the fix the click ran in the lift's event, before any frame: nothing of the press had been drawn.
        assertEquals("the press is on screen before the click runs", true, litAtClick)
    }

    @Test
    fun `V2-7 - a scroll container's margin scrolls with it - a flick that starts in the margin flings`() {
        val flings = CopyOnWriteArrayList<String>()
        NbMotionProbe.flings = { id, _ -> flings += id }
        show {
            Box(
                Modifier
                    .size(300.dp, 400.dp)
                    .testTag("panel")
                    .nbVerticalScroll(rememberNbScrollState(), id = "panel-1", contentPadding = PaddingValues(horizontal = 24.dp)),
            ) { Box(Modifier.fillMaxWidth().height(2000.dp)) }
        }
        compose.onNodeWithTag("panel").performTouchInput { swipe(Offset(4f, height * 0.8f), Offset(4f, height * 0.2f), 120) }
        idle()
        assertTrue("the flick in the margin flings: $flings", "panel-1" in flings)
    }

    @Test
    fun `V2-6 - the page's pose is a function of the touch position alone - the same x draws the same pose on any path`() {
        var stack by mutableStateOf(listOf("a", "b"))
        show { NbStackHost(stack, key = { it }, onBack = { stack = stack.dropLast(1) }, id = "s") { Box(Modifier.fillMaxSize()) } }
        back.dispatchOnBackStarted(BackEventCompat(0f, TOUCH_Y, 0f, BackEventCompat.EDGE_LEFT))
        idle()
        val seen = HashMap<Float, Float>()
        // Forwards, beyond, back, and again: shaped progress values that differ on every visit.
        for ((i, share) in listOf(0.2f, 0.4f, 0.6f, 0.4f, 0.2f, 0.4f, 0.2f).withIndex()) {
            back.dispatchOnBackProgressed(BackEventCompat(share * width, TOUCH_Y, (i % 3) * 0.1f, BackEventCompat.EDGE_LEFT))
            idle()
            val p = frames.last { it.host == "s" }.p
            seen[share]?.let { assertEquals("share $share: no hysteresis", it, p, 0f) }
            seen[share] = p
        }
        back.dispatchOnBackCancelled()
        idle()
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val FRAME_S = 0.016
        const val PERIOD_S = 0.016
        const val WAIT_FRAMES = 6
        const val MOTION_FRAMES = 40
        const val TOUCH_Y = 900f
    }
}
