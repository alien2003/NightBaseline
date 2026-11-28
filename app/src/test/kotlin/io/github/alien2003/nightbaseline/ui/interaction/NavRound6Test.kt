package io.github.alien2003.nightbaseline.ui.interaction

import android.app.Application
import android.os.Looper
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.toRoute
import io.github.alien2003.nightbaseline.ui.material.EffectsDecision
import io.github.alien2003.nightbaseline.ui.theme.NightBaselineTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.Serializable
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

/** Routes of the round 6 page tests. */
@Serializable
data object R6Home

@Serializable
data class R6Page(
    val n: Int,
)

/**
 * Round 6 of ADR 0011 on the app's real page stack ([NbNavStack] on a navigation controller, through the production
 * calls nbPush, the router's Back and the back dispatcher), each stated with the failure it shows without its fix.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w412dp-h915dp-port-420dpi")
class NavRound6Test {
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
        NbMotionProbe.unfollowedBack = null
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

    /** Counters of one page: compositions, measure passes, flow subscriptions. */
    private class Counts {
        var compositions = 0
        var measures = 0
        var subscriptions = 0
        var alive = false
    }

    private val counts = HashMap<Int, Counts>()
    private val flow = MutableStateFlow(0)

    @Composable
    private fun Page(n: Int) {
        val c = counts.getOrPut(n) { Counts() }
        val f = remember(n) { flow.onStart { c.subscriptions++ }.onCompletion { } }
        val value by f.collectAsStateWithLifecycle(initialValue = -1)
        SideEffect { c.compositions++ }
        androidx.compose.runtime.DisposableEffect(Unit) {
            c.alive = true
            onDispose { c.alive = false }
        }
        Box(
            Modifier.fillMaxSize().layout { m, k ->
                c.measures++
                val p = m.measure(k)
                layout(p.width, p.height) { p.place(0, 0) }
            },
        ) { SideEffect { if (value < -1) c.compositions++ } }
    }

    private var nav: NavHostController? = null
    private var router: NbBackRouter? = null

    private fun showStack() {
        show {
            router = LocalNbBackRouter.current
            val c = rememberNbNavController()
            nav = c
            NbNavStack(c, R6Home) {
                page<R6Home> { Page(0) }
                page<R6Page> { e -> Page(e.toRoute<R6Page>().n) }
            }
        }
    }

    private fun settle() {
        compose.mainClock.autoAdvance = true
        idle()
        compose.mainClock.autoAdvance = false
    }

    @Test
    fun `H2 - the page revealed by a pop composes, measures and collects nothing between the commit and the settle`() {
        showStack()
        compose.runOnUiThread { nav!!.nbPush(R6Page(1)) }
        settle()
        compose.mainClock.autoAdvance = false
        val below = counts.getValue(0)
        val c0 = below.compositions
        val s0 = below.subscriptions
        // The commit: a Back through the router (the same event as every Back).
        compose.runOnUiThread { router!!.back() }
        // Frames while the pop moves (each reports a frame of the pages' host); the counters at the last of them.
        var subsMoving = s0
        var compsMoving = c0
        var during = 0
        repeat(MOTION_FRAMES) {
            val before = frames.size
            frame()
            if (frames.size > before && frames.last().host == "pages") {
                during++
                subsMoving = below.subscriptions
                compsMoving = below.compositions
            }
        }
        assertTrue("the pop moved ($during frames)", during > 3)
        // Before the fix Navigation raised the revealed entry to STARTED at the commit: its flow was collected again
        // and it recomposed while it moved.
        assertEquals("no subscription while the page moves", s0, subsMoving)
        assertEquals("no composition while the page moves", c0, compsMoving)
        settle()
        assertEquals("one resume after the settle", s0 + 1, below.subscriptions)
    }

    @Test
    fun `H2 and R3-7 - a back gesture's start and first move frames measure nothing of the page below (strict)`() {
        showStack()
        compose.runOnUiThread { nav!!.nbPush(R6Page(1)) }
        settle()
        compose.runOnUiThread { nav!!.nbPush(R6Page(2)) }
        settle()
        val below = counts.getValue(1)
        val twoBelow = counts.getValue(0)
        val m0 = below.measures
        val c0 = below.compositions
        val t0 = twoBelow.compositions
        val twoAlive = twoBelow.alive
        val w =
            activity.resources.displayMetrics.widthPixels
                .toFloat()
        val back = activity.onBackPressedDispatcher
        compose.runOnUiThread { back.dispatchOnBackStarted(BackEventCompat(0f, TOUCH_Y, 0f, BackEventCompat.EDGE_LEFT)) }
        frame()
        compose.runOnUiThread { back.dispatchOnBackProgressed(BackEventCompat(0.1f * w, TOUCH_Y, 0.1f, BackEventCompat.EDGE_LEFT)) }
        frame()
        compose.runOnUiThread { back.dispatchOnBackProgressed(BackEventCompat(0.2f * w, TOUCH_Y, 0.2f, BackEventCompat.EDGE_LEFT)) }
        frame()
        // Before the fix the page two below was composed in the gesture's first frame (round 5: 0.2 to 0.9 s on
        // Metric) and the page below was measured again.
        assertEquals("zero measure passes of the page below", m0, below.measures)
        assertEquals("zero compositions of the page below", c0, below.compositions)
        // Round 7: only the page below is parked; the page two below is not composed at rest, and the gesture does
        // not compose it either (its pair is the top and the page below).
        assertFalse("the page two below is not parked at rest (round 7)", twoAlive)
        assertFalse("nor composed by the gesture", twoBelow.alive)
        assertEquals("zero compositions of the page two below", t0, twoBelow.compositions)
        compose.runOnUiThread { back.dispatchOnBackCancelled() }
        settle()
    }

    @Test
    fun `M1 - a tap on the entering page during a push pushes from it at once, there is no lifecycle gate`() {
        showStack()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { nav!!.nbPush(R6Page(1)) }
        repeat(4) { frame() }
        // Mid-push: a tap on the entering page opens the next one in the same input event.
        compose.runOnUiThread { nav!!.nbPush(R6Page(2)) }
        frame()
        assertEquals("the next page is pushed at once", 3, nav!!.currentBackStack.value.count { it.destination.route != null })
        val last = frames.last { it.host == "pages" }
        assertTrue("the motion heads to the new page", last.p > 0f)
        settle()
    }

    @Test
    fun `M4 - a Back its caller does not follow is reported, a caller that follows 100 ms late is not, and nothing flickers`() {
        val reports = CopyOnWriteArrayList<String>()
        NbMotionProbe.unfollowedBack = { host, _, _ -> reports += host }
        var stack by mutableStateOf(listOf("a", "b"))
        var follow = true
        var late: (() -> Unit)? = null
        show {
            router = LocalNbBackRouter.current
            NbStackHost(stack, key = { it }, onBack = { if (follow) late = { stack = stack.dropLast(1) } }, id = "s") {
                Box(Modifier.fillMaxSize())
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread { router!!.back() }
        repeat(6) { frame() }
        compose.runOnUiThread { late?.invoke() }
        settle()
        assertEquals("a late caller is no unfollowed Back", emptyList<String>(), reports)
        assertEquals(listOf("a"), stack)
        // A caller that does nothing.
        stack = listOf("a", "b")
        settle()
        follow = false
        compose.runOnUiThread { router!!.back() }
        settle()
        assertEquals("reported", listOf("s"), reports)
    }

    @Test
    fun `measurement 5 and L1 - a Back key sent as a sequence runs the app Back, a Back nothing takes goes to the system`() {
        val r = NbBackRouter()
        var backs = 0
        var starts = 0
        val target =
            object : NbBackTarget {
                override val depth = 1

                override fun enabled() = true

                override fun start(
                    edge: Int,
                    touchX: Float,
                ) {
                    starts++
                }

                override fun progress(move: NbBackMove) = Unit

                override fun cancel() = Unit

                override fun commit() = Unit

                override fun back() {
                    backs++
                }
            }
        r.add(target)
        val dispatcher = OnBackPressedDispatcher()
        var system = 0
        dispatcher.addCallback(
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    system++
                }
            },
        )
        val root = RootBackCallback(r, dispatcher) { false }
        root.isEnabled = true
        dispatcher.addCallback(root)
        // A 3-button Back key as a back sequence: no swipe edge, synthetic progress.
        dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_NONE))
        dispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, 0.4f, BackEventCompat.EDGE_NONE))
        dispatcher.onBackPressed()
        assertEquals("the key does not drive a finger (before: the platform's progress was followed)", 0, starts)
        assertEquals("the key runs the app's Back", 1, backs)
        // L1: the target is gone before the handler saw it: the Back reaches the system.
        r.remove(target)
        dispatcher.onBackPressed()
        assertEquals("the system's Back ran (before: swallowed)", 1, system)
        assertFalse(r.hasTarget)
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val MOTION_FRAMES = 40
        const val TOUCH_Y = 900f
    }
}
