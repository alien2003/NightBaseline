package io.github.alien2003.nightbaseline.ui.interaction

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Semantics key on every scroll container of the framework (its id, required and unique on a screen): tests enumerate
 * the containers of a screen through it, so a new screen's containers are covered by the scroll invariants without
 * being listed (ADR 0011 I4). A container cannot exist without it: the boundary check forbids every other scroll API.
 */
val NbScrollContainer = SemanticsPropertyKey<String>("NbScrollContainer")

private var SemanticsPropertyReceiver.nbScrollContainer by NbScrollContainer

/**
 * The one fling of the app (ADR 0011): the platform spline decay of `ScrollableDefaults.flingBehavior()`, wrapped so
 * every fling a framework container starts is reported to [NbMotionProbe]. ColorOS fling feel is unmeasured, so the
 * platform decay is kept [decision].
 */
@Composable
internal fun rememberNbFling(id: String): FlingBehavior {
    val base = ScrollableDefaults.flingBehavior()
    return remember(base, id) { NbFling(base, id) }
}

private class NbFling(
    private val base: FlingBehavior,
    private val id: String,
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        NbMotionProbe.flings?.invoke(id, initialVelocity)
        val ends = NbMotionProbe.flingEnds ?: return with(base) { performFling(initialVelocity) }
        // Tests: the distance the fling itself scrolled (what the content consumed), exact for lazy lists too.
        var travel = 0f
        val outer = this
        val counting =
            object : ScrollScope {
                override fun scrollBy(pixels: Float): Float = outer.scrollBy(pixels).also { travel += it }
            }
        val left = with(base) { counting.performFling(initialVelocity) }
        ends(id, travel)
        return left
    }
}

/** A saved scroll state for a framework container. */
@Composable
fun rememberNbScrollState(initial: Int = 0): ScrollState = rememberScrollState(initial)

/** A saved lazy list state for [NbLazyColumn]. */
@Composable
fun rememberNbLazyListState(): LazyListState = rememberLazyListState()

/**
 * A vertically scrolling container of the framework: the one fling, the one overscroll (from the theme), and the
 * container key for tests. The only way to scroll a column in the app.
 */
@Composable
fun Modifier.nbVerticalScroll(
    state: ScrollState,
    enabled: Boolean = true,
    id: String,
): Modifier {
    val fling = rememberNbFling(id)
    return semantics { nbScrollContainer = id }.verticalScroll(state, enabled, flingBehavior = fling)
}

/** A horizontally scrolling container of the framework (the calendar's transposed grid). */
@Composable
fun Modifier.nbHorizontalScroll(
    state: ScrollState,
    id: String,
): Modifier {
    val fling = rememberNbFling(id)
    return semantics { nbScrollContainer = id }.horizontalScroll(state, flingBehavior = fling)
}

/** A lazy column of the framework: the one fling, the one overscroll, the container key for tests. */
@Composable
fun NbLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberNbLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    id: String,
    content: LazyListScope.() -> Unit,
) {
    val fling = rememberNbFling(id)
    LazyColumn(
        modifier = modifier.semantics { nbScrollContainer = id },
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        flingBehavior = fling,
        content = content,
    )
}
