package io.github.alien2003.nightbaseline.nav

import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.motion.NbDurations
import io.github.alien2003.nightbaseline.ui.motion.NbEasing
import io.github.alien2003.nightbaseline.ui.theme.NbTheme

/**
 * The three top-level sections in one navigation destination (ADR 0006). Navigation-compose disposes a destination's
 * composition when another one is shown and rebuilds it on return, which cost about two slow UI-thread frames per tab
 * switch on the owner's unfolded inner screen. Here every section stays composed once visited; a switch only
 * crossfades two already composed (and usually already measured) trees; the hidden ones are neither measured, placed
 * nor drawn and their semantics are cleared, so they are not on screen, read out or reachable by focus.
 *
 * The crossfade (150 ms, 120 ms under reduced motion, `coloros-conformance.md` P1) animates the layers' alpha only:
 * the alpha is read in the draw layer, so a running switch never recomposes a section.
 *
 * Saveable state is kept per section by a [rememberSaveableStateHolder] keyed by the section, so it survives the
 * destination being covered by a pushed page (then only the selected section is rebuilt on return) and process death.
 *
 * @param content one section; `active` reads false while it is hidden (effects that navigate or take Back must check
 *   it; read it outside composition so a switch does not recompose the section).
 */
@Composable
internal fun TopLevelHost(
    selected: TopLevel,
    modifier: Modifier = Modifier,
    content: @Composable (tab: TopLevel, active: () -> Boolean) -> Unit,
) {
    val holder = rememberSaveableStateHolder()
    val current = rememberUpdatedState(selected)
    // Composed sections, in first-visit order. Only the selected one at first: the others are built when first shown.
    // A plain set: it only grows, in the same pass that reads it (no state write back into this composition).
    val visited = remember { LinkedHashSet<TopLevel>() }
    visited += selected
    val reduced = NbTheme.motion.reduced
    val transition = updateTransition(selected, label = "topLevel")
    // Shown: the selected section and, while the crossfade runs, the one it replaces. Changes only at its ends.
    val shown = setOf(transition.currentState, transition.targetState)
    Layout(
        modifier = modifier,
        content = {
            visited.forEach { tab ->
                key(tab) {
                    val alpha = transition.alphaOf(tab, reduced)
                    // Read only where it is used (effects, Back), so a switch does not recompose the sections.
                    val active = remember(tab) { { current.value == tab } }
                    Box(
                        Modifier
                            .layoutId(tab)
                            .graphicsLayer { this.alpha = alpha() }
                            // Unplaced nodes still report semantics: a hidden section must not be read out or found.
                            .then(if (tab in shown) Modifier else Hidden),
                    ) {
                        holder.SaveableStateProvider(tab.name) { content(tab, active) }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val placeables: List<Placeable> = measurables.filter { it.layoutId in shown }.map { it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) { placeables.forEach { it.place(0, 0) } }
    }
}

/** A hidden section: no semantics (screen readers, tests). */
private val Hidden: Modifier = Modifier.clearAndSetSemantics {}

/** The section's alpha in the crossfade, as a function read only in the draw layer. */
@Composable
private fun Transition<TopLevel>.alphaOf(
    tab: TopLevel,
    reduced: Boolean,
): () -> Float {
    val state =
        animateFloat(
            transitionSpec = { tween(if (reduced) NbDurations.REDUCED else NbDurations.SHORT, easing = NbEasing.Standard) },
            label = "alpha",
        ) { if (it == tab) 1f else 0f }
    return remember(state) { { state.value } }
}
