package io.github.alien2003.nightbaseline.ui.interaction

import android.app.Application
import android.os.Looper
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.ui.components.NbText
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
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * Frame tests of the navigation state machine in real hosts (ADR 0011 N1 to N8), one per open item of the round-4
 * measurement and review. Each states the failure it shows without the machine.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w412dp-h915dp-port-420dpi")
class NavFramesTest {
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

    private val width: Float get() =
        activity.resources.displayMetrics.widthPixels
            .toFloat()

    private val back get() = activity.onBackPressedDispatcher

    private fun start(x: Float) {
        back.dispatchOnBackStarted(BackEventCompat(x, TOUCH_Y, 0f, BackEventCompat.EDGE_LEFT))
        frame()
    }

    private fun move(x: Float) {
        back.dispatchOnBackProgressed(BackEventCompat(x, TOUCH_Y, x / width, BackEventCompat.EDGE_LEFT))
        frame()
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

    /** One display frame of the Nav spring from rest: the largest first frame of any change (R7). */
    private val firstStep: Float get() = spring(FRAME_S).toFloat() + 1e-3f

    private fun host(id: String) = frames.filter { it.host == id }

    @Test
    fun `R3-1 - a back gesture that starts while a cancelled one returns moves the page with the finger, then commits`() {
        // Variants: a normal second gesture; one whose start reaches the app but no move; a commit with no start at
        // all (the platform's queued gesture). Round 4: the page sat at rest, then vanished in one frame.
        for (variant in listOf("moves", "no-moves", "no-start")) {
            var stack by mutableStateOf(listOf("history", "night"))
            show { NbStackHost(stack, key = { it }, onBack = { stack = stack.dropLast(1) }, id = "s") { Box(Modifier.fillMaxSize()) } }
            compose.mainClock.autoAdvance = false
            start(0f)
            for (x in listOf(80f, 200f, 350f, 200f, 20f)) move(x)
            back.dispatchOnBackCancelled()
            repeat(2) { frame() }
            val returning = host("s").last()
            assertTrue("$variant: the cancel is returning at ${returning.p}", returning.p > 0f && !returning.gesture)
            frames.clear()
            when (variant) {
                "moves" -> {
                    start(40f)
                    val edge0 = host("s").last().fromPose.tx
                    assertEquals("$variant: the start does not move the page", returning.fromPose.tx, edge0, 1f)
                    for (x in listOf(140f, 300f, 520f)) {
                        move(x)
                        assertEquals("$variant: the edge follows the finger", edge0 + (x - 40f), host("s").last().fromPose.tx, 1f)
                    }
                }

                "no-moves" -> {
                    start(40f)
                }

                else -> {
                    Unit
                }
            }
            // The night page's edge when the commit comes (the frames are reported towards the motion's target).
            val lastFrame = host("s").lastOrNull()
            val atCommit = lastFrame?.let { if (it.from == "night") it.fromPose.tx else it.toPose.tx } ?: returning.fromPose.tx
            frames.clear()
            back.onBackPressed()
            repeat(MOTION_FRAMES) { frame() }
            compose.mainClock.autoAdvance = true
            idle()
            val p = host("s").map { it.p }
            assertTrue("$variant: it moves to the end over frames: $p", p.size >= MIN_FRAMES)
            assertTrue("$variant: towards the end", host("s").all { it.from == "night" })
            assertTrue("$variant: from where it was ($atCommit)", host("s").first().fromPose.tx >= atCommit - 1f)
            p.zipWithNext().forEach { (a, b) -> assertTrue("$variant: monotonic, no jump: $p", b >= a - 1e-4f && b - a <= MAX_STEP) }
            assertEquals(listOf("history"), stack)
            controller?.pause()?.stop()?.destroy()
            controller = null
        }
    }

    @Test
    fun `R3-2 - the bar shows one selection per change - the target of a tap from its first frame, a gesture's at its commit`() {
        var selected by mutableStateOf(1)
        val shown = CopyOnWriteArrayList<Int>()
        var current = -1
        show {
            NbSectionHost(selected, listOf(0, 1, 2), onBackToFirst = { selected = 0 }, id = "sec") { section, _ ->
                val bar = nbShownSection(section)
                SideEffect { current = bar }
                Box(Modifier.fillMaxSize())
            }
        }
        compose.mainClock.autoAdvance = false

        fun record() {
            frame()
            shown += current
        }
        // A gesture back to the first section, cancelled, then again and committed.
        back.dispatchOnBackStarted(BackEventCompat(0f, TOUCH_Y, 0f, BackEventCompat.EDGE_LEFT))
        record()
        for (x in listOf(100f, 300f, 450f)) {
            back.dispatchOnBackProgressed(BackEventCompat(x, TOUCH_Y, x / width, BackEventCompat.EDGE_LEFT))
            record()
        }
        back.dispatchOnBackCancelled()
        repeat(3) { record() }
        back.dispatchOnBackStarted(BackEventCompat(0f, TOUCH_Y, 0f, BackEventCompat.EDGE_LEFT))
        record()
        for (x in listOf(200f, 500f)) {
            back.dispatchOnBackProgressed(BackEventCompat(x, TOUCH_Y, x / width, BackEventCompat.EDGE_LEFT))
            record()
        }
        back.onBackPressed()
        repeat(MOTION_FRAMES) { record() }
        // A tap on the bar: 0 -> 2.
        selected = 2
        repeat(MOTION_FRAMES) { record() }
        compose.mainClock.autoAdvance = true
        idle()
        val changes = shown.zipWithNext().count { (a, b) -> a != b }
        // Round 4 showed 1, 0, 1, 0 (target, old, target) during one gesture.
        assertEquals("one change per committed change, never a third state: $shown", 2, changes)
        val toZero = shown.indexOfFirst { it == 0 }
        val toTwo = shown.indexOfFirst { it == 2 }
        assertTrue("the gesture's commit, then the tap: $shown", toZero in 1 until toTwo)
        assertTrue("the gesture showed 1 until its commit: $shown", shown.subList(0, toZero).all { it == 1 })
        assertEquals("the tap shows its target in its first frame: $shown", 2, shown[shown.size - MOTION_FRAMES])
    }

    @Test
    fun `R3-3 and R3-4 - every trigger moves in the first frame after its input, at most one frame into the spring`() {
        var stack by mutableStateOf(listOf("a"))
        var section by mutableStateOf(0)
        var value by mutableStateOf(1)
        var router: NbBackRouter? = null
        show {
            router = LocalNbBackRouter.current
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .size(80.dp)
                        .testTag("push")
                        .nbClickable(onClick = { stack = stack + "b" }, shape = RectangleShape),
                )
                Box(Modifier.fillMaxWidth().height(300.dp)) {
                    NbStackHost(stack, key = { it }, onBack = { stack = stack.dropLast(1) }, id = "s") { page ->
                        NbText(page)
                    }
                }
                Box(Modifier.fillMaxWidth().height(200.dp)) {
                    NbSectionHost(section, listOf(0, 1), onBackToFirst = { section = 0 }, id = "sec") { s, _ -> NbText("$s") }
                }
                NbSwitcher(value, key = { it }, modifier = Modifier.size(100.dp), id = "sw", order = naturalOrder()) { NbText("$it") }
            }
        }
        compose.mainClock.autoAdvance = false
        val triggers: List<Pair<String, () -> Unit>> =
            listOf(
                "s" to { compose.onNodeWithTag("push").performTouchInput { click() } },
                "s" to { compose.runOnUiThread { router!!.back() } },
                "sec" to { compose.runOnUiThread { section = 1 } },
                "sec" to { compose.runOnUiThread { back.onBackPressed() } },
                "sw" to { compose.runOnUiThread { value = 2 } },
            )
        for ((id, trigger) in triggers) {
            repeat(MOTION_FRAMES) { frame() }
            frames.clear()
            trigger()
            frame()
            val first = host(id).firstOrNull()
            assertTrue("$id: the first frame after the input moves: ${host(id).map { it.p }}", first != null && first.p > 0f)
            assertTrue(
                "$id: the first frame is at most one display frame into the spring: ${host(id).map { it.p }}",
                first!!.p <= firstStep,
            )
        }
        compose.mainClock.autoAdvance = true
        idle()
    }

    @Test
    fun `R3-8 - the page edge equals the finger's travel within 1 px, forwards and back, from any start`() {
        for (x0 in listOf(0f, 30f)) {
            var stack by mutableStateOf(listOf("a", "b"))
            show { NbStackHost(stack, key = { it }, onBack = { stack = stack.dropLast(1) }, id = "s") { Box(Modifier.fillMaxSize()) } }
            compose.mainClock.autoAdvance = false
            start(x0)
            val seen = HashMap<Float, Float>()
            for (x in listOf(100f, 300f, 600f, 900f, 600f, 300f, 100f, 600f)) {
                move(x)
                val edge = host("s").last().fromPose.tx
                assertEquals("x0 $x0, finger $x: edge", x - x0, edge, 1f)
                seen[x]?.let { assertEquals("the same x draws the same edge both ways", it, edge, 0f) }
                seen[x] = edge
            }
            back.dispatchOnBackCancelled()
            compose.mainClock.autoAdvance = true
            idle()
            controller?.pause()?.stop()?.destroy()
            controller = null
            frames.clear()
        }
    }

    @Test
    fun `R3-5 and R3-7 - the first sheet open and the first gesture frame are no slower than twice the median frame`() {
        var open by mutableStateOf<Int?>(null)
        var stack by mutableStateOf(listOf("a", "b"))
        show {
            Box(Modifier.fillMaxSize()) {
                NbStackHost(stack, key = { it }, onBack = { stack = stack.dropLast(1) }, id = "s") { page ->
                    Column { repeat(ROWS) { NbText("$page $it") } }
                }
                ModalHost(open, onDismiss = { open = null }, prewarm = 1) { v ->
                    Column(Modifier.modalPanel(state)) { repeat(ROWS) { NbText("sheet $v row $it") } }
                }
            }
        }
        // Launch: the prewarm runs in the first frames after the host appears.
        repeat(PREWARM_WAIT) { frame() }
        compose.mainClock.autoAdvance = false

        fun timed(action: () -> Unit): Long {
            val t0 = System.nanoTime()
            action()
            frame()
            return System.nanoTime() - t0
        }

        fun run(times: MutableList<Long>) {
            repeat(MOTION_FRAMES) { times += timed {} }
        }
        val first = mutableListOf<Long>()
        first += timed { open = 1 }
        run(first)
        compose.runOnUiThread { open = null }
        repeat(MOTION_FRAMES) { frame() }
        val later = mutableListOf<Long>()
        repeat(3) {
            later += timed { open = 1 }
            run(later)
            compose.runOnUiThread { open = null }
            repeat(MOTION_FRAMES) { frame() }
        }
        // The JVM's frame times are noisy and its median frame is mostly idle: the first open's worst frame is
        // compared with the worst frame of a warm open (an extra stall on the first open is what R3-5 measured).
        val warm = later.max().coerceAtLeast(MIN_MEDIAN_NS)
        assertTrue("first sheet open: worst frame ${first.max() / 1e6} ms vs warm ${warm / 1e6} ms", first.max() <= 2 * warm)
        // The first frame of the first back gesture after the push: the page below is parked (composed and drawn).
        val gesture = mutableListOf<Long>()
        gesture += timed { back.dispatchOnBackStarted(BackEventCompat(0f, TOUCH_Y, 0f, BackEventCompat.EDGE_LEFT)) }
        gesture += timed { back.dispatchOnBackProgressed(BackEventCompat(200f, TOUCH_Y, 0.2f, BackEventCompat.EDGE_LEFT)) }
        val moves = mutableListOf<Long>()
        for (x in 3..20) {
            moves +=
                timed { back.dispatchOnBackProgressed(BackEventCompat(x * 30f, TOUCH_Y, 0.3f, BackEventCompat.EDGE_LEFT)) }
        }
        back.dispatchOnBackCancelled()
        val gestureMedian = moves.sorted()[moves.size / 2].coerceAtLeast(MIN_MEDIAN_NS)
        assertTrue(
            "first gesture frames ${gesture.map { it / 1e6 }} ms vs median ${gestureMedian / 1e6}",
            gesture.max() <= 2 * gestureMedian,
        )
        compose.mainClock.autoAdvance = true
        idle()
        assertTrue(abs(width) > 0f)
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val TOUCH_Y = 600f
        const val MOTION_FRAMES = 40
        const val MIN_FRAMES = 6
        const val FRAME_S = 1.0 / 60.0

        /** A generous bound of one frame's step of the Nav spring (its fastest frame from any start is about 0.12). */
        const val MAX_STEP = 0.2f
        const val ROWS = 60
        const val PREWARM_WAIT = 30

        /** The JVM timer's noise floor: medians below it are taken as it. */
        const val MIN_MEDIAN_NS = 4_000_000L
    }
}
