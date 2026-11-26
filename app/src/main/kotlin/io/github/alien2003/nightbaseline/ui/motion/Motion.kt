// Motion tokens are literal values from tokens.md 9 (like the colour tables in ui/theme).
@file:Suppress("MagicNumber")

package io.github.alien2003.nightbaseline.ui.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/** Durations of `tokens.md` 9.1, in milliseconds. */
object NbDurations {
    /** `motion.duration.instant`: reduced-motion replacement. */
    const val INSTANT: Int = 0

    /** `motion.duration.micro`: outline to filled icon, navigation label weight crossfade. */
    const val MICRO: Int = 100

    /** `motion.duration.short`: crossfades, top-level switch, effects-mode switch, tooltip, dialog exit. */
    const val SHORT: Int = 150

    /** `motion.duration.medium`: dialog enter, banner in. */
    const val MEDIUM: Int = 250

    /** `motion.duration.reduced`: screen, sheet and dialog crossfade under reduced motion (own clock). */
    const val REDUCED: Int = 120

    /** `motion.duration.long`: onboarding page change. */
    const val LONG: Int = 350

    /** `motion.duration.glowIn`: touch light appears. */
    const val GLOW_IN: Int = 110

    /** `motion.duration.glowOut`: touch light fades after release. */
    const val GLOW_OUT: Int = 480

    /** Retired sheet exit tween; sheets now hide with [NbSprings.SheetHide] (kept for the callers until the sweep). */
    const val SHEET_EXIT: Int = 200

    /** `motion.overscroll` return to rest after a pull or a fling throw. */
    const val OVERSCROLL_RETURN: Int = 700

    /** `motion.overscroll` rise of a fling throw past the edge. */
    const val OVERSCROLL_RISE: Int = 60
}

/** Easing curves of `tokens.md` 9.2. */
object NbEasing {
    /** `motion.easing.standard` (0.2, 0, 0, 1). */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** `motion.easing.flow` (0.3, 0, 0.1, 1): entering elements. */
    val Flow: Easing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)

    /** `motion.easing.exit` (0.3, 0, 1, 1): leaving elements. */
    val Exit: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** `motion.easing.title` (0.33, 0, 0.67, 1): the small top-bar title fading in over scroll. */
    val Title: Easing = CubicBezierEasing(0.33f, 0f, 0.67f, 1f)

    /** `motion.easing.overscrollReturn` (0.2, 0.62, 0.17, 1.05): overscroll back to rest. */
    val OverscrollReturn: Easing = CubicBezierEasing(0.2f, 0.62f, 0.17f, 1.05f)

    /** `motion.easing.overscrollRise`: fling throw past the edge. */
    val OverscrollRise: Easing = FastOutSlowInEasing

    /** `motion.easing.linear`: scroll-driven values. */
    val Linear: Easing = LinearEasing
}

/** A spring token of `tokens.md` 9.3. */
@Immutable
data class NbSpring(
    /** Damping ratio (1.0 = no overshoot). */
    val dampingRatio: Float,
    /** Stiffness. */
    val stiffness: Float,
) {
    /** Whether this spring overshoots (replaced by a snap under reduced motion). */
    val overshoots: Boolean get() = dampingRatio < 1f

    /** The spring as an animation spec. */
    fun <T> spec(): FiniteAnimationSpec<T> = spring(dampingRatio = dampingRatio, stiffness = stiffness)
}

/**
 * Springs of `tokens.md` 9.3: critically damped wherever the canon is (page, selection, sheet), a gentle bounce only
 * on the switch knob. Gesture-driven motion inherits the finger's velocity; data never overshoots.
 */
object NbSprings {
    /** `motion.spring.page`: push / pop, predictive back release (settles in about 470 ms, as measured). */
    val Page: NbSpring = NbSpring(1.0f, 220f)

    /** `motion.spring.morph`: shared-bounds morphs. */
    val Morph: NbSpring = NbSpring(0.9f, 380f)

    /** `motion.spring.selection`: period selector thumb, tab underline (no overshoot). */
    val Selection: NbSpring = NbSpring(1.0f, 247f)

    /** `motion.spring.pressIn`: press shrink of compact elements (buttons, round buttons, chips). */
    val PressIn: NbSpring = NbSpring(1.0f, 632f)

    /** `motion.spring.pressInWide`: press shrink of wide elements (tiles, bar, full-width buttons). */
    val PressInWide: NbSpring = NbSpring(1.0f, 439f)

    /**
     * `motion.spring.pressOut`: release; carries the press velocity, slightly under-damped so a quick tap rebounds,
     * and the scale is clamped at 1.0 (ColorOS never grows an element above its size).
     */
    val PressOut: NbSpring = NbSpring(0.8f, 380f)

    /** Retired single press spring (kept for the callers until the sweep): use [PressIn] / [PressOut]. */
    val Press: NbSpring = PressOut

    /** `motion.spring.highlightIn`: pressed highlight of buttons and tiles appearing. */
    val HighlightIn: NbSpring = NbSpring(1.0f, 1755f)

    /** `motion.spring.highlightOut`: pressed highlight of buttons and tiles fading. */
    val HighlightOut: NbSpring = NbSpring(1.0f, 187f)

    /** `motion.spring.rowHighlightIn`: pressed highlight of rows inside a grouped card. */
    val RowHighlightIn: NbSpring = NbSpring(1.0f, 439f)

    /** `motion.spring.rowHighlightOut`. */
    val RowHighlightOut: NbSpring = NbSpring(1.0f, 895f)

    /** `motion.spring.follow`: anything tracking the finger. */
    val Follow: NbSpring = NbSpring(1.0f, 1500f)

    /** `motion.spring.sheet`: sheet show. */
    val Sheet: NbSpring = NbSpring(1.0f, 250f)

    /** `motion.spring.sheetHide`: programmatic sheet hide. */
    val SheetHide: NbSpring = NbSpring(1.0f, 440f)

    /** `motion.spring.sheetReturn`: a dragged sheet returning to its rest position. */
    val SheetReturn: NbSpring = NbSpring(1.0f, 247f)

    /** `motion.spring.switch`: switch knob (with the stretch to x1.3 at 34.7 % of travel). */
    val Switch: NbSpring = NbSpring(0.7f, 247f)

    /** `motion.spring.squash`: segmented control height squash 0.94 -> 1 (optional). */
    val Squash: NbSpring = NbSpring(0.5f, 110f)

    /** `motion.spring.pane`: pane resize on fold, unfold and resize. */
    val Pane: NbSpring = NbSpring(1.0f, 300f)

    /** `motion.spring.data`: band, marker and value changes (never overshoot). */
    val Data: NbSpring = NbSpring(1.0f, 400f)
}

/** Press scale of the smallest elements (`tokens.md` 9.5): 40 dp round buttons, chips. */
const val NB_PRESS_SCALE_SMALL: Float = 0.96f

/** Press scale of the largest elements: bar, tiles, full-width buttons, sheet handle. */
const val NB_PRESS_SCALE_LARGE: Float = 0.98f

/** Retired fixed press scale (kept for the callers until the sweep): the scale is size-dependent now. */
const val NB_PRESS_SCALE: Float = 0.97f

/** `motion.page.dim`: black dim over the covered page at the end of a push (and the start of a pop) [estimate]. */
const val NB_PAGE_DIM: Float = 0.3f

/** `motion.page.parallax`: fraction of the width the covered page moves during a push or pop. */
const val NB_PAGE_PARALLAX: Float = 1f / 3f

/**
 * The motion specs the components use, in their normal or reduced variant (`tokens.md` 9.6). Reduced motion is
 * active when animations are removed (animator duration scale 0) or battery saver is on; then overshooting springs
 * snap, morphs, sheets and dialogs become a short crossfade on our own clock, press scale, glow spread and overscroll
 * are off, and scroll-driven fades switch at a threshold.
 */
@Suppress("TooManyFunctions") // One spec per motion role of tokens.md 9.
@Immutable
data class NbMotionScheme(
    /** True when the reduced-motion variants replace the normal ones. */
    val reduced: Boolean,
) {
    /** Indicator moving between segments or tabs. */
    fun <T> selection(): FiniteAnimationSpec<T> = if (reduced) snap() else NbSprings.Selection.spec()

    /** Press scale (release); null under reduced motion (no press scale, only the static highlight). */
    fun <T> press(): FiniteAnimationSpec<T>? = if (reduced) null else NbSprings.PressOut.spec()

    /** Press shrink; null under reduced motion. [wide] elements shrink on the softer spring. */
    fun <T> pressIn(wide: Boolean = false): FiniteAnimationSpec<T>? =
        if (reduced) null else (if (wide) NbSprings.PressInWide else NbSprings.PressIn).spec()

    /** Release back to 1.0 (clamped by the caller); null under reduced motion. */
    fun <T> pressOut(): FiniteAnimationSpec<T>? = if (reduced) null else NbSprings.PressOut.spec()

    /** Finger-following values (chart scrub, drags). */
    fun <T> follow(): FiniteAnimationSpec<T> = if (reduced) snap() else NbSprings.Follow.spec()

    /** Data values (band, marker, value); static under reduced motion. */
    fun <T> data(): FiniteAnimationSpec<T> = if (reduced) snap() else NbSprings.Data.spec()

    /** Sheet show. */
    fun <T> sheet(): FiniteAnimationSpec<T> = if (reduced) fade() else NbSprings.Sheet.spec()

    /** Sheet hide (programmatic). */
    fun <T> sheetExit(): FiniteAnimationSpec<T> = if (reduced) fade() else NbSprings.SheetHide.spec()

    /** A dragged sheet returning to rest. */
    fun <T> sheetReturn(): FiniteAnimationSpec<T> = if (reduced) snap() else NbSprings.SheetReturn.spec()

    /** Switch knob. */
    fun <T> switch(): FiniteAnimationSpec<T> = if (reduced) snap() else NbSprings.Switch.spec()

    /** Push / pop of pages. */
    fun <T> page(): FiniteAnimationSpec<T> = if (reduced) fade() else NbSprings.Page.spec()

    /** Short crossfade (top-level switch, effects-mode change, top bar tint). */
    fun <T> crossfade(): FiniteAnimationSpec<T> = if (reduced) snap() else tween(NbDurations.SHORT, easing = NbEasing.Standard)

    /** Navigation item icon fill and label weight crossfade. */
    fun <T> navSelection(): FiniteAnimationSpec<T> = if (reduced) snap() else tween(NbDurations.MICRO, easing = NbEasing.Standard)

    /** Screen change: shared morph normally, a 120 ms crossfade under reduced motion. */
    fun <T> screenChange(): FiniteAnimationSpec<T> =
        if (reduced) tween(NbDurations.REDUCED, easing = NbEasing.Linear) else NbSprings.Morph.spec()

    /** Entering elements (dialog, banner). */
    fun <T> enter(): FiniteAnimationSpec<T> = if (reduced) fade() else tween(NbDurations.MEDIUM, easing = NbEasing.Flow)

    /** Leaving elements (dialog exit). */
    fun <T> exit(): FiniteAnimationSpec<T> = if (reduced) fade() else tween(NbDurations.SHORT, easing = NbEasing.Exit)

    /** Pressed highlight appearing ([row] = a row inside a grouped card). */
    fun <T> stateLayer(row: Boolean = false): AnimationSpec<T> =
        if (reduced) snap() else (if (row) NbSprings.RowHighlightIn else NbSprings.HighlightIn).spec()

    /** Pressed highlight fading. */
    fun <T> stateLayerOut(row: Boolean = false): AnimationSpec<T> =
        if (reduced) snap() else (if (row) NbSprings.RowHighlightOut else NbSprings.HighlightOut).spec()

    /** Touch glow appearing; null under reduced motion (no glow spread; the static highlight remains). */
    fun <T> glowIn(): FiniteAnimationSpec<T>? = if (reduced) null else tween(NbDurations.GLOW_IN, easing = NbEasing.Standard)

    /** Touch glow fading after release. */
    fun <T> glowOut(): FiniteAnimationSpec<T> = if (reduced) snap() else tween(NbDurations.GLOW_OUT, easing = NbEasing.Exit)

    /** Whether the ColorOS bounce overscroll is active (off under reduced motion: content stops at the edge). */
    val overscroll: Boolean get() = !reduced

    private fun <T> fade(): FiniteAnimationSpec<T> = tween(NbDurations.REDUCED, easing = NbEasing.Linear)
}

/** The motion scheme in effect; provided by `NightBaselineTheme` from the effects resolver. */
val LocalNbMotion = staticCompositionLocalOf { NbMotionScheme(reduced = false) }
