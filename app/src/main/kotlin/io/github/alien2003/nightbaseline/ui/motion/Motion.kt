// Motion tokens are literal values from tokens.md 9 (like the colour tables in ui/theme).
@file:Suppress("MagicNumber")

package io.github.alien2003.nightbaseline.ui.motion

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
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

    /** `motion.duration.micro`: state layer in, outline to filled icon. */
    const val MICRO: Int = 100

    /** `motion.duration.short`: crossfades, top bar tint, effects-mode switch, tooltip. */
    const val SHORT: Int = 150

    /** `motion.duration.medium`: dialog enter, banner in. */
    const val MEDIUM: Int = 250

    /** `motion.duration.reduced`: screen crossfade that replaces morphs under reduced motion (own clock). */
    const val REDUCED: Int = 120

    /** `motion.duration.long`: onboarding page change. */
    const val LONG: Int = 350

    /** `motion.duration.glowIn`: touch light appears. */
    const val GLOW_IN: Int = 110

    /** `motion.duration.glowOut`: touch light fades after release. */
    const val GLOW_OUT: Int = 480

    /** Sheet exit tween (`tokens.md` 9.4). */
    const val SHEET_EXIT: Int = 200
}

/** Easing curves of `tokens.md` 9.2. */
object NbEasing {
    /** `motion.easing.standard` (0.2, 0, 0, 1). */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** `motion.easing.flow` (0.3, 0, 0.1, 1): entering elements. */
    val Flow: Easing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)

    /** `motion.easing.exit` (0.3, 0, 1, 1): leaving elements. */
    val Exit: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

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

/** Springs of `tokens.md` 9.3. Gesture-driven motion inherits the finger's velocity; data never overshoots. */
object NbSprings {
    /** `motion.spring.page`: push / pop, predictive back release. */
    val Page: NbSpring = NbSpring(1.0f, 220f)

    /** `motion.spring.morph`: shared-bounds morphs. */
    val Morph: NbSpring = NbSpring(0.9f, 380f)

    /** `motion.spring.selection`: navigation and period capsules. */
    val Selection: NbSpring = NbSpring(0.72f, 600f)

    /** `motion.spring.press`: press scale 1 -> 0.97 and release. */
    val Press: NbSpring = NbSpring(0.6f, 1200f)

    /** `motion.spring.follow`: anything tracking the finger. */
    val Follow: NbSpring = NbSpring(1.0f, 1500f)

    /** `motion.spring.sheet`: sheet settle. */
    val Sheet: NbSpring = NbSpring(0.92f, 420f)

    /** `motion.spring.pane`: pane resize on fold, unfold and resize. */
    val Pane: NbSpring = NbSpring(1.0f, 300f)

    /** `motion.spring.data`: band, marker and value changes (never overshoot). */
    val Data: NbSpring = NbSpring(1.0f, 400f)
}

/** Press scale target (`tokens.md` 9.5). */
const val NB_PRESS_SCALE: Float = 0.97f

/**
 * The motion specs the components use, in their normal or reduced variant (`tokens.md` 9.6). Reduced motion is
 * active when animations are removed (animator duration scale 0) or battery saver is on; then overshooting springs
 * snap, morphs become a short crossfade on our own clock, press scale and touch glow are off, and scroll-driven fades
 * switch at a threshold.
 */
@Suppress("TooManyFunctions") // One spec per motion role of tokens.md 9.
@Immutable
data class NbMotionScheme(
    /** True when the reduced-motion variants replace the normal ones. */
    val reduced: Boolean,
) {
    /** Capsule moving between navigation or period items. */
    fun <T> selection(): FiniteAnimationSpec<T> = if (reduced) snap() else NbSprings.Selection.spec()

    /** Press scale; null under reduced motion (no press scale, only the static pressed layer). */
    fun <T> press(): FiniteAnimationSpec<T>? = if (reduced) null else NbSprings.Press.spec()

    /** Finger-following values (chart scrub, drags). */
    fun <T> follow(): FiniteAnimationSpec<T> = if (reduced) snap() else NbSprings.Follow.spec()

    /** Data values (band, marker, value); static under reduced motion. */
    fun <T> data(): FiniteAnimationSpec<T> = if (reduced) snap() else NbSprings.Data.spec()

    /** Sheet open / settle. */
    fun <T> sheet(): FiniteAnimationSpec<T> = if (reduced) snap() else NbSprings.Sheet.spec()

    /** Sheet exit. */
    fun <T> sheetExit(): FiniteAnimationSpec<T> = if (reduced) snap() else tween(NbDurations.SHEET_EXIT, easing = NbEasing.Exit)

    /** Short crossfade (top-level switch, effects-mode change, top bar tint). */
    fun <T> crossfade(): FiniteAnimationSpec<T> = if (reduced) snap() else tween(NbDurations.SHORT, easing = NbEasing.Standard)

    /** Screen change: shared morph normally, a 120 ms crossfade under reduced motion. */
    fun <T> screenChange(): FiniteAnimationSpec<T> =
        if (reduced) tween(NbDurations.REDUCED, easing = NbEasing.Linear) else NbSprings.Morph.spec()

    /** Entering elements (dialog, banner). */
    fun <T> enter(): FiniteAnimationSpec<T> = if (reduced) snap() else tween(NbDurations.MEDIUM, easing = NbEasing.Flow)

    /** State layer fade in. */
    fun <T> stateLayer(): AnimationSpec<T> = if (reduced) snap() else tween(NbDurations.MICRO, easing = NbEasing.Standard)

    /** Touch glow appearing; null under reduced motion (no glow). */
    fun <T> glowIn(): FiniteAnimationSpec<T>? = if (reduced) null else tween(NbDurations.GLOW_IN, easing = NbEasing.Standard)

    /** Touch glow fading after release. */
    fun <T> glowOut(): FiniteAnimationSpec<T> = if (reduced) snap() else tween(NbDurations.GLOW_OUT, easing = NbEasing.Exit)
}

/** The motion scheme in effect; provided by `NightBaselineTheme` from the effects resolver. */
val LocalNbMotion = staticCompositionLocalOf { NbMotionScheme(reduced = false) }
