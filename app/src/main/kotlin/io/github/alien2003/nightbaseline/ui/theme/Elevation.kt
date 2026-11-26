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

/**
 * One drop shadow of `tokens.md` section 6: vertical offset, blur radius and [color] (black by default) at [alpha];
 * [None] draws nothing.
 */
@Immutable
data class NbShadow(
    /** Vertical offset. */
    val offsetY: Dp,
    /** Blur radius. */
    val blur: Dp,
    /** Opacity of the shadow colour. */
    val alpha: Float,
    /** Shadow colour (opaque; [alpha] applies). */
    val color: Color = Color.Black,
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
    /** `elevation.card`: none in both themes (white cards on #F0F1F2 are separated by the background step). */
    val card: NbShadow,
    /** `elevation.shell`: floating bar and rail. */
    val shell: NbShadow,
    /** `elevation.control`: convex round shell buttons. */
    val control: NbShadow,
    /** `elevation.raised`: tooltip, dragged handle. */
    val raised: NbShadow,
    /** `elevation.overlay`: sheets and dialogs. */
    val overlay: NbShadow,
    /** `elevation.accentGlow`: coloured glow under the accent call-to-action. */
    val accentGlow: NbShadow,
)

private const val LIGHT_ACCENT = 0xFF0068E0
private const val DARK_ACCENT = 0xFF428AF7

/** Light elevation (soft shadows, as on the light ColorOS round buttons and bar). */
val LightNbElevation: NbElevation =
    NbElevation(
        flat = NbShadow.None,
        card = NbShadow.None,
        shell = NbShadow(4.dp, 12.dp, 0.10f),
        control = NbShadow(3.dp, 8.dp, 0.10f),
        raised = NbShadow(4.dp, 16.dp, 0.12f),
        overlay = NbShadow(0.dp, 24.dp, 0.12f),
        accentGlow = NbShadow(4.dp, 12.dp, 0.25f, Color(LIGHT_ACCENT)),
    )

/** Dark elevation: cards are flat, the shell relies on its rim more than on the shadow. */
val DarkNbElevation: NbElevation =
    NbElevation(
        flat = NbShadow.None,
        card = NbShadow.None,
        shell = NbShadow(4.dp, 10.dp, 0.35f),
        control = NbShadow(3.dp, 8.dp, 0.20f),
        raised = NbShadow(4.dp, 16.dp, 0.50f),
        overlay = NbShadow(0.dp, 24.dp, 0.30f),
        accentGlow = NbShadow(4.dp, 12.dp, 0.35f, Color(DARK_ACCENT)),
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
            shadow = Shadow(radius = shadow.blur, color = shadow.color.copy(alpha = shadow.alpha), offset = DpOffset(0.dp, shadow.offsetY)),
        )
    }
