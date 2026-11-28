package io.github.alien2003.nightbaseline.ui.interaction

import android.app.Application
import android.os.Looper
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.ui.material.EffectsDecision
import io.github.alien2003.nightbaseline.ui.theme.NightBaselineTheme
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * The second round of the framework (ADR 0011 R7, R8 and the review of round 2), each test written against the
 * defect it fixes and failing without the fix: a change waits for its entering content (R1-2), parked layers are
 * placed and drawn ahead (R1-3, sections too), a sheet opens once measured (R1-5), a modal of a hidden branch takes no
 * Back and an open modal takes the step keys, a modal follows the finger's travel, reduced motion is one light rule,
 * the touch light is in the press model, a batched move-and-lift is no tap, and a step swipe hands its velocity to
 * its Pager change.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w412dp-h915dp-port-420dpi")
class FrameworkRound2Test {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var controller: ActivityController<ComponentActivity>? = null
    private val activity: ComponentActivity get() = controller!!.get()
    private val frames = CopyOnWriteArrayList<NbFrame>()
    private val parked = CopyOnWriteArrayList<Pair<String, Any>>()

    @Before
    fun setUp() {
        NbMotionProbe.frames = { frames += it }
        NbMotionProbe.parked = { host, key -> parked += host to key }
        NbClock.nowNanos = { compose.mainClock.currentTime * NANOS_PER_MS }
    }

    @After
    fun tearDown() {
        NbMotionProbe.frames = null
        NbMotionProbe.parked = null
        NbMotionProbe.press = null
        NbClock.nowNanos = System::nanoTime
    }

    private fun show(
        reduced: Boolean? = null,
        content: @Composable () -> Unit,
    ) {
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity.setContent {
            NightBaselineTheme(darkTheme = true, effects = EffectsDecision.ForcedSolid, reducedMotion = reduced) { content() }
        }
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

    /** The Nav spring from rest at 0 towards 1 after [t] seconds (closed form, overdamped). */
    private fun spring(t: Double): Double {
        val z = NbPhysics.Nav.damping.toDouble()
        val w = sqrt(NbPhysics.Nav.stiffness.toDouble())
        val r1 = -w * (z - sqrt(z * z - 1))
        val r2 = -w * (z + sqrt(z * z - 1))
        val c2 = (-r1 * -1.0) / (r2 - r1)
        val c1 = -1.0 - c2
        return 1.0 + c1 * exp(r1 * t) + c2 * exp(r2 * t)
    }

    private fun moving(host: String): List<NbFrame> = frames.filter { it.host == host && it.p > 0f && it.p < 1f }

    @Test
    fun `a stack's page below and a section host's visited sections are placed and drawn ahead (R1-3)`() {
        var selected by mutableStateOf(0)
        show {
            Column {
                Box(Modifier.size(200.dp)) {
                    NbStackHost(listOf("a", "b"), key = { it }, onBack = {}, id = "s") { Box(Modifier.fillMaxSize()) }
                }
                Box(Modifier.size(200.dp)) {
                    NbSectionHost(selected, listOf(0, 1, 2), onBackToFirst = { selected = 0 }, id = "sec") { _, _ ->
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
        // The parked probe runs in the layer's draw (V9); Robolectric draws only on request.
        drawWindow()
        assertTrue("the page below the top has its layer ahead: $parked", parked.contains("s" to "a"))
        selected = 1
        idle()
        parked.clear()
        selected = 2
        idle()
        drawWindow()
        // Both visited sections not shown are parked: the gesture back to the first and a tap to either start on an
        // existing layer (the section host used to measure only the layers of the running change).
        assertTrue("visited sections are parked: $parked", parked.contains("sec" to 0) && parked.contains("sec" to 1))
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
    fun `R8 - a sheet opens once its panel is measured, on the spring's first frames (R1-5)`() {
        var value by mutableStateOf<String?>(null)
        var ready by mutableStateOf(false)
        show {
            ModalHost(value, onDismiss = { value = null }) {
                Box(Modifier.modalPanel(state).fillMaxWidth().height(if (ready) 300.dp else 0.dp))
            }
        }
        compose.mainClock.autoAdvance = false
        frames.clear()
        value = "x"
        repeat(WAIT_FRAMES) { frame() }
        assertTrue("no motion while the panel has no height: ${moving("modal").map { it.p }}", moving("modal").isEmpty())
        ready = true
        repeat(MOTION_FRAMES) { frame() }
        val p = moving("modal").map { it.p }.distinct()
        assertTrue("the sheet opens: $p", p.size >= 3)
        assertTrue("first moving frame ${p[0]} is an early frame of the spring", p[0] <= spring(2 * FRAME_S))
        compose.mainClock.autoAdvance = true
        idle()
    }

    @Test
    fun `a modal left open in a page below takes no Back, and an open modal takes the step keys`() {
        var stack by mutableStateOf(listOf("a", "b"))
        var dismissed = 0
        var stepped = 0
        var router: NbBackRouter? = null
        show {
            router = LocalNbBackRouter.current
            NbStackHost(stack, key = { it }, onBack = { stack = stack.dropLast(1) }, id = "s") { page ->
                Box(Modifier.fillMaxSize()) {
                    if (page == "a") {
                        NbStepKeys(previous = { stepped++ }, next = { stepped++ })
                        ModalHost("open", onDismiss = { dismissed++ }) { Box(Modifier.modalPanel(state).fillMaxWidth().height(200.dp)) }
                    }
                }
            }
        }
        // Page b is shown; the modal of page a (parked below it) must not take Back (review: it did, as its target
        // ignored its branch).
        assertFalse("no modal of a hidden branch is open", router!!.modalOpen())
        compose.runOnIdle { router!!.back() }
        idle()
        assertEquals("Back popped the shown page", listOf("a"), stack)
        assertEquals("the hidden modal was not dismissed", 0, dismissed)
        // Page a is shown with its modal open: the modal takes Back and the step keys reach nothing behind it.
        assertTrue(router!!.modalOpen())
        compose.runOnIdle { assertFalse(router!!.key(NbKeyIntent.Move(1, 0))) }
        assertEquals("the arrows do not step the page behind an open modal", 0, stepped)
        compose.runOnIdle { router!!.back() }
        idle()
        assertEquals("Back closes the modal", 1, dismissed)
    }

    @Test
    fun `a back gesture moves a sheet by the finger's travel, not by the platform's shaped progress`() {
        var value by mutableStateOf<String?>("x")
        show {
            ModalHost(value, onDismiss = { value = null }) { Box(Modifier.modalPanel(state).fillMaxWidth().height(300.dp)) }
        }
        val width =
            activity.resources.displayMetrics.widthPixels
                .toFloat()
        val back = activity.onBackPressedDispatcher
        back.dispatchOnBackStarted(BackEventCompat(0f, TOUCH_Y, 0f, BackEventCompat.EDGE_LEFT))
        idle()
        for (share in floatArrayOf(0.2f, 0.4f, 0.6f)) {
            frames.clear()
            // Android 14+ shapes the progress; here progress = share^2 while the finger moves linearly.
            back.dispatchOnBackProgressed(BackEventCompat(share * width, TOUCH_Y, share * share, BackEventCompat.EDGE_LEFT))
            idle()
            val p = frames.last { it.host == "modal" }.p
            assertEquals("the sheet follows the finger at $share", 1f - share, p, 1e-3f)
        }
        back.dispatchOnBackCancelled()
        idle()
    }

    @Test
    fun `reduced motion - every press light switches to the same full light and off, without a tween`() {
        val samples = ConcurrentHashMap<Any, CopyOnWriteArrayList<Float>>()
        NbMotionProbe.press =
            { element, sample, v -> if (sample == NbPressSample.Glow) samples.getOrPut(element) { CopyOnWriteArrayList() } += v }
        val a = MutableInteractionSource()
        val b = MutableInteractionSource()
        val c = MutableInteractionSource()
        var shell: ShellPressState? = null
        show(reduced = true) {
            Column {
                Box(Modifier.size(200.dp, 56.dp).nbClickable(onClick = {}, shape = RectangleShape, interactionSource = a, glow = true))
                val state = rememberShellPressState()
                shell = state
                Box(
                    Modifier
                        .size(200.dp, 56.dp)
                        .shellPressInput(state)
                        .shellPressScale(state)
                        .shellPressGlow(state, RectangleShape),
                )
                FollowPresses(state, b)
                rememberNbTouchLight(c, forced = false)
            }
        }
        val presses = listOf(a, b, c).map { PressInteraction.Press(Offset(5f, 5f)) }
        compose.runOnIdle { listOf(a, b, c).zip(presses).forEach { (s, p) -> s.tryEmit(p) } }
        idle()
        assertEquals("three pressables light up: $samples", 3, samples.size)
        samples.forEach { (e, v) -> assertEquals("$e lights fully at once", listOf(1f), v.toList()) }
        compose.runOnIdle { listOf(a, b, c).zip(presses).forEach { (s, p) -> s.tryEmit(PressInteraction.Release(p)) } }
        idle()
        samples.forEach { (e, v) -> assertEquals("$e goes off at once", listOf(1f, 0f), v.toList()) }
        assertTrue(shell != null)
    }

    @Test
    fun `the primary button's touch light is in the press model - its frames are reported and the app leaving clears it`() {
        val samples = CopyOnWriteArrayList<Float>()
        val source = MutableInteractionSource()
        var light: NbTouchLight? = null
        NbMotionProbe.press = { element, sample, v -> if (element === light && sample == NbPressSample.Glow) samples += v }
        show { light = rememberNbTouchLight(source, forced = false) }
        compose.runOnIdle { source.tryEmit(PressInteraction.Press(Offset(5f, 5f))) }
        idle()
        assertTrue("the light's frames are reported: $samples", samples.isNotEmpty())
        assertEquals(1f, light!!.alpha, 1e-3f)
        // The app leaves the foreground while the finger is still down: no button comes back lit.
        controller!!.pause()
        idle()
        assertEquals("the light is out after the pause", 0f, light!!.alpha, 1e-3f)
    }

    @Test
    fun `a move and the lift in one event beyond the touch slop is not a tap`() {
        var clicks = 0
        show { Box(Modifier.size(200.dp).testTag("t").nbClickable(onClick = { clicks++ }, shape = RectangleShape)) }
        compose.onNodeWithTag("t").performTouchInput {
            down(center)
            // The pointer moves without an event of its own: the up carries the move (a batched sample).
            updatePointerTo(0, center + Offset(viewConfiguration.touchSlop * 4f, 0f))
            up()
        }
        idle()
        assertEquals("a swipe never becomes a tap, even when its only far sample is the lift", 0, clicks)
        compose.onNodeWithTag("t").performTouchInput {
            down(center)
            up()
        }
        idle()
        assertEquals("a real tap still clicks", 1, clicks)
    }

    @Test
    fun `a step swipe hands its release velocity to the Pager change it makes`() {
        var value by mutableStateOf(10)
        show {
            NbSwitcher(value, key = { it }, modifier = Modifier.fillMaxSize(), id = "pager", order = naturalOrder()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .testTag("pager-$it")
                        .nbStepArea(onPrevious = { value -= 1 }, onNext = { value += 1 }),
                )
            }
        }
        // A step by a button (no velocity): the reference curve.
        compose.mainClock.autoAdvance = false
        frames.clear()
        compose.runOnIdle { value = 11 }
        repeat(MOTION_FRAMES) { frame() }
        val button = moving("pager").map { it.p }.distinct()
        compose.mainClock.autoAdvance = true
        idle()
        // A fast swipe towards the start edge: next. Its change starts with the finger's speed.
        frames.clear()
        compose.onNodeWithTag("pager-11").performTouchInput { swipe(Offset(width * 0.8f, centerY), Offset(width * 0.2f, centerY), 120) }
        compose.mainClock.autoAdvance = false
        repeat(MOTION_FRAMES) { frame() }
        val swipe = moving("pager").map { it.p }.distinct()
        compose.mainClock.autoAdvance = true
        idle()
        assertEquals(12, value)
        assertTrue("both run: $button / $swipe", button.size >= 2 && swipe.size >= 2)
        assertTrue("the swipe's step starts faster than a button's (${swipe[0]} vs ${button[0]})", swipe[0] > button[0] + 0.02f)
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val FRAME_S = 0.016
        const val WAIT_FRAMES = 6
        const val MOTION_FRAMES = 6
        const val TOUCH_Y = 900f
    }
}
