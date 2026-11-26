package io.github.alien2003.nightbaseline.ui.material

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import io.github.alien2003.nightbaseline.ui.components.GroupPosition
import io.github.alien2003.nightbaseline.ui.components.NightRow
import io.github.alien2003.nightbaseline.ui.model.NightState
import io.github.alien2003.nightbaseline.ui.sample.SampleData
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.NightBaselineTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The keyboard focus ring of rows inside a clipping card (`tokens.md` 10): drawn inside the row, so all four sides
 * are visible (an outside ring on a row as large as its card's clip was cut off completely).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "en-rUS-w360dp-h780dp-port-xhdpi")
class FocusRingInsideTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun singleRowShowsTheRingOnAllSides() = assertRingOnAllSides(GroupPosition.Single)

    @Test
    fun middleRowShowsTheRingOnAllSides() = assertRingOnAllSides(GroupPosition.Middle)

    @Test
    fun lastRowShowsTheRingOnAllSides() = assertRingOnAllSides(GroupPosition.Last)

    private fun assertRingOnAllSides(position: GroupPosition) {
        run {
            var ring = Color.Unspecified
            show {
                ring = NbTheme.colors.focusRing
                Box(Modifier.padding(16.dp).width(ROW_DP.dp).testTag(TAG)) {
                    NightRow(SampleData.night(NightState.AllWithin), onClick = {}, forced = ForcedInteraction.Focus, position = position)
                }
            }
            val image = compose.onNodeWithTag(TAG).captureToImage().toPixelMap()
            val band = (RING_MIDDLE_DP * DENSITY).toInt()
            val midX = image.width / 2
            val midY = image.height / 2
            val sides =
                mapOf(
                    "start" to image[band, midY],
                    "end" to image[image.width - 1 - band, midY],
                    "top" to image[midX, band],
                    "bottom" to image[midX, image.height - 1 - band],
                )
            sides.forEach { (side, color) ->
                assertTrue("$position: the ring shows at the $side ($color, ring $ring)", close(color, ring))
            }
        }
    }

    private fun show(content: @Composable () -> Unit) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent { NightBaselineTheme(darkTheme = false, effects = EffectsDecision.ForcedSolid) { content() } }
        compose.waitForIdle()
    }

    private fun close(
        a: Color,
        b: Color,
    ): Boolean = abs(a.red - b.red) < TOLERANCE && abs(a.green - b.green) < TOLERANCE && abs(a.blue - b.blue) < TOLERANCE

    private companion object {
        const val TAG = "row"
        const val ROW_DP = 300
        const val DENSITY = 2f

        /** The middle of the 2 dp ring drawn 2 dp inside the row's edge. */
        const val RING_MIDDLE_DP = 3f
        const val TOLERANCE = 0.08f
    }
}
