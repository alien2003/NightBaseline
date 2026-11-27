package io.github.alien2003.nightbaseline.ui.interaction

import android.app.Application
import android.os.Looper
import android.view.KeyEvent
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.ui.material.EffectsDecision
import io.github.alien2003.nightbaseline.ui.theme.NightBaselineTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/**
 * The framework's hosts, press model and key path on their own (ADR 0011), each test written against a defect of the
 * previous round: the bar of a leaving section (review 1), back targets of a leaving swap layer (review 12), one
 * press model (review 6), a swipe that became a tap (V3), the platform's pointer-stop rule behind the emulator's
 * dropped flings (V2), and the key decoder (review 9, DPAD / Page keys).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w412dp-h915dp-port-420dpi")
class FrameworkHostsTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var activity: ComponentActivity? = null

    @After
    fun tearDown() {
        NbMotionProbe.press = null
        NbMotionProbe.flings = null
    }

    private fun show(content: @Composable () -> Unit) {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity!!.setContent { NightBaselineTheme(darkTheme = true, effects = EffectsDecision.ForcedSolid) { content() } }
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

    @Test
    fun `every section's bar shows the host's target from the first frame of a change, in both directions`() {
        var selected by mutableStateOf(0)
        val shown = ConcurrentHashMap<Int, Int>()
        show {
            NbSectionHost(selected, listOf(0, 1, 2), onBackToFirst = { selected = 0 }) { section, _ ->
                val s = nbShownSection(section)
                SideEffect { shown[section] = s }
                Box(Modifier.fillMaxSize())
            }
        }
        for (s in listOf(1, 2)) {
            selected = s
            idle()
        }
        for (target in listOf(0, 2, 1, 0)) {
            compose.mainClock.autoAdvance = false
            selected = target
            frame()
            // Down the bar order the leaving section is the upper layer: its bar must not show the old section.
            assertEquals("every layer's bar shows $target (review 1)", setOf(target), shown.values.toSet())
            compose.mainClock.autoAdvance = true
            idle()
        }
    }

    @Test
    fun `a leaving swap layer is not a back target`() {
        var value by mutableStateOf("a")
        var router: NbBackRouter? = null
        show {
            router = LocalNbBackRouter.current
            NbSwitcher(value, key = { it }, id = "swap") { v ->
                // Each layer holds a stack of two pages: it can go back while its layer is the shown one.
                NbStackHost(listOf("$v-1", "$v-2"), key = { it }, onBack = {}, id = "stack-$v") { Box(Modifier.fillMaxSize()) }
            }
        }
        assertEquals(1, router!!.enabledTargets())
        compose.mainClock.autoAdvance = false
        value = "b"
        frame()
        frame()
        assertEquals("only the shown layer's stack takes Back during the swap (review 12)", 1, router!!.enabledTargets())
        compose.mainClock.autoAdvance = true
        idle()
        assertEquals(1, router!!.enabledTargets())
    }

    @Test
    fun `one press model - a clickable and a shell control of the same size press on the same curves`() {
        val samples = ConcurrentHashMap<Any, CopyOnWriteArrayList<Pair<NbPressSample, Float>>>()
        NbMotionProbe.press = { element, sample, v -> samples.getOrPut(element) { CopyOnWriteArrayList() } += sample to v }
        val clickSource = MutableInteractionSource()
        var shell: ShellPressState? = null
        show {
            Column {
                Box(
                    Modifier
                        .size(
                            WIDTH,
                            HEIGHT,
                        ).nbClickable(onClick = {}, shape = RectangleShape, interactionSource = clickSource, glow = true),
                )
                val state = rememberShellPressState()
                shell = state
                val scale = with(LocalDensity.current) { nbPressScale(IntSize(WIDTH.roundToPx(), HEIGHT.roundToPx()), this) }
                Box(
                    Modifier
                        .size(WIDTH, HEIGHT)
                        .shellPressInput(state, pressedScale = scale)
                        .shellPressScale(state)
                        .shellPressGlow(state, RectangleShape),
                )
            }
        }
        compose.mainClock.autoAdvance = false
        samples.clear()
        val a = PressInteraction.Press(Offset(10f, 10f))
        val b = PressInteraction.Press(Offset(10f, 10f))
        clickSource.tryEmit(a)
        shell!!.source.tryEmit(b)
        repeat(PRESS_FRAMES) { frame() }
        clickSource.tryEmit(PressInteraction.Release(a))
        shell!!.source.tryEmit(PressInteraction.Release(b))
        repeat(RELEASE_FRAMES) { frame() }
        compose.mainClock.autoAdvance = true
        idle()

        fun curve(
            element: Any?,
            sample: NbPressSample,
        ): List<Float> =
            samples[element].orEmpty().filter { it.first == sample }.map { it.second }.fold(mutableListOf()) { acc, v ->
                if (acc.lastOrNull() != v) acc += v
                acc
            }
        val clickable = samples.keys.first { it != shell && samples.getValue(it).any { s -> s.first == NbPressSample.Scale } }
        val feedback = samples.keys.first { it != shell && samples.getValue(it).any { s -> s.first == NbPressSample.Glow } }
        val scaleA = curve(clickable, NbPressSample.Scale)
        val scaleB = curve(shell, NbPressSample.Scale)
        assertTrue("the press moves: $scaleA", scaleA.size > PRESS_FRAMES)
        assertTrue("never above 1.0", (scaleA + scaleB).all { it <= 1f })
        assertEquals("the same scale curve (review 6: the shell used PressIn at 0.98)", scaleA.size, scaleB.size)
        scaleA.zip(scaleB).forEach { (x, y) -> assertEquals(x, y, EPS) }
        val glowA = curve(feedback, NbPressSample.Glow)
        val glowB = curve(shell, NbPressSample.Glow)
        assertEquals("the same light curve", glowA.size, glowB.size)
        glowA.zip(glowB).forEach { (x, y) -> assertEquals(x, y, EPS) }
    }

    @Test
    fun `a swipe that starts on a pressable never becomes its click, a tap does`() {
        var clicks = 0
        show {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(HEIGHT)
                    .testTag("tile")
                    .nbClickable(onClick = { clicks++ }, shape = RectangleShape),
            )
        }
        compose.onNodeWithTag("tile").performTouchInput { swipe(Offset(width * 0.2f, centerY), Offset(width * 0.8f, centerY), 150) }
        idle()
        assertEquals("a horizontal swipe inside the tile is not a click (V3)", 0, clicks)
        compose.onNodeWithTag("tile").performTouchInput { swipe(Offset(width * 0.5f, centerY), Offset(width * 0.5f + 4f, centerY), 60) }
        idle()
        assertEquals("a move within the touch slop is still a tap", 1, clicks)
        compose.onNodeWithTag("tile").performTouchInput { click() }
        idle()
        assertEquals(2, clicks)
    }

    @Test
    fun `V2 - the platform takes a pointer that pauses over 40 ms before the last sample as stopped (the emulator's drops)`() {
        val flings = CopyOnWriteArrayList<Float>()
        NbMotionProbe.flings = { _, v -> flings += v }
        show {
            Column(Modifier.fillMaxSize().testTag("list").nbVerticalScroll(rememberNbScrollState(), id = "list")) {
                repeat(ROWS) { Box(Modifier.fillMaxWidth().height(HEIGHT)) }
            }
        }

        fun flickWithLastGap(gapMs: Long): Float {
            flings.clear()
            compose.onNodeWithTag("list").performTouchInput {
                down(center)
                repeat(SAMPLES) {
                    advanceEventTime(SAMPLE_MS)
                    moveBy(Offset(0f, -STEP_PX))
                }
                // The last sample arrives after [gapMs] (adb's injector on a busy emulator: 56-59 ms measured).
                advanceEventTime(gapMs)
                moveBy(Offset(0f, -STEP_PX))
                up()
            }
            idle()
            return flings.maxOfOrNull { abs(it) } ?: 0f
        }
        val regular = flickWithLastGap(SAMPLE_MS)
        val late = flickWithLastGap(LATE_MS)
        assertTrue("an even flick flings ($regular px/s)", regular > MIN_FLING)
        assertTrue("a 56 ms gap before the last sample reads as a stopped pointer ($late px/s)", late < MIN_FLING)
    }

    @Test
    fun `the one key decoder - reading direction, pager keys, Back and the shortcuts`() {
        show { Box(Modifier) }

        fun key(
            code: Int,
            meta: Int = 0,
            rtl: Boolean = false,
        ) = nbDecodeKey(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, meta), rtl)
        assertEquals(NbKeyIntent.Move(-1, 0), key(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(NbKeyIntent.Move(1, 0), key(KeyEvent.KEYCODE_DPAD_LEFT, rtl = true))
        assertEquals(NbKeyIntent.Move(0, 1), key(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(NbKeyIntent.Page(1), key(KeyEvent.KEYCODE_PAGE_DOWN))
        assertEquals(NbKeyIntent.Page(-1), key(KeyEvent.KEYCODE_PAGE_UP))
        assertEquals(NbKeyIntent.Edge(end = true), key(KeyEvent.KEYCODE_MOVE_END))
        assertEquals(NbKeyIntent.Back, key(KeyEvent.KEYCODE_ESCAPE))
        assertEquals(NbKeyIntent.Back, key(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.META_ALT_ON))
        assertEquals(NbKeyIntent.Back, key(KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.META_ALT_ON, rtl = true))
        assertEquals(NbKeyIntent.Section(1), key(KeyEvent.KEYCODE_2, KeyEvent.META_CTRL_ON))
        assertEquals(NbKeyIntent.Command('R'), key(KeyEvent.KEYCODE_R, KeyEvent.META_CTRL_ON))
        assertEquals(null, key(KeyEvent.KEYCODE_A))
        assertEquals(1, NbKeyIntent.Page(1).stepDelta())
        assertEquals(-1, NbKeyIntent.Move(-1, 0).stepDelta())
        assertEquals(null, NbKeyIntent.Move(0, 1).stepDelta())
        assertFalse(NbKeyIntent.Back.stepDelta() != null)
    }

    private companion object {
        val WIDTH = 300.dp
        val HEIGHT = 56.dp
        const val PRESS_FRAMES = 8
        const val RELEASE_FRAMES = 40
        const val EPS = 1e-4f
        const val ROWS = 80
        const val SAMPLES = 6
        const val SAMPLE_MS = 8L
        const val LATE_MS = 56L
        const val STEP_PX = 30f
        const val MIN_FLING = 1000f
    }
}
