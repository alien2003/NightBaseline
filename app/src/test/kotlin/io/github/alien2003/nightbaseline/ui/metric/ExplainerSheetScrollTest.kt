package io.github.alien2003.nightbaseline.ui.metric

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import io.github.alien2003.nightbaseline.ui.interaction.MODAL_PANEL_TAG
import io.github.alien2003.nightbaseline.ui.interaction.ModalHost
import io.github.alien2003.nightbaseline.ui.interaction.NbMotionProbe
import io.github.alien2003.nightbaseline.ui.interaction.NbScrollContainer
import io.github.alien2003.nightbaseline.ui.layout.LayoutInputs
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import io.github.alien2003.nightbaseline.ui.material.EffectsDecision
import io.github.alien2003.nightbaseline.ui.theme.NightBaselineTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

/**
 * V2-7 on the real explainer sheet: on a phone short enough for its text to scroll, a flick that starts in the panel's
 * side margin flings its scroll container exactly as one in its middle (before the fix the container sat inside the
 * panel's padding, and a flick in the margin did nothing).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "en-rUS-w412dp-h480dp-port-420dpi", fontScale = 1.5f)
class ExplainerSheetScrollTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val flings = CopyOnWriteArrayList<Pair<String, Float>>()

    @After
    fun tearDown() {
        NbMotionProbe.flings = null
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    @Test
    fun `a flick in the sheet's side margin flings its text`() {
        NbMotionProbe.flings = { id, v -> flings += id to v }
        val layout = resolveLayout(LayoutInputs(412f, 480f, fontScale = 1.5f))
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            NightBaselineTheme(darkTheme = true, effects = EffectsDecision.ForcedSolid) {
                ModalHost("open", onDismiss = {}, kind = infoModalKind(layout)) { RangeExplainerPanel(layout, sideLine = null) }
            }
        }
        idle()
        val panel =
            compose
                .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.TestTag, MODAL_PANEL_TAG), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .single()
                .boundsInRoot
        val text =
            compose
                .onAllNodes(SemanticsMatcher.keyIsDefined(NbScrollContainer), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .single { it.config[NbScrollContainer] == "metric-9" }
        val range = text.config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("the text scrolls in this window", range.maxValue() > 0f)
        val b = text.boundsInRoot
        for ((where, x) in listOf("middle" to b.center.x, "margin" to panel.left + 6f)) {
            flings.clear()
            val y = b.top + b.height * 0.7f
            compose.onAllNodes(SemanticsMatcher("root") { it.parent == null })[0].performTouchInput {
                swipe(Offset(x, y), Offset(x, y - 300f), 100)
            }
            idle()
            val v = flings.filter { it.first == "metric-9" }.maxOfOrNull { abs(it.second) } ?: 0f
            assertTrue("a flick in the sheet's $where flings its text ($v px/s)", v >= 1000f)
        }
    }
}
