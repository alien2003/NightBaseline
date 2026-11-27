package io.github.alien2003.nightbaseline.ui.interaction

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import kotlin.math.sign

/** The layout side pages come from: +1 (right) in LTR, -1 in RTL. */
@Composable
internal fun layoutSide(): Int = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1

/** A [NbTransition] tied to the caller, with the theme's dim and the motion configuration applied. */
@Composable
internal fun rememberTransition(id: String): NbTransition {
    val scope = rememberCoroutineScope()
    val transition = remember(scope) { NbTransition(id, scope) }
    transition.reduced = LocalNbMotion.current.reduced
    transition.dark = NbTheme.colors.isDark
    return transition
}

/** Runs the change a host set up in this composition (call it after the host's `go`). */
@Composable
internal fun RunPending(
    transition: NbTransition,
    runId: Int,
) {
    LaunchedEffect(transition, runId) { transition.runPending() }
}

/** The plain memory of a host: the state it shows and the keys it knew. Never read by drawing. */
private class HostMemory {
    var top: Any? = null
    var keys: List<Any> = emptyList()
    val saved = HashSet<Any>()
}

/**
 * A stack of pages (ADR 0011, kind Page, relation Depth): app routes, settings sub-pages, onboarding steps. The top of
 * [stack] is shown; a change of the top is a push when the old top is still below it (or gone without the new top
 * having been below: a reset) and a pop when the new top was below. The change runs on the Nav spring with the Page
 * pose; a back gesture drives the same p under the finger and a release continues from (p, v). The lower page of a
 * change is composed only while the change runs.
 *
 * Back: while the stack has more than one page and the host's branch is shown, the host is a target of the app's
 * router; Back (gesture or not) calls [onBack], which must pop [stack].
 *
 * @param saveable keep each page's saveable state (removed when the page leaves the stack); false when [content]
 *   provides its own (navigation entries).
 * @param onSettled runs after every change has settled and its leaving page is gone.
 */
@Composable
fun <T : Any> NbStackHost(
    stack: List<T>,
    key: (T) -> Any,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    id: String = "stack",
    backEnabled: Boolean = true,
    saveable: Boolean = true,
    onSettled: () -> Unit = {},
    content: @Composable (T) -> Unit,
) {
    val transition = rememberTransition(id)
    val side = layoutSide()
    val memory = remember { HostMemory() }
    val values = remember { HashMap<Any, T>() }
    val holder = rememberSaveableStateHolder()
    val keys = stack.map(key)
    stack.forEach { values[key(it)] = it }
    val top = keys.lastOrNull() ?: return
    val previous = memory.top
    if (previous != null && previous != top) {
        val pop = top in memory.keys && previous !in keys
        transition.go(previous, top, NbKind.Page, toIsUpper = !pop, e = side)
    }
    memory.top = top
    memory.keys = keys
    RunPending(transition, transition.runId)
    @Suppress("UNUSED_VARIABLE")
    val version = transition.version
    val layers = transition.layers(top)
    val back by rememberUpdatedState(onBack)
    val canBack = rememberUpdatedState(backEnabled && keys.size > 1)
    val scope = LocalNbBackScope.current
    val target =
        remember(transition, scope) {
            StackBackTarget(transition, scope, memory, canBack, side) { back() }
        }
    target.side = side
    RegisterBackTarget(target)
    val settled = rememberUpdatedState(onSettled)
    LaunchedEffect(transition.settled) {
        values.keys.retainAll((keys + layers).toSet())
        if (saveable) memory.forgotten(holder, keys)
        settled.value()
    }
    Box(modifier.fillMaxSize()) {
        layers.forEach { k ->
            key(k) {
                val value = values[k]
                val child = rememberChildScope { memory.top == k }
                val isTop = k == top
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(if (k == layers.last()) 1f else 0f)
                        .poseLayer(transition, k)
                        .blockInput { memory.top != k || transition.gesture }
                        .then(if (isTop) Modifier else Hidden),
                ) {
                    if (value != null) {
                        CompositionLocalProvider(LocalNbBackScope provides child) {
                            if (saveable) holder.SaveableStateProvider(k) { content(value) } else content(value)
                        }
                    }
                }
            }
        }
    }
}

/** Saveable state of pages that left the stack is dropped. */
private fun HostMemory.forgotten(
    holder: SaveableStateHolder,
    keys: List<Any>,
) {
    saved.filter { it !in keys }.forEach(holder::removeState)
    saved.retainAll(keys.toSet())
    saved.addAll(keys)
}

/** The Back target of a [NbStackHost]: pops the top page, previewed under the finger. */
private class StackBackTarget(
    private val transition: NbTransition,
    private val scope: NbBackScope,
    private val memory: HostMemory,
    private val canBack: State<Boolean>,
    var side: Int,
    private val onBack: () -> Unit,
) : NbBackTarget {
    private var token = -1
    override val depth: Int get() = scope.depth + 1

    override fun enabled(): Boolean = canBack.value && scope.active()

    override fun start(edge: Int) {
        val keys = memory.keys
        if (keys.size < 2) return
        token = transition.beginGesture(keys.last(), keys[keys.size - 2], NbKind.Page, toIsUpper = false, e = edgeSide(edge, side))
    }

    override fun progress(
        fraction: Float,
        timeMillis: Long,
    ) = transition.drag(token, fraction, timeMillis)

    override fun cancel() {
        transition.release(token, commit = false)
    }

    override fun commit() {
        if (!transition.gesture) return
        onBack()
        transition.release(token, commit = true)
    }

    override fun back() = onBack()

    override fun abort() = transition.abort()
}

/** A hidden layer: no semantics (screen readers, tests). */
private val Hidden: Modifier = Modifier.clearAndSetSemantics {}

/**
 * Top-level sections in bar order (ADR 0011, kind Tab, relation Section; ADR 0006): every visited section stays
 * composed; a change of [selected] runs the Page geometry with the higher bar index as the upper layer, on the Nav
 * spring, whatever triggered it (bar, rail, keyboard, "See all", a gesture back to the first section). Hidden
 * sections are neither measured, placed nor drawn, and their semantics are cleared.
 *
 * Back: while a section other than the first is shown and the host's branch is shown, Back (gesture or not) calls
 * [onBackToFirst]; the gesture drives the same p (the section slides towards the end side, the first one comes back
 * from under it).
 *
 * @param content one section; `active` reads true while it is the shown one (read it outside composition).
 */
@Composable
fun <K : Any> NbSectionHost(
    selected: K,
    order: List<K>,
    onBackToFirst: () -> Unit,
    modifier: Modifier = Modifier,
    id: String = "sections",
    content: @Composable (section: K, active: () -> Boolean) -> Unit,
) {
    val transition = rememberTransition(id)
    val side = layoutSide()
    val holder = rememberSaveableStateHolder()
    val memory = remember { SectionMemory<K>() }
    val visited = remember { LinkedHashSet<K>() }
    visited += selected
    val previous = memory.selected
    if (previous != null && previous != selected) {
        val upper = order.indexOf(selected) > order.indexOf(previous)
        transition.go(previous, selected, NbKind.Tab, toIsUpper = upper, e = side)
    }
    memory.selected = selected
    memory.first = order.first()
    RunPending(transition, transition.runId)
    @Suppress("UNUSED_VARIABLE")
    val version = transition.version
    val layers = transition.layers(selected)
    @Suppress("UNCHECKED_CAST")
    (layers as List<K>).forEach { visited += it }
    val back by rememberUpdatedState(onBackToFirst)
    val canBack = rememberUpdatedState(selected != order.first())
    val scope = LocalNbBackScope.current
    val target = remember(transition, scope) { SectionBackTarget(transition, scope, memory, canBack, side) { back() } }
    target.side = side
    RegisterBackTarget(target)
    Layout(
        modifier = modifier,
        content = {
            visited.forEach { section ->
                key(section) {
                    val active = remember(section) { { memory.selected == section } }
                    val child = rememberChildScope(active)
                    val shown = section in layers
                    val shift = remember(section) { SectionShift(transition, section) }
                    Box(
                        Modifier
                            .layoutId(section)
                            .poseLayer(transition, section, shift)
                            .blockInput { memory.selected != section || transition.gesture }
                            .then(if (shown && section == selected) Modifier else Hidden),
                    ) {
                        CompositionLocalProvider(LocalNbBackScope provides child, LocalSectionShift provides shift) {
                            holder.SaveableStateProvider(section.toString()) { content(section, active) }
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val placeables: List<Pair<Any?, Placeable>> =
            measurables.filter { it.layoutId in layers }.map { it.layoutId to it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { (id, placeable) -> placeable.place(0, 0, zIndex = if (id == layers.last()) 1f else 0f) }
        }
    }
}

private class SectionMemory<K : Any> {
    var selected: K? = null
    var first: K? = null
}

/** The Back target of a [NbSectionHost]: back to the first section. */
private class SectionBackTarget<K : Any>(
    private val transition: NbTransition,
    private val scope: NbBackScope,
    private val memory: SectionMemory<K>,
    private val canBack: State<Boolean>,
    var side: Int,
    private val onBack: () -> Unit,
) : NbBackTarget {
    private var token = -1
    override val depth: Int get() = scope.depth + 1

    override fun enabled(): Boolean = canBack.value && scope.active()

    override fun start(edge: Int) {
        val shown = memory.selected
        val first = memory.first
        // Tab: the direction comes from the bar order, whatever the edge (the first section is always at the start).
        if (shown != null && first != null && shown != first) {
            token = transition.beginGesture(shown, first, NbKind.Tab, toIsUpper = false, e = side)
        }
    }

    override fun progress(
        fraction: Float,
        timeMillis: Long,
    ) = transition.drag(token, fraction, timeMillis)

    override fun cancel() {
        transition.release(token, commit = false)
    }

    override fun commit() {
        if (!transition.gesture) return
        onBack()
        transition.release(token, commit = true)
    }

    override fun back() = onBack()

    override fun abort() = transition.abort()
}

/**
 * Content that changes in place (ADR 0011): with an [order] the values are a sequence (nights, months) and a change
 * runs the Pager kind towards the side of the newer value; without one they are unordered peers (a two-pane
 * selection, a view toggle, a filter result) and a change runs the Swap kind. Both on the Nav spring. The leaving
 * value stays composed until p settles; the box clips while a change runs.
 */
@Composable
fun <T : Any> NbSwitcher(
    value: T,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    id: String = "switcher",
    order: Comparator<T>? = null,
    content: @Composable (T) -> Unit,
) {
    val transition = rememberTransition(id)
    val side = layoutSide()
    val memory = remember { HostMemory() }
    val values = remember { HashMap<Any, T>() }
    val k = key(value)
    values[k] = value
    val previous = memory.top
    if (previous != null && previous != k) {
        val old = values[previous]
        if (order != null && old != null) {
            val forward = sign(order.compare(value, old).toFloat()).toInt().takeIf { it != 0 } ?: 1
            transition.go(previous, k, NbKind.Pager, toIsUpper = true, e = forward * side)
        } else {
            transition.go(previous, k, NbKind.Swap, toIsUpper = true, e = side)
        }
    }
    memory.top = k
    RunPending(transition, transition.runId)
    @Suppress("UNUSED_VARIABLE")
    val version = transition.version
    val layers = transition.layers(k)
    LaunchedEffect(transition.settled) { values.keys.retainAll(layers.toSet() + k) }
    Box(modifier.then(if (layers.size > 1) Modifier.clipToBounds() else Modifier)) {
        layers.forEach { layer ->
            key(layer) {
                val v = values[layer]
                Box(
                    Modifier
                        .zIndex(if (layer == k) 1f else 0f)
                        .poseLayer(transition, layer)
                        .blockInput { memory.top != layer }
                        .then(if (layer == k) Modifier else Hidden),
                ) { if (v != null) content(v) }
            }
        }
    }
}

/** The horizontal shift of one section's layer, for the parts of the section that stay in place (the floating bar). */
internal class SectionShift(
    private val transition: NbTransition,
    private val key: Any,
) {
    var width: Float = 0f

    /** The layer's translation now. Reads snapshot state: call it in a layer lambda. */
    fun tx(): Float = transition.poseOf(key, width).tx
}

private val LocalSectionShift = staticCompositionLocalOf<SectionShift?> { null }

/**
 * Keeps an element of a section in place while the sections change (the floating bar and rail, ADR 0011): the bar is
 * one element of the window, so a section change moves the pages under it, not the bar; it shows the target section
 * from the start of the change. Outside a section host it changes nothing.
 */
@Composable
fun Modifier.nbFixedInSection(): Modifier {
    val shift = LocalSectionShift.current ?: return this
    return graphicsLayer { translationX = -shift.tx() }
}
