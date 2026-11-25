package io.github.alien2003.nightbaseline.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import io.github.alien2003.nightbaseline.ui.material.EffectsDecision
import io.github.alien2003.nightbaseline.ui.material.LocalEffects
import io.github.alien2003.nightbaseline.ui.material.rememberPlatformEffectsInputs
import io.github.alien2003.nightbaseline.ui.material.resolveEffectsMode
import io.github.alien2003.nightbaseline.ui.motion.LocalNbMotion
import io.github.alien2003.nightbaseline.ui.motion.NbMotionScheme

/** Colour tokens in effect. */
val LocalNbColors = staticCompositionLocalOf { LightNbColors }

/** Type scale in effect. */
val LocalNbTypography = staticCompositionLocalOf { DefaultNbTypography }

/** Elevation tokens in effect. */
val LocalNbElevation = staticCompositionLocalOf { LightNbElevation }

/**
 * NightBaseline's theme: the tokens of `docs/design/tokens.md` for light or dark, the effects decision (glass or the
 * solid fallback, `tokens.md` 7.3) and the motion scheme that follows from it (`tokens.md` 9.6).
 *
 * @param darkTheme dark tokens; follows the system by default (no dynamic colour).
 * @param effects a forced effects decision (component catalog, screenshot tests); null resolves it from the
 *   platform with [rememberPlatformEffectsInputs].
 * @param reducedMotion forces the reduced-motion variants on or off; null takes them from the effects decision.
 * @param fontScale overrides the system font scale (catalog and tests); null keeps the system value. Text sizes are
 *   in sp and are never capped, so 2.0 renders at 200 %.
 */
@Composable
fun NightBaselineTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    effects: EffectsDecision? = null,
    reducedMotion: Boolean? = null,
    fontScale: Float? = null,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkNbColors else LightNbColors
    val elevation = if (darkTheme) DarkNbElevation else LightNbElevation
    val decision = effects ?: resolveEffectsMode(rememberPlatformEffectsInputs())
    val motion = NbMotionScheme(reduced = reducedMotion ?: decision.reducedMotion)
    val density = LocalDensity.current
    val scaledDensity =
        remember(density, fontScale) {
            if (fontScale == null) density else Density(density.density, fontScale)
        }
    val materialColors = remember(colors) { colors.toMaterialColorScheme() }
    CompositionLocalProvider(
        LocalNbColors provides colors,
        LocalNbTypography provides DefaultNbTypography,
        LocalNbElevation provides elevation,
        LocalEffects provides decision,
        LocalNbMotion provides motion,
        LocalDensity provides scaledDensity,
    ) {
        MaterialTheme(colorScheme = materialColors, content = content)
    }
}

/** Accessors for the tokens in effect, e.g. `NbTheme.colors.statusMoved`. */
object NbTheme {
    /** Colour tokens. */
    val colors: NbColors
        @Composable @ReadOnlyComposable
        get() = LocalNbColors.current

    /** Type scale. */
    val typography: NbTypography
        @Composable @ReadOnlyComposable
        get() = LocalNbTypography.current

    /** Elevation. */
    val elevation: NbElevation
        @Composable @ReadOnlyComposable
        get() = LocalNbElevation.current

    /** Glass or solid, and why. */
    val effects: EffectsDecision
        @Composable @ReadOnlyComposable
        get() = LocalEffects.current

    /** Normal or reduced motion specs. */
    val motion: NbMotionScheme
        @Composable @ReadOnlyComposable
        get() = LocalNbMotion.current
}

/**
 * Material 3 colour scheme built from our tokens, so Material components used for their semantics (switches, radio
 * buttons, text fields) are styled by the same values. Error roles map to the neutral info colour: the app has no
 * error colour (`tokens.md` 2.3).
 */
private fun NbColors.toMaterialColorScheme() =
    if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = accent,
            tertiary = statusWithin,
            background = background,
            onBackground = textPrimary,
            surface = surface1,
            onSurface = textPrimary,
            surfaceVariant = surface3,
            onSurfaceVariant = textSecondary,
            surfaceContainer = surface2,
            surfaceContainerHigh = surface3,
            outline = outline,
            outlineVariant = separator,
            error = statusInfo,
            onError = surface1,
            errorContainer = statusInfoContainer,
            onErrorContainer = statusInfoOnContainer,
            scrim = scrim,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = accent,
            tertiary = statusWithin,
            background = background,
            onBackground = textPrimary,
            surface = surface1,
            onSurface = textPrimary,
            surfaceVariant = surface3,
            onSurfaceVariant = textSecondary,
            surfaceContainer = surface2,
            surfaceContainerHigh = surface3,
            outline = outline,
            outlineVariant = separator,
            error = statusInfo,
            onError = surface1,
            errorContainer = statusInfoContainer,
            onErrorContainer = statusInfoOnContainer,
            scrim = scrim,
        )
    }
