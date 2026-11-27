package io.github.alien2003.nightbaseline.ui.material

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import io.github.alien2003.nightbaseline.ui.components.SettingsRow
import io.github.alien2003.nightbaseline.ui.history.HistoryActions
import io.github.alien2003.nightbaseline.ui.history.HistoryFilter
import io.github.alien2003.nightbaseline.ui.history.HistorySamples
import io.github.alien2003.nightbaseline.ui.history.HistoryScreen
import io.github.alien2003.nightbaseline.ui.interaction.PressAnimationProbe
import io.github.alien2003.nightbaseline.ui.interaction.nbClickable
import io.github.alien2003.nightbaseline.ui.layout.LayoutInputs
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import io.github.alien2003.nightbaseline.ui.theme.NightBaselineTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * No element stays pressed (`tokens.md` 2.8): the pressed highlight of [nbClickable] always clears, whatever ends the
 * press. Checked on what the rows actually draw ([PressAnimationProbe.litElements]):
 *
 * - a quick tap in a scrolling screen: the clickable emits the press and its release together, so the release arrives
 *   before the press highlight's first frame (0.1.0 skipped it there and the highlight ran on to full and stayed);
 * - that tap on the Settings "Health Connect permissions" row, whose click starts another activity, then pause /
 *   stop / resume;
 * - quick and fast alternating taps on the History filter tabs, each recomposing the list;
 * - a held press that is cancelled, or whose window loses focus, or whose activity pauses.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "en-rUS-w360dp-h780dp-port-420dpi")
class PressClearTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var controller: ActivityController<ComponentActivity>
    private val activity: ComponentActivity get() = controller.get()

    @Before
    fun trackLit() {
        PressAnimationProbe.trackLit = true
        PressAnimationProbe.resetLit()
    }

    @After
    fun stopTracking() {
        PressAnimationProbe.trackLit = false
        PressAnimationProbe.resetLit()
    }

    @Test
    fun tapThatStartsAnActivityLeavesNoPressedRow() {
        show { SettingsScreenRow(onClick = { activity.startActivity(Intent(activity, ComponentActivity::class.java)) }) }
        quickTap(ROW)
        // P1: the click runs after the frame that shows the press (two frames after the lift).
        frames(FRAME_MS * 2)
        assertNotNull("the click starts the other activity", shadowOf(activity).nextStartedActivity)
        // The other activity covers this one right away.
        controller.windowFocusChanged(false)
        controller.pause()
        frames(FRAME_MS)
        controller.stop()
        frames(SETTLE_MS)
        controller.restart().start().resume()
        controller.windowFocusChanged(true)
        frames(SETTLE_MS)
        assertNothingLit("after coming back from the other activity")
    }

    @Test
    fun quickTapInScrollingScreenClears() {
        show { SettingsScreenRow(onClick = {}) }
        repeat(TAPS) {
            // Each tap on a settled row: its press and release arrive before the highlight's first frame.
            quickTap(ROW)
            frames(SETTLE_MS)
            assertNothingLit("after quick tap ${it + 1}")
        }
    }

    @Test
    fun fastAlternatingHistoryFilterTabsClear() {
        var filter by mutableStateOf(HistoryFilter.All)
        val layout = resolveLayout(LayoutInputs(WIDTH_DP, HEIGHT_DP))
        show {
            HistoryScreen(
                HistorySamples.filtered(filter),
                layout,
                actions = HistoryActions(onFilter = { filter = it }),
            )
        }
        // "All" -> "Moved" -> "Learning and gaps" within a few frames, each change regrouping the list.
        quickTap(MOVED)
        frames(BETWEEN_TAPS_MS)
        quickTap(GAPS)
        frames(SETTLE_MS)
        assertEquals(HistoryFilter.Gaps, filter)
        assertNothingLit("after a quick tap on each of two tabs")
        // Then fast alternating taps, the next one landing while the previous highlight still moves.
        repeat(TAPS) { i ->
            quickTap(if (i % 2 == 0) MOVED else GAPS)
            frames(BETWEEN_TAPS_MS)
        }
        frames(SETTLE_MS)
        assertEquals(HistoryFilter.Moved, filter)
        assertNothingLit("after fast taps between the filter tabs")
    }

    @Test
    fun cancelledPressClears() {
        show { SettingsScreenRow(onClick = {}) }
        holdRow()
        compose.onNodeWithText(ROW).performTouchInput { cancel() }
        frames(SETTLE_MS)
        assertNothingLit("after a cancelled press")
    }

    @Test
    fun heldPressClearsWhenTheWindowLosesFocus() {
        show { SettingsScreenRow(onClick = {}) }
        holdRow()
        controller.windowFocusChanged(false)
        frames(SETTLE_MS)
        assertNothingLit("while the window has no focus")
        controller.windowFocusChanged(true)
        compose.onNodeWithText(ROW).performTouchInput { up() }
        frames(SETTLE_MS)
        assertNothingLit("after the finger is lifted")
    }

    @Test
    fun heldPressClearsWhenTheActivityPauses() {
        show { SettingsScreenRow(onClick = {}) }
        holdRow()
        controller.pause()
        frames(SETTLE_MS)
        controller.resume()
        frames(FRAME_MS)
        // The finger is still down: the pause alone ended the pressed look.
        assertNothingLit("after a pause")
        compose.onNodeWithText(ROW).performTouchInput { up() }
        frames(SETTLE_MS)
        assertNothingLit("after resume and release")
    }

    @Composable
    private fun SettingsScreenRow(onClick: () -> Unit) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SettingsRow(ROW, onClick = onClick)
            SettingsRow("Set up again", onClick = {}, showDivider = true)
        }
    }

    /**
     * A tap shorter than the tap-indication delay of a scrolling container (the Settings list, the History feed): the
     * clickable emits the press and its release together once the finger is up, as a quick tap on the phone does.
     */
    private fun quickTap(text: String) {
        compose.onNodeWithText(text).performTouchInput {
            down(center)
            up()
        }
    }

    /** Holds a finger on the row until its highlight is fully drawn (checks that the probe sees it). */
    private fun holdRow() {
        compose.onNodeWithText(ROW).performTouchInput { down(center) }
        frames(SETTLE_MS)
        assertEquals("the held row draws its pressed highlight", 1, litNow())
    }

    private fun show(content: @Composable () -> Unit) {
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        controller.windowFocusChanged(true)
        activity.setContent {
            NightBaselineTheme(darkTheme = false, effects = EffectsDecision.ForcedSolid) { content() }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        frames(SETTLE_MS)
    }

    private fun frames(ms: Long) {
        compose.mainClock.advanceTimeBy(ms)
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Draws the window once more and returns how many elements drew a pressed look. */
    private fun litNow(): Int {
        compose.onRoot().captureToImage()
        return PressAnimationProbe.litElements
    }

    private fun assertNothingLit(moment: String) = assertEquals("elements still drawn pressed $moment", 0, litNow())

    private companion object {
        const val ROW = "Health Connect permissions"
        const val MOVED = "Moved"
        const val GAPS = "Learning and gaps"
        const val TAPS = 5
        const val FRAME_MS = 16L
        const val BETWEEN_TAPS_MS = 48L
        const val SETTLE_MS = 1_500L
        const val WIDTH_DP = 360f
        const val HEIGHT_DP = 780f
    }
}
