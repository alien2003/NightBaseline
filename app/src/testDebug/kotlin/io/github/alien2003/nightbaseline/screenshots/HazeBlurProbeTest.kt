// detekt treats only src/test as tests; this is the test source set of the debug variant.
@file:Suppress("FunctionNaming", "MagicNumber")

package io.github.alien2003.nightbaseline.screenshots

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs

/**
 * Answers the open question of `layouts.md` 12: does Haze's RenderEffect blur render under Robolectric's native
 * graphics (SDK 35, hardware PixelCopy)? Hard black / white stripes are drawn as the source and a 24 dp Haze blur
 * without tint covers them; the mean difference between neighbouring pixels inside the blurred box is compared with the
 * same box unblurred. The verdict is written to `haze-blur-probe.txt` next to the screenshots and decides whether the
 * glass screenshots are meaningful (CatalogScreenshotsTest); it does (verified 2026-11-25), and the test fails if that changes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w320dp-h320dp-xhdpi")
class HazeBlurProbeTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun hazeBlurRendersUnderRobolectric() {
        val blurred = render(blur = true)
        val (left, right) = lastHalves
        val sharp = render(blur = false)
        val ratio = blurred / sharp
        // The box straddles the boundary: left quarter over stripes (blurred to mid grey), right quarter over white.
        val sampled = right - left > HazeProbe.MIN_HALF_CONTRAST
        val verdict = HazeProbe.verdict(ratio) && sampled
        val dir = File(System.getProperty(OUTPUT_PROPERTY) ?: "build/screenshots").apply { mkdirs() }
        val outcome = if (verdict) "RENDERS" else "DOES NOT RENDER"
        File(dir, "haze-blur-probe.txt").writeText(
            "Haze blur under Robolectric (SDK 35, native graphics, hardware PixelCopy): $outcome\n" +
                "edge energy blurred %.2f, unblurred %.2f, ratio %.3f (blur renders when ratio < %.2f)\n".format(
                    blurred,
                    sharp,
                    ratio,
                    HazeProbe.THRESHOLD,
                ) +
                "blurred box luminance: left (stripes) %.1f, right (white) %.1f (backdrop sampled when they differ by > %.0f)\n"
                    .format(left, right, HazeProbe.MIN_HALF_CONTRAST),
        )
        assertTrue("probe measured nothing", sharp > 0.0)
        // The glass screenshots rely on this; if it ever fails, render the solid fallback only (layouts.md 11).
        assertTrue("Haze blur no longer renders under Robolectric (ratio $ratio, halves $left / $right)", verdict)
    }

    private fun render(blur: Boolean): Double {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        activity.setContent {
            val state = rememberHazeState()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().hazeSource(state)) {
                    val stripe = 6.dp.toPx()
                    var x = 0f
                    var black = true
                    // Stripes on the left half, white on the right: a blur keeps the halves apart (the backdrop is
                    // really sampled) while smoothing the stripes; a flat fallback scrim would erase both.
                    drawRect(
                        Color.White,
                        topLeft = Offset(size.width / 2, 0f),
                        size =
                            androidx.compose.ui.geometry
                                .Size(size.width / 2, size.height),
                    )
                    while (x < size.width / 2) {
                        drawRect(
                            if (black) Color.Black else Color.White,
                            topLeft = Offset(x, 0f),
                            size =
                                androidx.compose.ui.geometry
                                    .Size(stripe, size.height),
                        )
                        x += stripe
                        black = !black
                    }
                }
                val style =
                    HazeBlurStyle {
                        blurEnabled(true)
                        blurRadius(24.dp)
                        noiseFactor(0f)
                        colorEffects(emptyList())
                    }
                Box(
                    Modifier
                        .size(160.dp)
                        .testTag(PROBE)
                        .then(if (blur) Modifier.hazeBlur(HazeInput.Sources(state), style) else Modifier),
                )
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onNodeWithTag(PROBE).captureToImage().asAndroidBitmap()
        val energy = edgeEnergy(bitmap)
        lastHalves = halves(bitmap)
        activity.finish()
        return energy
    }

    private var lastHalves = 0.0 to 0.0

    /** Mean luminance of the left and right quarter of the box. */
    private fun halves(bitmap: Bitmap): Pair<Double, Double> {
        fun mean(
            x0: Int,
            x1: Int,
        ): Double {
            var sum = 0.0
            var n = 0
            for (y in 0 until bitmap.height step 4) {
                for (x in x0 until x1) {
                    sum += lum(bitmap.getPixel(x, y))
                    n++
                }
            }
            return sum / n
        }
        return mean(0, bitmap.width / 4) to mean(bitmap.width * 3 / 4, bitmap.width)
    }

    /** Mean absolute luminance difference between horizontal neighbours (high for sharp stripes, low when blurred). */
    private fun edgeEnergy(bitmap: Bitmap): Double {
        var sum = 0.0
        var count = 0
        val y0 = bitmap.height / 4
        for (y in y0 until bitmap.height - y0 step 4) {
            for (x in bitmap.width / 4 until bitmap.width * 3 / 4 - 1) {
                sum += abs(lum(bitmap.getPixel(x, y)) - lum(bitmap.getPixel(x + 1, y)))
                count++
            }
        }
        return if (count == 0) 0.0 else sum / count
    }

    private fun lum(c: Int): Double = ((c shr 16 and 0xFF) + (c shr 8 and 0xFF) + (c and 0xFF)) / 3.0

    private companion object {
        const val PROBE = "probe"
        const val OUTPUT_PROPERTY = "nightbaseline.screenshots.dir"
    }
}

/** Shared verdict of the probe. */
object HazeProbe {
    /** Blur is considered rendered when the edge energy drops below this share of the unblurred one. */
    const val THRESHOLD: Double = 0.5

    /** The blurred box must still show the white half brighter than the striped half by this much (0..255). */
    const val MIN_HALF_CONTRAST: Double = 40.0

    /** True when [ratio] (blurred / unblurred edge energy) shows a blur. */
    fun verdict(ratio: Double): Boolean = ratio < THRESHOLD
}
