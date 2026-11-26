package io.github.alien2003.nightbaseline.nav

import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.clearAndSetSemantics
import io.github.alien2003.nightbaseline.ui.components.TopLevel
import io.github.alien2003.nightbaseline.ui.motion.NB_PAGE_DIM
import io.github.alien2003.nightbaseline.ui.motion.NB_PAGE_PARALLAX
import io.github.alien2003.nightbaseline.ui.motion.NbDurations
import io.github.alien2003.nightbaseline.ui.motion.NbEasing
import io.github.alien2003.nightbaseline.ui.shell.BackPreview
import io.github.alien2003.nightbaseline.ui.shell.BackPreviewProbe
import io.github.alien2003.nightbaseline.ui.shell.backPreviewPage
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import kotlinx.coroutines.flow.first

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
 * Predictive back to Home (ADR 0009): while [peek] is set, that section is composed and placed under the selected one
 * in the covered pose of a pop (parallax and, on the dark theme, the dim), view only; the selected section draws the
 * [preview] pose of its key (the section). A commit crossfades the leaving section out in its pose while the
 * uncovered one springs to rest, then [onPeekDone] ends the peek.
 *
 * @param content one section; `active` reads false while it is hidden (effects that navigate or take Back must check
 *   it; read it outside composition so a switch does not recompose the section).
 */
@Composable
internal fun TopLevelHost(
    selected: TopLevel,
    modifier: Modifier = Modifier,
    preview: BackPreview? = null,
    peek: TopLevel? = null,
    onPeekDone: () -> Unit = {},
    content: @Composable (tab: TopLevel, active: () -> Boolean) -> Unit,
) {
    val holder = rememberSaveableStateHolder()
    val current = rememberUpdatedState(selected)
    val peeked = rememberUpdatedState(peek)
    // Composed sections, in first-visit order. Only the selected one at first: the others are built when first shown.
    // A plain set: it only grows, in the same pass that reads it (no state write back into this composition).
    val visited = remember { LinkedHashSet<TopLevel>() }
    visited += selected
    if (peek != null) visited += peek
    val reduced = NbTheme.motion.reduced
    val dim = NbTheme.colors.isDark
    val transition = updateTransition(selected, label = "topLevel")
    // Shown: the selected section and, while the crossfade runs, the one it replaces. Changes only at its ends.
    val shown = setOf(transition.currentState, transition.targetState)
    // Placed: also the section a back gesture uncovers (view only: its semantics stay cleared).
    val placed = if (peek != null) shown + peek else shown
    if (preview != null && peek != null) PeekSettle(transition, preview, peek, onPeekDone)
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
                            .graphicsLayer {
                                if (preview != null && peeked.value == tab) {
                                    // The uncovered section in the covered pose of a pop, at rest after a commit.
                                    val under = preview.under.value
                                    BackPreviewProbe.underSink?.invoke(tab, under)
                                    translationX = -preview.direction * NB_PAGE_PARALLAX * size.width * under
                                    this.alpha = if (dim) 1f - NB_PAGE_DIM * under else 1f
                                } else {
                                    this.alpha = alpha()
                                }
                            }.then(if (preview != null) Modifier.backPreviewPage(preview, tab) else Modifier)
                            // Unplaced nodes still report semantics: a hidden section must not be read out or found.
                            .then(if (tab in shown) Modifier else Hidden),
                    ) {
                        holder.SaveableStateProvider(tab.name) { content(tab, active) }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val placeables: List<Pair<Any?, Placeable>> =
            measurables.filter { it.layoutId in placed }.map { it.layoutId to it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            // The uncovered section lies under the leaving one, whatever the visit order.
            placeables.forEach { (id, placeable) -> placeable.place(0, 0, zIndex = if (id == peek) -1f else 0f) }
        }
    }
}

/**
 * After a committed back gesture (the uncovered [peek] is now the target): clears the leaving section's pose once the
 * crossfade is over and ends the peek once the uncovered section is at rest.
 */
@Composable
private fun PeekSettle(
    transition: Transition<TopLevel>,
    preview: BackPreview,
    peek: TopLevel,
    onPeekDone: () -> Unit,
) {
    val done by rememberUpdatedState(onPeekDone)
    val committed = transition.targetState == peek
    LaunchedEffect(committed, peek) {
        if (!committed) return@LaunchedEffect
        snapshotFlow { transition.currentState == transition.targetState }.first { it }
        preview.key?.let(preview::release)
        snapshotFlow { preview.under.value }.first { it <= 0f }
        done()
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
