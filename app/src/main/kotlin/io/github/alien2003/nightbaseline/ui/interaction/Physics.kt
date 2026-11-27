// The registry holds the only motion literals of the app (tokens.md 9 is generated from it and checked by a test).
@file:Suppress("MagicNumber")

package io.github.alien2003.nightbaseline.ui.interaction

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.sqrt

/**
 * One spring of the registry (ADR 0011). Every animated value of the app is a scalar driven by one of these; the
 * visibility threshold is part of the token, so a motion ends when it visibly ends (a 1e-3 share of the travel, about
 * one pixel of a page), never on Compose's 0.01 px default.
 */
@Immutable
data class NbSpring(
    /** Damping ratio (1.0 = critical, above 1 = overdamped; both never overshoot). */
    val damping: Float,
    /** Stiffness k; the natural frequency is sqrt(k) per second. */
    val stiffness: Float,
) {
    /** Natural angular frequency, 1/s. */
    val omega: Float get() = sqrt(stiffness)

    /** True when the spring overshoots its target (damping below 1). */
    val overshoots: Boolean get() = damping < 1f

    /**
     * The fastest start velocity (in units of the travel per second) towards a target [distance] away that still
     * reaches it without crossing it: `omega * (zeta + sqrt(zeta^2 - 1)) * distance` (ADR 0011, lemma 1).
     */
    fun crossingVelocity(distance: Float): Float {
        val z = damping.coerceAtLeast(1f)
        return omega * (z + sqrt(z * z - 1f)) * distance
    }

    /** As a Float spec that ends at [threshold]. */
    fun spec(threshold: Float = NbPhysics.P_EPS): FiniteAnimationSpec<Float> = spring(damping, stiffness, threshold)

    /** As a spec of any type with the default threshold (property motions of colours, offsets). */
    fun <T> specOf(): FiniteAnimationSpec<T> = spring(damping, stiffness)
}

/**
 * The single motion registry of the app (ADR 0011, mirrored 1:1 in `tokens.md` 9). Navigation of every kind runs on
 * [Nav]; every other entry is a property motion with one named role. No other file may contain a spring or tween
 * literal (enforced by `InteractionBoundaryTest`).
 */
object NbPhysics {
    /**
     * Every navigation kind (page, tab, pager, swap, sheet, dialog, reveal): damping 1.2, stiffness 860, fitted to the
     * ColorOS Settings back recording together with the page pose exponent [PAGE_GAMMA] (`tools/design/fit-spring.py`:
     * rms 2.7 px on the incoming page, 1.7 px on the outgoing page; the linear coupling fits at 15.4 px only).
     * [measured fit of reference-analysis 8.1; HYPOTHESIS until a pixel-level re-measure of the frames]
     */
    val Nav: NbSpring = NbSpring(1.2f, 860f)

    /** Icon outline to filled and the navigation label weight (about 100 ms visually). */
    val Quick: NbSpring = NbSpring(1f, 1500f)

    /** Selection indicators: tab underline, period thumb trailing edge, radio fill. */
    val Select: NbSpring = NbSpring(1f, 247f)

    /** The leading edge of the period thumb (it stretches towards the new period). */
    val SelectLead: NbSpring = NbSpring(1f, 600f)

    /** Data values: band, marker, value changes (never overshoot). */
    val Data: NbSpring = NbSpring(1f, 400f)

    /** The switch knob, the one bounce of the canon. */
    val Switch: NbSpring = NbSpring(0.7f, 247f)

    /** Press shrink of compact elements. */
    val PressIn: NbSpring = NbSpring(1f, 632f)

    /** Press shrink of wide elements (tiles, bar, full-width buttons). */
    val PressInWide: NbSpring = NbSpring(1f, 439f)

    /** Press release (clamped at scale 1.0 by the press node). */
    val PressOut: NbSpring = NbSpring(0.8f, 380f)

    /** Pressed highlight of buttons and tiles appearing / fading. */
    val HighlightIn: NbSpring = NbSpring(1f, 1755f)

    /** Pressed highlight of buttons and tiles fading. */
    val HighlightOut: NbSpring = NbSpring(1f, 187f)

    /** Pressed highlight of a row inside a grouped card appearing. */
    val RowHighlightIn: NbSpring = NbSpring(1f, 439f)

    /** Pressed highlight of a row fading. */
    val RowHighlightOut: NbSpring = NbSpring(1f, 895f)

    /** Overscroll return to the edge (settles from 100 px to 0.5 px in about 0.65 s, reference-analysis 8.2). */
    val Overscroll: NbSpring = NbSpring(1f, 150f)

    /** Settle tolerance of every navigation scalar p (a 1e-3 share of the travel). */
    const val P_EPS: Float = 1e-3f

    /** Settle tolerance of the overscroll offset, px. */
    const val OVERSCROLL_EPS_PX: Float = 0.5f

    /** Touch light appears (the only tween in, Iris / COUI). */
    const val GLOW_IN_MS: Int = 110

    /** Touch light fades after release. */
    const val GLOW_OUT_MS: Int = 480

    /** Share of the width the covered page moves (reference-analysis 8.1). */
    const val PARALLAX: Float = 1f / 3f

    /** Black dim over the covered page at full cover (dark theme only; reference-analysis 8.1). */
    const val DIM: Float = 0.3f

    /**
     * Exponent of the leaving page's travel: the upper page of a stack pair is at `W * (1 - s^gamma)` when a share s
     * of it is shown, the lower at `-W / 3 * s` (fitted with [Nav]).
     */
    const val PAGE_GAMMA: Float = 2.2f

    /** A dialog starts at this scale. */
    const val DIALOG_START_SCALE: Float = 0.8f

    /** A dialog is opaque from this share of its motion on. */
    const val DIALOG_OPAQUE_AT: Float = 0.35f

    /** A revealed element moves up this far (dp) while it fades in. */
    const val REVEAL_RISE_DP: Float = 8f

    /** Radio fill: the pulse amplitude of `P_radio(p) = 1 + 0.08 sin(pi p)`. */
    const val RADIO_PULSE: Float = 0.08f

    /** The switch knob stretches to x1.3 while it travels, peaking at 34.7 % of the travel. */
    const val SWITCH_STRETCH: Float = 0.3f

    /** Peak of the switch knob stretch along the travel. */
    const val SWITCH_STRETCH_PEAK: Float = 0.347f

    /** Press scale of the smallest elements (40 dp). */
    const val PRESS_SCALE_SMALL: Float = 0.96f

    /** Press scale of the largest elements (bar, tiles, full-width buttons). */
    const val PRESS_SCALE_LARGE: Float = 0.98f

    /** Overscroll: the content follows this share of a pull at the edge, falling linearly to 0 at [OVERSCROLL_MAX_DP]. */
    const val OVERSCROLL_FOLLOW: Float = 0.5f

    /** Overscroll: the largest translation past an edge (dp). */
    const val OVERSCROLL_MAX_DP: Float = 240f

    /** Every spring of the registry with its name (the generated `tokens.md` 9 table and the I12 test). */
    val springs: Map<String, NbSpring>
        get() =
            linkedMapOf(
                "Nav" to Nav,
                "Quick" to Quick,
                "Select" to Select,
                "SelectLead" to SelectLead,
                "Data" to Data,
                "Switch" to Switch,
                "PressIn" to PressIn,
                "PressInWide" to PressInWide,
                "PressOut" to PressOut,
                "HighlightIn" to HighlightIn,
                "HighlightOut" to HighlightOut,
                "RowHighlightIn" to RowHighlightIn,
                "RowHighlightOut" to RowHighlightOut,
                "Overscroll" to Overscroll,
            )
}

/** The easing curves left after ADR 0011: the two glow tweens and the scroll-driven small title. */
object NbEasing {
    /** `motion.easing.standard` (0.2, 0, 0, 1): glow in. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** `motion.easing.exit` (0.3, 0, 1, 1): glow out. */
    val Exit: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** `motion.easing.title` (0.33, 0, 0.67, 1): the small top-bar title as a pure function of the scroll offset. */
    val Title: Easing = CubicBezierEasing(0.33f, 0f, 0.67f, 1f)
}

/**
 * The one motion configuration of the app (ADR 0011 R5), provided once at the root. [reduced] is true when the
 * platform removes animations (animator duration scale 0) or battery saver is on [decision: battery saver also
 * removes decoration]; then every driver snaps, press scale, glow and highlight snap, the overscroll is off and back
 * gestures show no preview. Other duration scales are applied by the platform's `MotionDurationScale` already and are
 * not applied again here. Fling is content physics and stays on.
 */
@Immutable
data class NbMotionConfig(
    /** Reduced motion. */
    val reduced: Boolean,
) {
    /** A property motion on [spring]; a snap under reduced motion. */
    fun <T> of(spring: NbSpring): FiniteAnimationSpec<T> = if (reduced) snap() else spring.specOf()

    /** Press shrink; null under reduced motion. */
    fun <T> pressIn(wide: Boolean = false): FiniteAnimationSpec<T>? =
        if (reduced) null else (if (wide) NbPhysics.PressInWide else NbPhysics.PressIn).specOf()

    /** Press release; null under reduced motion. */
    fun <T> pressOut(): FiniteAnimationSpec<T>? = if (reduced) null else NbPhysics.PressOut.specOf()

    /** Pressed highlight appearing ([row] = a row inside a grouped card). */
    fun <T> stateLayer(row: Boolean = false): AnimationSpec<T> = of(if (row) NbPhysics.RowHighlightIn else NbPhysics.HighlightIn)

    /** Pressed highlight fading. */
    fun <T> stateLayerOut(row: Boolean = false): AnimationSpec<T> = of(if (row) NbPhysics.RowHighlightOut else NbPhysics.HighlightOut)

    /** Touch glow appearing; null under reduced motion (the static highlight remains). */
    fun <T> glowIn(): FiniteAnimationSpec<T>? = if (reduced) null else tween(NbPhysics.GLOW_IN_MS, easing = NbEasing.Standard)

    /** Touch glow fading. */
    fun <T> glowOut(): FiniteAnimationSpec<T> = if (reduced) snap() else tween(NbPhysics.GLOW_OUT_MS, easing = NbEasing.Exit)

    /** Whether the ColorOS overscroll is active (off under reduced motion). */
    val overscroll: Boolean get() = !reduced
}

/** The motion configuration in effect; provided by `NightBaselineTheme`. */
val LocalNbMotion = staticCompositionLocalOf { NbMotionConfig(reduced = false) }
