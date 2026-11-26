package io.github.alien2003.nightbaseline.ui.components

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.ui.material.EffectsDecision
import io.github.alien2003.nightbaseline.ui.theme.NightBaselineTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The [ModalHost] state machine on its own: closing always animates and ends with nothing composed, rapid
 * open / close / re-open never leaves a stuck overlay, an option chosen while the panel is still moving closes it once,
 * the scrim covers (and takes the touches of) a bar drawn before it, and the panel never moves above its top.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w412dp-h915dp-port-420dpi")
class ModalHostTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private var open by mutableStateOf<String?>(null)
    private var underneath by mutableIntStateOf(0)
    private var bar by mutableIntStateOf(0)
    private var dismissed = 0

    private fun setUp() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            NightBaselineTheme(darkTheme = false, effects = EffectsDecision.ForcedSolid) {
                Box(Modifier.fillMaxSize()) {
                    NbTextButton("Underneath", onClick = { underneath++ }, modifier = Modifier.align(Alignment.TopCenter))
                    // A floating bar composed before the host, like the shell's.
                    NbTextButton(
                        "Bar",
                        onClick = { bar++ },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(64.dp),
                    )
                    ModalHost(open, onDismiss = {
                        dismissed++
                        open = null
                    }) { value ->
                        GlassSheet(value) {
                            NbTextButton("Choose", onClick = { dismiss() })
                            repeat(OPTIONS) { NbText("Line $it") }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Sets the host's value and publishes it, so a manually driven clock sees it on the next frame. */
    private fun show(value: String?) =
        compose.runOnIdle {
            open = value
            Snapshot.sendApplyNotifications()
        }

    private fun count(tag: String) = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    private fun assertGone() {
        compose.onAllNodesWithTag(MODAL_PANEL_TAG).assertCountEquals(0)
        compose.onAllNodesWithTag(MODAL_SCRIM_TAG).assertCountEquals(0)
    }

    @Test
    fun closingAnimatesThenNothingIsLeft() {
        setUp()
        show("Sheet")
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        show(null)
        compose.mainClock.advanceTimeBy(FRAME * 2)
        assertEquals("the panel is still leaving", 1, count(MODAL_PANEL_TAG))
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertGone()
        compose.onNodeWithText("Underneath").performClick()
        compose.runOnIdle { assertEquals(1, underneath) }
    }

    @Test
    fun rapidOpenCloseReopenNeverSticks() {
        setUp()
        compose.mainClock.autoAdvance = false
        repeat(TOGGLES) { i ->
            show(if (i % 2 == 0) "Sheet" else null)
            compose.mainClock.advanceTimeBy(FRAME * (1 + i % 3))
        }
        // Even count: the last toggle closed it.
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertGone()
        // Close then re-open within one frame: it ends open and at rest.
        compose.mainClock.autoAdvance = false
        show("Sheet")
        compose.mainClock.advanceTimeBy(FRAME * 3)
        show(null)
        compose.mainClock.advanceTimeBy(FRAME)
        show("Sheet")
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(1, count(MODAL_PANEL_TAG))
        compose.onNodeWithText("Choose").performClick()
        compose.waitForIdle()
        assertGone()
    }

    @Test
    fun optionWhileOpeningClosesOnce() {
        setUp()
        compose.mainClock.autoAdvance = false
        show("Sheet")
        compose.mainClock.advanceTimeBy(FRAME * 5)
        compose.onNodeWithText("Choose").performClick()
        compose.mainClock.advanceTimeBy(FRAME)
        // Touches on the leaving panel are swallowed: no second dismissal.
        compose.onNodeWithTag(MODAL_PANEL_TAG).performTouchInput { click(center) }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertGone()
        assertEquals(1, dismissed)
    }

    @Test
    fun theBarIsBeneathTheScrim() {
        setUp()
        show("Sheet")
        compose.waitForIdle()
        val scrim = compose.onNodeWithTag(MODAL_SCRIM_TAG).getBoundsInRoot()
        val barBounds = compose.onNodeWithText("Bar").getBoundsInRoot()
        assertTrue("the scrim covers the bar", scrim.top <= barBounds.top && scrim.bottom >= barBounds.bottom)
        // A touch above the panel lands on the scrim, never on the content behind it.
        compose.onNodeWithTag(MODAL_SCRIM_TAG).performTouchInput { click(Offset(centerX, 4f)) }
        compose.waitForIdle()
        assertGone()
        compose.runOnIdle {
            assertEquals(0, bar)
            assertEquals(0, underneath)
        }
        compose.onNodeWithText("Bar").performClick()
        compose.runOnIdle { assertEquals(1, bar) }
    }

    @Test
    fun thePanelNeverMovesAboveItsTop() {
        setUp()
        show("Sheet")
        compose.waitForIdle()
        val rest = compose.onNodeWithTag(MODAL_PANEL_TAG).getBoundsInRoot().top
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(MODAL_PANEL_TAG).performTouchInput {
            swipe(Offset(centerX, top + 12f), Offset(centerX, top - 600f), durationMillis = 300)
        }
        compose.mainClock.advanceTimeBy(FRAME)
        val during = compose.onNodeWithTag(MODAL_PANEL_TAG).getBoundsInRoot().top
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("panel top $during above its rest $rest", during >= rest)
        assertEquals(rest, compose.onNodeWithTag(MODAL_PANEL_TAG).getBoundsInRoot().top)
    }

    private companion object {
        const val FRAME = 16L
        const val TOGGLES = 8
        const val OPTIONS = 3
    }
}
