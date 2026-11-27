package io.github.alien2003.nightbaseline.ui.material

import android.app.Application
import android.os.Looper
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.RecomposeScope
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.tooling.CompositionObserver
import androidx.compose.runtime.tooling.CompositionObserverHandle
import androidx.compose.runtime.tooling.CompositionRegistrationObserver
import androidx.compose.runtime.tooling.ObservableComposition
import androidx.compose.runtime.tooling.observe
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.findViewTreeCompositionContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.ui.components.GroupPosition
import io.github.alien2003.nightbaseline.ui.components.NightRow
import io.github.alien2003.nightbaseline.ui.history.HistorySamples
import io.github.alien2003.nightbaseline.ui.interaction.PressAnimationProbe
import io.github.alien2003.nightbaseline.ui.interaction.nbClickable
import io.github.alien2003.nightbaseline.ui.theme.NightBaselineTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Guards the per-frame cost of the press feedback on list rows (the History feed is the worst case: one
 * [nbClickable] per night). Every composition of the window, including the lazy list's item subcompositions, is
 * observed through the runtime's composition observer:
 *
 * - scrolling the feed recomposes none of the rows already on screen and starts no press animation;
 * - pressing and releasing one row recomposes none of them either (highlight and shrink are draw / layer work), and
 *   only that row creates an animation: idle rows never run indication animations.
 */
@OptIn(ExperimentalComposeRuntimeApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w411dp-h891dp-port-420dpi")
class RowInteractionCostTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val shot = mutableStateOf<(@Composable () -> Unit)?>(null)

    @Test
    fun scrollAndPressDoNotRecomposeRowsOrAnimateIdleOnes() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { shot.value?.invoke() }
        idle()
        val content = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val recomposer = content.findViewTreeCompositionContext() as Recomposer
        val counter = ScopeCounter()
        val registration = recomposer.observe(counter)

        val nights = HistorySamples.feedNights
        val list = LazyListState()
        compose.runOnIdle {
            shot.value = {
                NightBaselineTheme(darkTheme = false, effects = EffectsDecision.ForcedSolid) {
                    LazyColumn(Modifier.height(LIST_HEIGHT), state = list) {
                        itemsIndexed(nights, key = { _, night -> night.wakeDate.toEpochDay() }) { i, night ->
                            NightRow(
                                night,
                                onClick = {},
                                modifier = Modifier.testTag("row$i"),
                                position = GroupPosition.of(i, nights.size),
                            )
                        }
                    }
                }
            }
        }
        idle()
        val onScreen = counter.seen.toSet()
        assertTrue("the observer sees the rows' compositions (${onScreen.size} scopes)", onScreen.size > MIN_SCOPES)
        val animationsBefore = PressAnimationProbe.animationsCreated

        // Scroll by less than one row: rows stay composed, one more enters at the bottom.
        counter.reset()
        repeat(SCROLL_STEPS) {
            compose.runOnIdle { runBlocking { list.scrollBy(SCROLL_STEP_PX) } }
            idle()
        }
        assertEquals("rows on screen recomposed while scrolling", 0, counter.reentered(onScreen))
        assertEquals("idle rows created press animations while scrolling", animationsBefore, PressAnimationProbe.animationsCreated)

        // Press and release one row of the grouped card (highlight only, no shrink).
        counter.reset()
        compose.onNodeWithTag("row1").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(PRESS_HOLD_MS)
        compose.onNodeWithTag("row1").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        idle()
        assertEquals("rows recomposed by a press", 0, counter.reentered(onScreen))
        assertEquals("only the pressed row creates its highlight", animationsBefore + 1, PressAnimationProbe.animationsCreated)

        registration.dispose()
        counter.dispose()
        activity.finish()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    /** Counts how often each recompose scope runs, across every composition the recomposer registers. */
    private class ScopeCounter :
        CompositionRegistrationObserver,
        CompositionObserver {
        val seen = mutableListOf<RecomposeScope>()
        private val entered = mutableMapOf<RecomposeScope, Int>()
        private val handles = mutableListOf<CompositionObserverHandle>()

        fun reset() = entered.clear()

        /** How many of [scopes] ran again since [reset]. */
        fun reentered(scopes: Set<RecomposeScope>): Int = entered.filterKeys { it in scopes }.values.sum()

        fun dispose() = handles.forEach { it.dispose() }

        override fun onCompositionRegistered(composition: ObservableComposition) {
            handles += composition.setObserver(this)
        }

        override fun onCompositionUnregistered(composition: ObservableComposition) = Unit

        override fun onBeginComposition(composition: ObservableComposition) = Unit

        override fun onScopeEnter(scope: RecomposeScope) {
            seen += scope
            entered[scope] = (entered[scope] ?: 0) + 1
        }

        override fun onReadInScope(
            scope: RecomposeScope,
            value: Any,
        ) = Unit

        override fun onScopeExit(scope: RecomposeScope) = Unit

        override fun onEndComposition(composition: ObservableComposition) = Unit

        override fun onScopeInvalidated(
            scope: RecomposeScope,
            value: Any?,
        ) = Unit

        override fun onScopeDisposed(scope: RecomposeScope) = Unit
    }

    private companion object {
        val LIST_HEIGHT = 400.dp
        const val MIN_SCOPES = 20
        const val SCROLL_STEPS = 8
        const val SCROLL_STEP_PX = 5f
        const val PRESS_HOLD_MS = 300L
        const val SETTLE_MS = 1_500L
    }
}
