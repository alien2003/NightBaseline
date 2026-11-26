package io.github.alien2003.nightbaseline.ui.shell

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import io.github.alien2003.nightbaseline.ui.motion.NbDurations
import io.github.alien2003.nightbaseline.ui.motion.NbEasing
import io.github.alien2003.nightbaseline.ui.motion.NbSprings
import io.github.alien2003.nightbaseline.ui.theme.NbTheme

/**
 * Page transitions of `coloros-conformance.md` P1 / P2, measured on the ColorOS Settings back gesture
 * (`reference-analysis.md` 8.1): a pushed page slides in from the end edge over the full width while the covered page
 * moves a third of the width towards the start (parallax) and dims; pop is the mirror image. All on the critically
 * damped `spring.page` (1.0 / 220), no scale. Top-level switches crossfade in 150 ms. Under reduced motion every change
 * is a 120 ms crossfade. Slides follow the layout direction (Compose mirrors horizontal offsets in RTL only for
 * layout, so the sign is applied here from [rtl]).
 *
 * @param reduced reduced motion.
 * @param dim the covered page fades towards the window background; on the dark theme's black window this is the
 *   measured dimming (black 0 -> 0.3), on light windows it would brighten the page, so the light theme passes false.
 * @param rtl right-to-left layout.
 */
@Immutable
data class PageTransitions(
    val reduced: Boolean,
    val dim: Boolean,
    val rtl: Boolean = false,
) {
    private val sign: Int get() = if (rtl) -1 else 1

    private fun <T> page() = NbSprings.Page.spec<T>()

    /** Incoming page of a push. */
    fun pushEnter(): EnterTransition =
        if (reduced) fadeIn(tween(NbDurations.REDUCED)) else slideInHorizontally(page<IntOffset>()) { sign * it }

    /** Covered page of a push. */
    fun pushExit(): ExitTransition =
        if (reduced) {
            fadeOut(tween(NbDurations.REDUCED))
        } else {
            slideOutHorizontally(page<IntOffset>()) { -sign * it / PARALLAX } + covered()
        }

    /** Uncovered page of a pop. */
    fun popEnter(): EnterTransition =
        if (reduced) {
            fadeIn(tween(NbDurations.REDUCED))
        } else {
            slideInHorizontally(page<IntOffset>()) { -sign * it / PARALLAX } + uncovered()
        }

    /** Leaving page of a pop. */
    fun popExit(): ExitTransition =
        if (reduced) fadeOut(tween(NbDurations.REDUCED)) else slideOutHorizontally(page<IntOffset>()) { sign * it }

    /** Top-level switch: a short crossfade. */
    fun switchEnter(): EnterTransition = fadeIn(tween(if (reduced) NbDurations.REDUCED else NbDurations.SHORT, easing = NbEasing.Standard))

    /** Top-level switch: a short crossfade. */
    fun switchExit(): ExitTransition = fadeOut(tween(if (reduced) NbDurations.REDUCED else NbDurations.SHORT, easing = NbEasing.Standard))

    /** A push ([forward]) or pop for `AnimatedContent`. */
    fun content(forward: Boolean): ContentTransform =
        if (forward) {
            pushEnter() togetherWith pushExit()
        } else {
            popEnter() togetherWith
                popExit()
        }

    private fun covered(): ExitTransition = if (dim) fadeOut(page(), targetAlpha = 1f - DIM) else ExitTransition.None

    private fun uncovered(): EnterTransition = if (dim) fadeIn(page(), initialAlpha = 1f - DIM) else EnterTransition.None

    private companion object {
        /** The covered page moves a third of the width. */
        const val PARALLAX = 3

        /** Dimming of the covered page ([estimate], `coloros-conformance.md` P1). */
        const val DIM = 0.3f
    }
}

/** The [PageTransitions] of the current theme, motion setting and layout direction. */
@Composable
fun rememberPageTransitions(): PageTransitions {
    val reduced = NbTheme.motion.reduced
    val dark = NbTheme.colors.isDark
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return remember(reduced, dark, rtl) { PageTransitions(reduced, dim = dark, rtl = rtl) }
}
