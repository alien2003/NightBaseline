package io.github.alien2003.nightbaseline.ui.interaction

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import io.github.alien2003.nightbaseline.ui.theme.NbTheme
import kotlin.math.roundToInt
import kotlin.math.sign

/** The layout side pages come from: +1 (right) in LTR, -1 in RTL. */
@Composable
internal fun layoutSide(): Int = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1

/** A [NbTransition] tied to the caller, with the theme's dim and the motion configuration applied. */
@Composable
internal fun rememberTransition(id: String): NbTransition {
    val scope = rememberCoroutineScope()
    val router = LocalNbBackRouter.current
    val transition = remember(scope, router) { NbTransition(id, scope).also { it.reprojecting = { router?.reprojecting == true } } }
    // Snapshot state: a discarded composition rolls these writes back (V4).
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

/**
 * The plain memory of a host: the state it shows, the keys and values it knew, the page it parked. Never read by
 * drawing. It is written only after a composition has been applied ([SideEffect]) or outside composition (measure,
 * effects): a composition that is discarded after it set up a change rolls back its snapshot writes, and the memory
 * still holds the old state, so the next composition sets the change up again (ADR 0011: nothing of a thrown-away
 * composition leaks out; review V4: the hosts still wrote values, visited sections and their targets' side in
 * composition).
 */
private class HostMemory<T> {
    var top: Any? = null
    var keys: List<Any> = emptyList()
    val saved = HashSet<Any>()

    /** The values of the keys of applied compositions: a leaving layer still draws its value. */
    val values = HashMap<Any, T>()

    /** The page parked below the top while at rest (kept composed through the next change, review V8). */
    var parked: Any? = null

    /** Layers that have content to draw (rule R8), written by [NbLayer]: in its measure, and false when it leaves. */
    val withContent = HashSet<Any>()

    /** The host's width in px (the travel of a back gesture over it). */
    var width: Float = 0f
}

/**
 * A stack of pages (ADR 0011, kind Page, relation Depth): app routes, settings sub-pages, onboarding steps. The top of
 * [stack] is shown; a change of the top is a push when the old top is still below it (or gone without the new top
 * having been below: a reset) and a pop when the new top was below. The change runs on the Nav spring with the Page
 * pose; a back gesture drives the same p under the finger and a release continues from (p, v). The page below the top
 * is composed, placed and drawn ahead in an invisible layer (parked), and a push waits for its page's content (R8).
 *
 * Back: while the stack has more than one page and the host's branch is shown, the host is a target of the app's
 * router; Back (gesture or not) calls [onBack], which must pop [stack].
 *
 * @param saveable keep each page's saveable state (removed when the page leaves the stack); false when [content]
 *   provides its own (navigation entries).
 * @param onSettled runs after every change has settled and its leaving page is gone.
 */
@Suppress("CyclomaticComplexMethod") // One host: change, back target, settle, parked page, layers.
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
    val memory = remember { HostMemory<T>() }
    val holder = rememberSaveableStateHolder()
    val keys = stack.map(key)
    val current = stack.associateBy(key)
    val top = keys.lastOrNull() ?: return
    val previous = memory.top
    if (previous != null && previous != top) {
        val pop = top in memory.keys && previous !in keys
        transition.go(previous, top, NbKind.Page, toIsUpper = !pop, e = side)
    }
    val back by rememberUpdatedState(onBack)
    val canBack = rememberUpdatedState(backEnabled && keys.size > 1)
    val scope = LocalNbBackScope.current
    val target =
        remember(transition, scope) {
            StackBackTarget(transition, scope, memory, canBack, side) { back() }
        }
    SideEffect {
        memory.top = top
        memory.keys = keys
        memory.values.putAll(current)
        target.side = side
        transition.hasContent = { it in memory.withContent }
    }
    RunPending(transition, transition.runId)
    @Suppress("UNUSED_VARIABLE")
    val version = transition.version
    val layers = transition.layers(top)
    RegisterBackTarget(target)
    val settled = rememberUpdatedState(onSettled)
    LaunchedEffect(transition.settled) {
        // Only at rest: a change set up after the one that settled (a fold's snap right after a dropped push) still
        // shows its leaving page, whose entry must stay alive until that change settles too (V7).
        if (transition.running) return@LaunchedEffect
        memory.values.keys.retainAll((keys + layers).toSet())
        if (saveable) memory.forgotten(holder, keys)
        settled.value()
        // A Back that came while a deferred Back ran (R9) runs now, from the state that one left.
        if (transition.takeQueuedBack() && target.enabled()) target.back()
    }
    // The page below the top is composed, measured, placed and drawn ahead, parked (an invisible layer: no semantics,
    // no input, no focus): a back gesture or a pop starts on a page whose layer and display lists already exist, so
    // its first frame only changes layer properties (V5, R1-3). While a change runs, the page parked before it stays
    // composed, and the page that will be below the top after a pop is composed ahead (review V8: the parked page was
    // disposed in the push's first frame, and the new page below was composed from scratch at a pop's settle).
    val below = keys.getOrNull(keys.size - 2)
    val parkedKeys =
        buildList {
            if (transition.running) {
                memory.parked?.let { if (it in keys) add(it) }
                keys.getOrNull(keys.size - BELOW_NEXT)?.let { add(it) }
            }
            below?.let { add(it) }
        }.filter { it !in layers }.distinct()
    SideEffect { if (!transition.running) memory.parked = below }
    val composed = parkedKeys + layers
    Box(modifier.fillMaxSize().onSizeChanged { memory.width = it.width.toFloat() }) {
        composed.forEach { k ->
            key(k) {
                val value = current[k] ?: memory.values[k]
                val child = rememberChildScope { memory.top == k && !transition.deferred }
                val isTop = k == top
                val parked = k !in layers
                NbLayer(
                    onContent = { has -> if (has) memory.withContent += k else memory.withContent -= k },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .evictFocus(hidden = !isTop)
                            .zIndex(if (k == layers.last()) 1f else 0f)
                            .poseLayer(transition, k, parked = if (parked) id else null)
                            .blockInput { memory.top != k || transition.inputBlocked }
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

/** The page two below the top: below the top once the top is popped. */
private const val BELOW_NEXT = 3

/** Saveable state of pages that left the stack is dropped. */
private fun HostMemory<*>.forgotten(
    holder: SaveableStateHolder,
    keys: List<Any>,
) {
    saved.filter { it !in keys }.forEach(holder::removeState)
    saved.retainAll(keys.toSet())
    saved.addAll(keys)
}

/**
 * The Back target of a [NbStackHost]: pops the top page, previewed under the finger. Every Back (gesture or not)
 * moves first and pops when its motion has settled (R9).
 */
private class StackBackTarget(
    private val transition: NbTransition,
    private val scope: NbBackScope,
    private val memory: HostMemory<*>,
    private val canBack: State<Boolean>,
    var side: Int,
    private val onBack: () -> Unit,
) : NbBackTarget {
    private var token = -1
    private val finger = NbBackFinger()
    override val depth: Int get() = scope.depth + 1

    override fun enabled(): Boolean = canBack.value && scope.active()

    override fun start(
        edge: Int,
        touchX: Float,
    ) {
        token = -1
        // A Back still moving lands first: this gesture goes back from the page that Back leads to (R9).
        val keys = if (transition.land()) memory.keys.dropLast(1) else memory.keys
        if (keys.size < 2) return
        finger.start(edge, touchX)
        token = transition.beginGesture(keys.last(), keys[keys.size - 2], NbKind.Page, toIsUpper = false, e = edgeSide(edge, side))
    }

    override fun progress(move: NbBackMove) = transition.drag(token, finger.share(move, memory.width), move.timeMillis)

    override fun cancel() {
        transition.release(token, commit = false)
    }

    override fun commit() {
        // A gesture that never started here (the platform committed without a start): a Back like any other.
        if (token == -1) back() else transition.release(token, commit = true, then = onBack)
        token = -1
    }

    override fun back() {
        val keys = memory.keys
        if (keys.size < 2) {
            onBack()
            return
        }
        transition.goBack(keys.last(), keys[keys.size - 2], NbKind.Page, toIsUpper = false, e = side, then = onBack)
    }

    override fun abort() = transition.abort()
}

/**
 * A layer that stops being the shown one gives up the focus it holds (review V6: a hidden layer stopped focus from
 * entering, but a focused element inside it kept the focus, so its key handlers still answered for a page not shown).
 */
@Composable
private fun Modifier.evictFocus(hidden: Boolean): Modifier {
    val manager = LocalFocusManager.current
    val holds = remember { BooleanArray(1) }
    LaunchedEffect(hidden) { if (hidden && holds[0]) manager.clearFocus(force = true) }
    return onFocusChanged { holds[0] = it.hasFocus }
}

/** A layer that is not the shown one: no semantics (screen readers, tests) and no focus can enter it. */
private val Hidden: Modifier =
    Modifier
        .clearAndSetSemantics {}
        .focusProperties { onEnter = { cancelFocusChange() } }
        .focusGroup()

/**
 * One layer of a host: its content stacked as in a `Box` (top start, loose constraints), and whether that content
 * emitted anything to draw (rule R8: a change waits for its entering layer's content). [onContent] runs in every
 * measure of the layer, and with false when the layer leaves the composition.
 */
@Composable
internal fun NbLayer(
    onContent: (Boolean) -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    // A layer that leaves the composition has no content any more (review V7: a key that came back reported the
    // content of its old layer, so its change skipped the wait for its new one).
    val gone by rememberUpdatedState(onContent)
    DisposableEffect(Unit) { onDispose { gone(false) } }
    Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        onContent(placeables.isNotEmpty())
        val width = maxOf(constraints.minWidth, placeables.maxOfOrNull { it.width } ?: 0)
        val height = maxOf(constraints.minHeight, placeables.maxOfOrNull { it.height } ?: 0)
        layout(width, height) { placeables.forEach { it.placeRelative(0, 0) } }
    }
}

/**
 * Top-level sections in bar order (ADR 0011, kind Tab, relation Section; ADR 0006): every visited section stays
 * composed; a change of [selected] runs the Page geometry with the higher bar index as the upper layer, on the Nav
 * spring, whatever triggered it (bar, rail, keyboard, "See all", a gesture back to the first section). Visited
 * sections that are not shown are parked: measured, placed and drawn ahead in an invisible layer, with no semantics,
 * input or focus.
 *
 * Back: while a section other than the first is shown and the host's branch is shown, Back (gesture or not) calls
 * [onBackToFirst]; the gesture drives the same p (the section slides towards the end side, the first one comes back
 * from under it).
 *
 * @param content one section; `active` reads true while it is the shown one (read it outside composition).
 */
@Suppress("CyclomaticComplexMethod") // One host: change, back target, parked sections, layers.
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
    val previous = memory.selected
    if (previous != null && previous != selected) {
        val upper = order.indexOf(selected) > order.indexOf(previous)
        transition.go(previous, selected, NbKind.Tab, toIsUpper = upper, e = side)
    }
    RunPending(transition, transition.runId)
    @Suppress("UNUSED_VARIABLE")
    val version = transition.version
    val layers = transition.layers(selected)

    // The sections to compose: every one visited before (applied compositions only) and the ones of this change.
    @Suppress("UNCHECKED_CAST")
    val visited =
        LinkedHashSet<K>(memory.visited).apply {
            add(selected)
            addAll(layers as List<K>)
        }
    val back by rememberUpdatedState(onBackToFirst)
    val canBack = rememberUpdatedState(selected != order.first())
    val scope = LocalNbBackScope.current
    val target = remember(transition, scope) { SectionBackTarget(transition, scope, memory, canBack, side) { back() } }
    SideEffect {
        memory.selected = selected
        memory.first = order.first()
        memory.visited.addAll(visited)
        target.side = side
        transition.hasContent = { it in memory.withContent }
    }
    RegisterBackTarget(target)
    LaunchedEffect(transition.settled) {
        if (!transition.running && transition.takeQueuedBack() && target.enabled()) target.back()
    }
    // A deferred Back to the first section (R9) shows its target in the fixed elements (the bar) from its start, as a
    // tap on the bar does.
    @Suppress("UNCHECKED_CAST")
    val shownSelection = (transition.committedTo as? K) ?: selected
    Layout(
        modifier = modifier.onSizeChanged { memory.width = it.width.toFloat() },
        content = {
            visited.forEach { section ->
                key(section) {
                    val active = remember(section) { { memory.selected == section && !transition.deferred } }
                    val child = rememberChildScope(active)
                    val shown = section in layers
                    val shift = remember(section) { SectionShift(transition, section) }
                    NbLayer(
                        onContent = { has -> if (has) memory.withContent += section else memory.withContent -= section },
                        modifier =
                            Modifier
                                .layoutId(section)
                                .evictFocus(hidden = !(shown && section == selected))
                                .poseLayer(transition, section, shift, parked = if (shown) null else id)
                                .blockInput { memory.selected != section || transition.inputBlocked }
                                .then(if (shown && section == selected) Modifier else Hidden),
                    ) {
                        CompositionLocalProvider(
                            LocalNbBackScope provides child,
                            LocalSectionShift provides shift,
                            LocalSectionSelected provides shownSelection,
                        ) {
                            holder.SaveableStateProvider(section.toString()) { content(section, active) }
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        // Every visited section is measured, placed and drawn: the ones not in the change are parked (invisible, no
        // semantics, no input, no focus), so a tab change or the gesture back to the first section starts on a layer
        // that already exists (the same rule as the stack's page below; review: the section host measured only the
        // layers of the running change, so its first frame laid out the whole target section).
        val placeables: List<Pair<Any?, Placeable>> = measurables.map { it.layoutId to it.measure(constraints) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach { (id, placeable) ->
                val z =
                    when (id) {
                        layers.last() -> 1f
                        in layers -> 0f
                        else -> -1f
                    }
                placeable.place(0, 0, zIndex = z)
            }
        }
    }
}

private class SectionMemory<K : Any> {
    var selected: K? = null
    var first: K? = null

    /** Every section shown so far (ADR 0006: they stay composed), written after each applied composition. */
    val visited = LinkedHashSet<K>()

    /** Sections that have content to draw (rule R8). */
    val withContent = HashSet<Any>()

    /** The host's width in px (the travel of a back gesture over it). */
    var width: Float = 0f
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
    private val finger = NbBackFinger()
    override val depth: Int get() = scope.depth + 1

    override fun enabled(): Boolean = canBack.value && scope.active()

    override fun start(
        edge: Int,
        touchX: Float,
    ) {
        finger.start(edge, touchX)
        val landed = transition.land()
        val shown = if (landed) memory.first else memory.selected
        val first = memory.first
        // Tab: the direction comes from the bar order, whatever the edge (the first section is always at the start).
        token =
            if (shown != null && first != null && shown != first) {
                transition.beginGesture(shown, first, NbKind.Tab, toIsUpper = false, e = side)
            } else {
                -1
            }
    }

    override fun progress(move: NbBackMove) = transition.drag(token, finger.share(move, memory.width), move.timeMillis)

    override fun cancel() {
        transition.release(token, commit = false)
    }

    override fun commit() {
        if (token == -1) back() else transition.release(token, commit = true, then = onBack)
        token = -1
    }

    override fun back() {
        val shown = memory.selected
        val first = memory.first
        if (shown == null || first == null || shown == first) {
            onBack()
            return
        }
        transition.goBack(shown, first, NbKind.Tab, toIsUpper = false, e = side, then = onBack)
    }

    override fun abort() = transition.abort()
}

/**
 * Content that changes in place (ADR 0011): with an [order] the values are a sequence (nights, months) and a change
 * runs the Pager kind towards the side of the newer value; without one they are unordered peers (a two-pane
 * selection, a view toggle, a filter result) and a change runs the Swap kind. Both on the Nav spring. The leaving
 * value stays composed until p settles; the box clips while a change runs.
 */
@Suppress("CyclomaticComplexMethod") // One host: change (pager or swap), step velocity, layers.
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
    val memory = remember { HostMemory<T>() }
    val k = key(value)
    val previous = memory.top
    val router = LocalNbBackRouter.current
    var handoff: Long? = null
    if (previous != null && previous != k) {
        val old = memory.values[previous]
        if (order != null && old != null) {
            val forward = sign(order.compare(value, old).toFloat()).toInt().takeIf { it != 0 } ?: 1
            // A step made by a swipe continues with the swipe's speed (p per second over this pager's width). Read
            // here, consumed once the composition is applied (V4: a destructive read in composition lost it when the
            // composition was discarded or ran again).
            val step = router?.stepHandoff?.peek()
            handoff = step?.first
            val speed = step?.second ?: 0f
            val v0 = if (memory.width > 0f) speed / memory.width else 0f
            transition.go(previous, k, NbKind.Pager, toIsUpper = true, e = forward * side, v0 = v0)
        } else {
            transition.go(previous, k, NbKind.Swap, toIsUpper = true, e = side)
        }
    }
    SideEffect {
        memory.top = k
        memory.values[k] = value
        handoff?.let { router?.stepHandoff?.consume(it) }
        transition.hasContent = { it in memory.withContent }
    }
    RunPending(transition, transition.runId)
    @Suppress("UNUSED_VARIABLE")
    val version = transition.version
    val layers = transition.layers(k)
    LaunchedEffect(transition.settled) { memory.values.keys.retainAll(layers.toSet() + k) }
    Box(modifier.onSizeChanged { memory.width = it.width.toFloat() }.then(if (layers.size > 1) Modifier.clipToBounds() else Modifier)) {
        layers.forEach { layer ->
            key(layer) {
                val v = if (layer == k) value else memory.values[layer]
                // Each layer is its own back branch: a leaving layer's hosts are no back target (ADR 0011 R4).
                val child = rememberChildScope { memory.top == layer }
                NbLayer(
                    onContent = { has -> if (has) memory.withContent += layer else memory.withContent -= layer },
                    modifier =
                        Modifier
                            .evictFocus(hidden = layer != k)
                            .zIndex(if (layer == k) 1f else 0f)
                            .poseLayer(transition, layer)
                            .blockInput { memory.top != layer }
                            .then(if (layer == k) Modifier else Hidden),
                ) { if (v != null) CompositionLocalProvider(LocalNbBackScope provides child) { content(v) } }
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

/** The section a [NbSectionHost] shows or is changing to; the same value in every section's layer. */
private val LocalSectionSelected = compositionLocalOf<Any?> { null }

/**
 * The selection the fixed elements of a section (the bar, the rail) show: the host's selected section, the same in
 * every section's layer, so during a change both layers' bars show the target from its first frame and animate the
 * same way whichever layer is on top (ADR 0011: the bar is one element of the window). Outside a section host, [own].
 */
@Composable
fun <K : Any> nbShownSection(own: K): K {
    @Suppress("UNCHECKED_CAST")
    return (LocalSectionSelected.current as? K)?.takeIf { it::class == own::class } ?: own
}

/**
 * Keeps an element of a section in place while the sections change (the floating bar and rail, ADR 0011): the bar is
 * one element of the window, so a section change moves the pages under it, not the bar; it shows the target section
 * from the start of the change. Outside a section host it changes nothing.
 */
@Composable
fun Modifier.nbFixedInSection(): Modifier {
    val shift = LocalSectionShift.current ?: return this
    // An offset read at placement, not a layer: the glass bar samples its backdrop exactly as before.
    return offset { IntOffset(-shift.tx().roundToInt(), 0) }
}
