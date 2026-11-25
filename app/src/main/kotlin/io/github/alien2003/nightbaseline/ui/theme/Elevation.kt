package io.github.alien2003.nightbaseline.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** One drop shadow of `tokens.md` section 6: vertical offset, blur radius and black at [alpha]; [None] draws nothing. */
@Immutable
data class NbShadow(
    /** Vertical offset. */
    val offsetY: Dp,
    /** Blur radius. */
    val blur: Dp,
    /** Opacity of the black shadow colour. */
    val alpha: Float,
) {
    /** Shadow constants. */
    companion object {
        /** No shadow (`elevation.flat`). */
        val None: NbShadow = NbShadow(0.dp, 0.dp, 0f)
    }
}

/** Elevation tokens of one theme. Shadows are identical in glass and solid modes. */
@Immutable
data class NbElevation(
    /** `elevation.flat`. */
    val flat: NbShadow,
    /** `elevation.card`: cards and tiles (light only). */
    val card: NbShadow,
    /** `elevation.shell`: floating bar and rail. */
    val shell: NbShadow,
    /** `elevation.raised`: tooltip, dragged handle. */
    val raised: NbShadow,
    /** `elevation.overlay`: sheets and dialogs. */
    val overlay: NbShadow,
)

/** Light elevation. */
val LightNbElevation: NbElevation =
    NbElevation(
        flat = NbShadow.None,
        card = NbShadow(1.dp, 3.dp, 0.06f),
        shell = NbShadow(6.dp, 20.dp, 0.10f),
        raised = NbShadow(4.dp, 12.dp, 0.12f),
        overlay = NbShadow(12.dp, 32.dp, 0.14f),
    )

/** Dark elevation: cards are flat, the shell relies on its rim more than on the shadow. */
val DarkNbElevation: NbElevation =
    NbElevation(
        flat = NbShadow.None,
        card = NbShadow.None,
        shell = NbShadow(4.dp, 16.dp, 0.50f),
        raised = NbShadow(4.dp, 12.dp, 0.50f),
        overlay = NbShadow(12.dp, 32.dp, 0.60f),
    )

/** Draws [shadow] behind the element in [shape] (no-op for [NbShadow.None]). */
fun Modifier.nbShadow(
    shadow: NbShadow,
    shape: Shape,
): Modifier =
    if (shadow.alpha <= 0f) {
        this
    } else {
        dropShadow(
            shape = shape,
            shadow = Shadow(radius = shadow.blur, color = Color.Black.copy(alpha = shadow.alpha), offset = DpOffset(0.dp, shadow.offsetY)),
        )
    }
