package io.github.alien2003.nightbaseline.ui.material

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

/**
 * Renders the content without colour (saturation 0), for the catalog's grayscale toggle and the grayscale screenshot
 * variants: every status must stay distinguishable by shape and label alone (`principles.md` 4). Works on every API
 * level (a colour-filtered layer, not a RenderEffect).
 */
fun Modifier.grayscale(enabled: Boolean = true): Modifier =
    if (!enabled) {
        this
    } else {
        drawWithContent {
            val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
                drawContent()
                canvas.restore()
            }
        }
    }
