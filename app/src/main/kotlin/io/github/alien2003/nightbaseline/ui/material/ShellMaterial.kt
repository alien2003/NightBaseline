package io.github.alien2003.nightbaseline.ui.material

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import io.github.alien2003.nightbaseline.ui.theme.NbColors
import io.github.alien2003.nightbaseline.ui.theme.NbElevation
import io.github.alien2003.nightbaseline.ui.theme.NbShadow
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import io.github.alien2003.nightbaseline.ui.theme.nbShadow

/**
 * The shell materials of `tokens.md` 7.1 (glass) with their solid counterparts of 7.2. Glass is only for the shell:
 * cards, bands, charts, the chart tooltip, rows and calendar cells always use [solidCard] and friends.
 */
enum class ShellMaterial(
    /** Haze blur radius (dp). */
    val blurRadius: Dp,
    /** Noise factor of the glass (0..1). */
    val noise: Float,
    /** Whether the 1 dp gradient rim and 0.5 dp outer hairline are drawn. */
    val rim: Boolean,
) {
    /** `material.glass.bar` / `material.solid.bar`: floating bottom bar. */
    Bar(24.dp, GLASS_NOISE, rim = true),

    /** `material.glass.rail` / `material.solid.bar`: floating navigation rail. */
    Rail(24.dp, GLASS_NOISE, rim = true),

    /** `material.glass.topBar` / `material.solid.topBar`: top bar while content is under it. */
    TopBar(20.dp, 0f, rim = false),

    /** `material.glass.sheet` / `material.solid.sheet`. */
    Sheet(40.dp, GLASS_NOISE, rim = true),

    /** `material.glass.dialog` / `material.solid.dialog`. */
    Dialog(32.dp, GLASS_NOISE, rim = true),
    ;

    /** Tint composited over the blur. */
    fun tint(colors: NbColors): Color =
        when (this) {
            Bar, Rail -> colors.glassBar
            TopBar -> colors.glassTopBar
            Sheet, Dialog -> colors.glassSheet
        }

    /** Solid fill of the fallback. */
    fun solidFill(colors: NbColors): Brush =
        when (this) {
            Bar, Rail -> Brush.verticalGradient(listOf(colors.solidBarTop, colors.solidBarBottom))
            TopBar -> Brush.verticalGradient(listOf(colors.solidTopBar, colors.solidTopBar))
            Sheet -> Brush.verticalGradient(listOf(colors.solidSheet, colors.solidSheet))
            Dialog -> Brush.verticalGradient(listOf(colors.solidDialog, colors.solidDialog))
        }

    /** Shadow token. */
    fun shadow(elevation: NbElevation): NbShadow =
        when (this) {
            Bar, Rail -> elevation.shell
            TopBar -> NbShadow.None
            Sheet, Dialog -> elevation.overlay
        }
}

private const val GLASS_NOISE = 0.03f

/**
 * The backdrop the glass shell blurs: the content root marks itself with `Modifier.hazeSource(state)` and provides
 * the state here. Null (previews without a source, tests) means glass draws its tint without blur.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Draws [material] behind the element in [shape]: shadow, then glass (Haze backdrop blur + token tint + noise) or the
 * solid fill, then the rim. The geometry is the same in both modes; only the fill differs (`tokens.md` 7.3).
 *
 * @param strength 0..1 opacity of the material (the top bar fades in with scroll); 1 for every other surface.
 */
@Composable
fun Modifier.shellSurface(
    material: ShellMaterial,
    shape: Shape,
    strength: Float = 1f,
): Modifier {
    val colors = NbTheme.colors
    val effects = NbTheme.effects
    val hazeState = LocalHazeState.current
    val shadow = material.shadow(NbTheme.elevation)
    val tint = material.tint(colors)
    val style =
        remember(material, tint, strength) {
            HazeBlurStyle {
                blurEnabled(true)
                blurRadius(material.blurRadius)
                noiseFactor(material.noise)
                backgroundColor(colors.background)
                colorEffects(listOf(HazeColorEffect.tint(tint)))
                alpha(strength)
                if (material == ShellMaterial.TopBar) {
                    // Progressive: full under the status bar, fading out towards the bar's bottom edge.
                    progressive(HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f))
                }
            }
        }
    val fill: Modifier =
        when {
            strength <= 0f -> Modifier
            effects.glass && hazeState != null -> Modifier.hazeBlur(HazeInput.Sources(hazeState), style)
            effects.glass -> Modifier.background(tint.copy(alpha = tint.alpha * strength), shape)
            else -> Modifier.background(material.solidFill(colors), shape, alpha = strength)
        }
    return this
        .nbShadow(if (strength > 0f) shadow else NbShadow.None, shape)
        .clip(shape)
        .then(fill)
        .then(if (material.rim && strength > 0f) Modifier.glassRim(colors, shape) else Modifier)
}

/**
 * The rim of `tokens.md` 2.5: a 1 dp vertical gradient stroke inside the shape (`rimTop` -> `rimMid` -> `rimBottom`)
 * plus a 0.5 dp outer hairline (`rimOuter`) that keeps the edge visible on a same-coloured backdrop.
 */
fun Modifier.glassRim(
    colors: NbColors,
    shape: Shape,
): Modifier =
    border(0.5.dp, colors.glassRimOuter, shape)
        .border(1.dp, Brush.verticalGradient(listOf(colors.glassRimTop, colors.glassRimMid, colors.glassRimBottom)), shape)

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
