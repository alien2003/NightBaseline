package io.github.alien2003.nightbaseline.ui.material

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import io.github.alien2003.nightbaseline.ui.theme.NbColors
import io.github.alien2003.nightbaseline.ui.theme.NbShadow
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.ShellColors
import io.github.alien2003.nightbaseline.ui.theme.ShellTokens
import io.github.alien2003.nightbaseline.ui.theme.nbShadow

/**
 * The shell materials (`coloros17-glass.md` 4.1, ColorOS 17 "Luminous Effect" acrylic): backdrop blur + static
 * per-theme tint + 3 % noise + rim (thin bright top, faint 2 dp bottom reflection band) + soft shadow. No refraction,
 * no reflection, no motion specular. Glass is only for the shell: cards, bands, charts, the chart tooltip, rows and
 * calendar cells always use [solidCard] and friends. The solid fallback keeps the geometry, rim and shadow.
 */
enum class ShellMaterial(
    /** Haze blur radius (dp). */
    val blurRadius: Dp,
    /** Noise factor of the glass (0..1). */
    val noise: Float,
    /** Whether the rim (top highlight + bottom reflection band) is drawn. */
    val rim: Boolean,
) {
    /** Floating bottom bar. */
    Bar(24.dp, GLASS_NOISE, rim = true),

    /** Floating navigation rail. */
    Rail(24.dp, GLASS_NOISE, rim = true),

    /** Top bar while content is under it: progressive blur + darkening, no backing, no hairline. */
    TopBar(12.dp, 0f, rim = false),

    /** Bottom sheet: near-opaque tint, a top highlight only. */
    Sheet(40.dp, GLASS_NOISE, rim = false),

    /** Dialog: near-opaque tint, no rim. */
    Dialog(32.dp, GLASS_NOISE, rim = false),
    ;

    /** Tint composited over the blur. */
    fun tint(shell: ShellColors): Color =
        when (this) {
            Bar, Rail -> shell.barTint
            TopBar -> Color.Transparent
            Sheet -> shell.sheetTint
            Dialog -> shell.dialogTint
        }

    /** Solid fill of the fallback. */
    fun solidFill(
        shell: ShellColors,
        colors: NbColors,
    ): Brush =
        when (this) {
            Bar, Rail -> Brush.verticalGradient(listOf(shell.barSolidTop, shell.barSolidBottom))
            TopBar -> topBarScrim(colors.background, SOLID_TOP_BAR_DIM)
            Sheet -> Brush.verticalGradient(listOf(shell.sheetSolid, shell.sheetSolid))
            Dialog -> Brush.verticalGradient(listOf(shell.dialogSolid, shell.dialogSolid))
        }

    /** Shadow. */
    fun shadow(shell: ShellColors): NbShadow =
        when (this) {
            Bar, Rail -> shell.shellShadow
            TopBar -> NbShadow.None
            Sheet, Dialog -> shell.overlayShadow
        }
}

private const val GLASS_NOISE = 0.03f

/** Darkening of the solid top bar under the status bar (fades to 0 at the bottom of the fade zone). */
private const val SOLID_TOP_BAR_DIM = 0.85f

/** Alpha of the 1 dp contrasting border in higher-contrast mode (`coloros17-glass.md` 4.5). */
private const val HIGH_CONTRAST_BORDER_ALPHA = 0.6f

private fun topBarScrim(
    color: Color,
    alpha: Float,
): Brush = Brush.verticalGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)))

/**
 * The backdrop the glass shell blurs: the content root marks itself with `Modifier.hazeSource(state)` and provides
 * the state here. Null (previews without a source, tests) means glass draws its tint without blur.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/** Higher contrast (or reduced transparency) is requested: solid shell plus a 1 dp contrasting border. */
val highContrastShell: Boolean
    @Composable get() = NbTheme.effects.reason == EffectsReason.Transparency

/**
 * Draws [material] behind the element in [shape]: shadow, then glass (Haze backdrop blur + tint + noise, plus a faint
 * light towards the bottom) or the solid fill, then the rim; in higher-contrast mode a 1 dp border instead of the
 * rim. The geometry is the same in both modes; only the fill differs (`tokens.md` 7.3).
 *
 * @param strength 0..1 opacity of the material (the top bar fades in with scroll); 1 for every other surface.
 */
@Suppress("CyclomaticComplexMethod") // One modifier composing the material layers per effect mode and contrast.
@Composable
fun Modifier.shellSurface(
    material: ShellMaterial,
    shape: Shape,
    strength: Float = 1f,
): Modifier {
    val colors = NbTheme.colors
    val shell = ShellTokens.colors
    val effects = NbTheme.effects
    val hazeState = LocalHazeState.current
    val shadow = material.shadow(shell)
    val tint = material.tint(shell)
    val topBar = material == ShellMaterial.TopBar
    val style =
        remember(material, tint, strength, colors.background) {
            HazeBlurStyle {
                blurEnabled(true)
                blurRadius(material.blurRadius)
                noiseFactor(material.noise)
                backgroundColor(colors.background)
                if (tint.alpha > 0f) colorEffects(listOf(HazeColorEffect.tint(tint)))
                alpha(strength)
                if (topBar) {
                    // Progressive: full under the status bar, fading out towards the bottom of the fade zone.
                    progressive(HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f))
                }
            }
        }
    val glass = effects.glass
    val fill: Modifier =
        when {
            strength <= 0f -> Modifier
            glass && hazeState != null -> Modifier.hazeBlur(HazeInput.Sources(hazeState), style)
            glass && topBar -> Modifier
            glass -> Modifier.background(tint.copy(alpha = tint.alpha * strength), shape)
            else -> Modifier.background(material.solidFill(shell, colors), shape, alpha = strength)
        }
    // Drawn on top of the blur: the darkening of the top bar, the bar's light towards its bottom edge.
    val overlay: Modifier =
        when {
            strength <= 0f || !glass -> {
                Modifier
            }

            // The shell redraws with every frame of the content blurred under it: brushes are built once per size.
            topBar -> {
                Modifier.drawWithCache {
                    val scrim = topBarScrim(colors.background, shell.topBarDim)
                    onDrawWithContent {
                        drawContent()
                        drawRect(scrim, alpha = strength)
                    }
                }
            }

            material == ShellMaterial.Bar || material == ShellMaterial.Rail -> {
                Modifier.drawWithCache {
                    val sheen = Brush.verticalGradient(listOf(Color.Transparent, shell.barSheen))
                    onDrawWithContent {
                        drawContent()
                        drawRect(sheen)
                    }
                }
            }

            else -> {
                Modifier
            }
        }
    val edge: Modifier =
        when {
            strength <= 0f || topBar -> Modifier
            highContrastShell -> Modifier.border(1.dp, colors.onGlassPrimary.copy(alpha = HIGH_CONTRAST_BORDER_ALPHA), shape)
            material.rim -> Modifier.shellRim(shell.rimTop, shell.rimTopWidth, shell.rimBottom, shell.rimBottomWidth, shape)
            else -> Modifier.shellRim(shell.sheetHighlight, 0.5.dp, Color.Transparent, 0.dp, shape)
        }
    return this
        .nbShadow(if (strength > 0f) shadow else NbShadow.None, shape)
        .clip(shape)
        .then(overlay)
        .then(fill)
        .then(edge)
}

/**
 * The shell rim, drawn inside the (clipped) shape: a highlight of [topWidth] along the top edge fading out towards
 * the middle, and a reflection band of [bottomWidth] along the bottom edge (bottom brighter and thicker, as measured
 * on the bar: 1 px top rim, 2–3 px bottom rim). Draws above the content's background, below its children.
 */
fun Modifier.shellRim(
    top: Color,
    topWidth: Dp,
    bottom: Color,
    bottomWidth: Dp,
    shape: Shape,
): Modifier =
    // Outline, gradients and strokes are built once per size (the bar redraws every frame of the blur under it).
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        // Half of a centred stroke lies outside the clipped shape, so the visible rim is [topWidth] wide.
        val topRim =
            if (topWidth > 0.dp && top.alpha > 0f) {
                Brush.verticalGradient(0f to top, RIM_FADE to top.copy(alpha = 0f), 1f to top.copy(alpha = 0f)) to
                    Stroke(width = topWidth.toPx() * 2)
            } else {
                null
            }
        val bottomRim =
            if (bottomWidth > 0.dp && bottom.alpha > 0f) {
                Brush.verticalGradient(0f to bottom.copy(alpha = 0f), 1f - RIM_FADE to bottom.copy(alpha = 0f), 1f to bottom) to
                    Stroke(width = bottomWidth.toPx() * 2)
            } else {
                null
            }
        onDrawWithContent {
            drawContent()
            topRim?.let { (brush, stroke) -> drawOutline(outline, brush, style = stroke) }
            bottomRim?.let { (brush, stroke) -> drawOutline(outline, brush, style = stroke) }
        }
    }

/** Fraction of the height over which a rim fades out (top) or in (bottom). */
private const val RIM_FADE = 0.45f

/**
 * The solid convex surface of round shell buttons (`coloros17-glass.md` 4.2, measured on the back button): a vertical
 * gradient, a 0.5 dp top rim and a 1 dp brighter bottom rim, and a soft shadow (`elevation.control`). Never blurred.
 * Higher contrast adds a 1 dp border.
 */
@Composable
fun Modifier.convexSurface(shape: Shape): Modifier {
    val shell = ShellTokens.colors
    val colors = NbTheme.colors
    val edge =
        if (highContrastShell) {
            Modifier.border(1.dp, colors.onGlassPrimary.copy(alpha = HIGH_CONTRAST_BORDER_ALPHA), shape)
        } else {
            Modifier.shellRim(shell.convexRimTop, 0.5.dp, shell.convexRimBottom, 1.dp, shape)
        }
    return this
        .nbShadow(shell.controlShadow, shape)
        .clip(shape)
        .background(Brush.verticalGradient(listOf(shell.convexTop, shell.convexBottom)))
        .then(edge)
}

/**
 * Lets the element draw [extra] beyond its bottom edge without taking the space in the layout (the top bar's blur
 * and darkening fade out over 16 dp below the toolbar). Use after a size-fixing modifier such as `matchParentSize`.
 */
fun Modifier.extendBottom(extra: Dp): Modifier =
    layout { measurable, constraints ->
        val add = extra.roundToPx()
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight + add else constraints.maxHeight
        val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
        layout(placeable.width, (placeable.height - add).coerceAtLeast(0)) { placeable.place(0, 0) }
    }

/**
 * Masks the element so it fades to transparent over its last [fade] (the top bar's blur ends softly even where the
 * platform blur has no progressive mode). One offscreen layer.
 */
fun Modifier.fadeOutBottom(fade: Dp): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val start = (1f - fade.toPx() / size.height).coerceIn(0f, 1f)
            val mask = Brush.verticalGradient(0f to Color.Black, start to Color.Black, 1f to Color.Transparent)
            onDrawWithContent {
                drawContent()
                drawRect(mask, blendMode = BlendMode.DstIn)
            }
        }

/** `material.solid.card`: `surface.1` with `elevation.card`. */
@Composable
fun Modifier.solidCard(shape: Shape): Modifier =
    nbShadow(NbTheme.elevation.card, shape)
        .background(NbTheme.colors.surface1, shape)

/** `material.solid.nested`: `surface.2`, no shadow. */
@Composable
fun Modifier.solidNested(shape: Shape): Modifier = background(NbTheme.colors.surface2, shape)

/** `material.solid.tooltip`: `solid.tooltip`, 1 dp `separator` outline, `elevation.raised`; both effect modes. */
@Composable
fun Modifier.solidTooltip(shape: Shape): Modifier =
    nbShadow(NbTheme.elevation.raised, shape)
        .background(NbTheme.colors.solidTooltip, shape)
        .border(1.dp, NbTheme.colors.separator, shape)
