// The hosts of ADR 0011: stack, sections, switcher, swap, their sync and back target.
@file:Suppress("TooManyFunctions")

package io.github.alien2003.nightbaseline.ui.interaction

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
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

/** A [NbTransition] tied to the caller on the logical stack [initial], with the theme's dim and the motion set up. */
@Composable
internal fun rememberTransition(
    id: String,
    initial: List<Any>,
): NbTransition {
    val scope = rememberCoroutineScope()
    val router = LocalNbBackRouter.current
    val transition =
        remember(scope, router) { NbTransition(id, scope, initial).also { it.reprojecting = { router?.reprojecting == true } } }
    // Snapshot state: a discarded composition rolls these writes back (V4).
    transition.reduced = LocalNbMotion.current.reduced
    transition.dark = NbTheme.colors.isDark
    return transition
}

/** The caller's last state a host saw in an applied composition. */
private open class SyncMemory {
    var seen: List<Any>? = null
}

/**
 * The caller's state of a host changed to [stack] (a tap, a link, the bar, a key, a view model's step): one Go event
 * into the host's state machine, in this composition ([go] builds it from the machine's logical stack). A change the
 * machine already made (a Back the host applied itself, whose caller then follows) is a no-op there. Only applied
 * compositions are remembered.
 */
@Composable
private fun HostSync(
    transition: NbTransition,
    memory: SyncMemory,
    stack: List<Any>,
    go: (logical: List<Any>) -> NbNavEvent.Go,
) {
    val seen = memory.seen
    if (seen != null && seen != stack) {
        val logical = transition.logical
        if (stack != logical) transition.dispatch(go(logical), inComposition = true)
    }
    SideEffect {
        memory.seen = stack
        // A motion set up in this composition gets its frames from now on (its first frame was drawn pre-advanced, R7).
        transition.ensurePump()
    }
    // M4: a Back of the machine is acknowledged explicitly: the caller's state reaching the stack the Back led to
    // clears it. A caller that does not follow within the timeout is reported ([NbMotionProbe.unfollowedBack]: tests
    // fail on it) and then followed by the machine; there is no frame-count resync.
    val pending = transition.pendingFollow
    if (pending != null && stack == pending.first) SideEffect { transition.acknowledge(stack) }
    val latest by rememberUpdatedState(stack)
    val goNow by rememberUpdatedState(go)
    LaunchedEffect(pending) {
        val (expected, at) = pending ?: return@LaunchedEffect
        while (NbClock.nowNanos() - at < FOLLOW_TIMEOUT_NANOS) withFrameNanos { }
        if (transition.pendingFollow?.first != expected || latest == expected) return@LaunchedEffect
        transition.acknowledge(expected)
        NbMotionProbe.reportUnfollowed(transition.hostId, expected, latest)
        if (latest != transition.state.stack) transition.dispatch(goNow(transition.state.stack))
    }
}

/** How long a caller may take to follow a Back of the machine before it is reported and followed (M4), ns. */
private const val FOLLOW_TIMEOUT_NANOS = 500_000_000L

/**
 * The plain memory of a host: the values it knew, the page it parked. Never read by drawing. It is written only after
 * a composition has been applied ([SideEffect]) or outside composition (measure, effects): a composition that is
 * discarded rolls back its snapshot writes, and the memory still holds the old state (review V4).
 */
private class HostMemory<T> : SyncMemory() {
    val saved = HashSet<Any>()

    /** The values of the keys of applied compositions: a leaving layer still draws its value. */
    val values = HashMap<Any, T>()

    /** The pages parked below the top while at rest (kept composed through the next change, review V8). */
    val parkedSet = LinkedHashSet<Any>()

    /** The host's width in px (the travel of a back gesture over it). */
    var width: Float = 0f
}

/**
 * A stack of pages (ADR 0011, kind Page, relation Depth): app routes, settings sub-pages, onboarding steps. The host
 * holds the logical stack in its navigation state machine ([NbNavReducer]); a change of [stack] by the caller is one
 * event into it (a push when the old top is still below the new one or gone without the new top having been below,
 * a pop when the new top was below), and so is every Back: a back gesture's start, moves, cancel and commit, and Back
 * without a gesture (button, Esc, Alt+Left, 3-button, TalkBack). A Back is applied to the machine at once and then
 * reported to the caller through [onBack], which must pop [stack]; the caller's pop is then a no-op for the machine.
 * The page below the top is composed, placed and drawn ahead in an invisible layer (parked), so a gesture's first
 * frame only changes layer properties.
 *
 * @param saveable keep each page's saveable state (removed when the page leaves the stack); false when [content]
 *   provides its own (navigation entries).
 * @param onSettled runs after every motion has settled and its leaving page is gone (the host is at rest).
 */
@Suppress("LongParameterList") // The host's parameters.
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
) = StackHost(stack, key, onBack, modifier, id, backEnabled, saveable, onSettled, machine = null, content)

/** [NbStackHost] with access to its state machine ([machine]: the app's pages dispatch their own pushes at once). */
@Suppress("CyclomaticComplexMethod", "LongMethod", "LongParameterList") // One host: sync, back target, settle, layers.
@Composable
internal fun <T : Any> StackHost(
    stack: List<T>,
    key: (T) -> Any,
    onBack: () -> Unit,
    modifier: Modifier,
    id: String,
    backEnabled: Boolean,
    saveable: Boolean,
    onSettled: () -> Unit,
    machine: ((NbTransition, Int) -> Unit)?,
    content: @Composable (T) -> Unit,
) {
    val keys = stack.map(key)
    if (keys.isEmpty()) return
    val transition = rememberTransition(id, keys)
    val sideNow = layoutSide()
    if (machine != null) SideEffect { machine(transition, sideNow) }
    val side = layoutSide()
    val memory = remember { HostMemory<T>() }
    val holder = rememberSaveableStateHolder()
    val current = stack.associateBy(key)
    HostSync(transition, memory, keys) { logical -> stackGo(logical, keys, side) }
    val back by rememberUpdatedState(onBack)
    val canBack = rememberUpdatedState(backEnabled)
    val scope = LocalNbBackScope.current
    val target = remember(transition, scope) { stackBackTarget(transition, scope, memory, canBack, side) { back() } }
    SideEffect {
        memory.values.putAll(current)
        target.side = side
    }
    val logical = transition.logical
    val top = logical.last()
    val layers = transition.layers()
    RegisterBackTarget(target)
    val settled = rememberUpdatedState(onSettled)
    LaunchedEffect(transition.settled) {
        // Only at rest: a motion set up after the one that ended (a third state) still shows its leaving page, whose
        // entry must stay alive until that motion settles too (V7).
        if (transition.running) return@LaunchedEffect
        val live = transition.state.stack
        memory.values.keys.retainAll(live.toSet())
        if (saveable) memory.forgotten(holder, live)
        settled.value()
    }
    // The two pages below the top are composed, measured, placed and drawn ahead at rest, parked (an invisible layer:
    // no semantics, no input, no focus): a back gesture, a pop or a double Back starts on pages whose layers and
    // display lists already exist, so the start of a motion composes and measures nothing new (V5, R1-3; round 6 H2:
    // the page two below used to be composed in a gesture's first frame). While a motion runs, the pages parked
    // before it stay composed.
    val running = transition.moving
    val ahead = listOfNotNull(logical.getOrNull(logical.size - 2), logical.getOrNull(logical.size - BELOW_NEXT))
    val parkedKeys =
        buildList {
            if (running) memory.parkedSet.filter { it in current || it in memory.values }.forEach { add(it) }
            addAll(ahead)
        }.filter { it !in layers }.distinct()
    SideEffect {
        if (!running) {
            memory.parkedSet.clear()
            memory.parkedSet.addAll(ahead)
        }
    }
    val composed = parkedKeys + layers
    Box(modifier.fillMaxSize().onSizeChanged { memory.width = it.width.toFloat() }) {
        composed.forEach { k ->
            key(k) {
                val value = current[k] ?: memory.values[k]
                val child = rememberChildScope { transition.top == k }
                val isTop = k == top
                val parked = k !in layers
                NbLayer(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .evictFocus(hidden = !isTop)
                            .zIndex(if (k == layers.last()) 1f else 0f)
                            .poseLayer(transition, k, parked = if (parked) id else null)
                            .blockInput { transition.top != k || transition.inputBlocked }
                            .then(if (isTop) Modifier else Hidden),
                ) {
                    if (value != null) {
                        val cap = remember(transition, k) { NbLayerCap(transition, k) }
                        CompositionLocalProvider(LocalNbBackScope provides child, LocalNbLayerCap provides cap) {
                            if (saveable) holder.SaveableStateProvider(k) { content(value) } else content(value)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The Go event of a stack going from the logical stack [logical] to [keys] (relation Depth): a pop when the new top
 * was below and the old top is gone, else a push (a reset included).
 */
internal fun stackGo(
    logical: List<Any>,
    keys: List<Any>,
    side: Int,
): NbNavEvent.Go {
    val pop = keys.last() in logical && logical.last() !in keys
    return NbNavEvent.Go(keys, NbKind.Page, toIsUpper = !pop, e = side, now = NbClock.nowNanos())
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
 * The Back target of a host (ADR 0011): every input is one event into the host's state machine, applied at once. A
 * gesture's start, moves, cancel and commit drive the motion under the finger; Back without a gesture is a Go to the
 * stack [backOf] gives. A Back that changed the logical stack (a commit, a Back) is reported to the caller
 * ([onBack]) at once; the caller's state change then meets a machine that already shows it.
 */
@Suppress("LongParameterList") // The machine, its branch, its geometry and the caller's Back.
internal class HostBackTarget(
    private val transition: NbTransition,
    private val scope: NbBackScope,
    private val width: () -> Float,
    private val canBack: () -> Boolean,
    private val kind: NbKind,
    var side: Int,
    /** The logical stack Back leads to from a logical stack, or null when there is none. */
    private val backOf: (stack: List<Any>) -> List<Any>?,
    private val onBack: () -> Unit,
) : NbBackTarget {
    override val depth: Int get() = scope.depth + 1

    override fun enabled(): Boolean = canBack() && backOf(transition.state.stack) != null && scope.active()

    override fun start(
        edge: Int,
        touchX: Float,
    ) {
        voided = false
        gestureId = 0L
        taken = false
        val back = backOf(transition.state.stack) ?: return
        val dir = edgeSide(edge, side)
        // A page leaves to the side of the swipe; a section to the bar-order side whatever the edge (Home is first).
        val e = if (kind == NbKind.Tab) side else dir
        val id = nextGestureId++
        gestureId = id
        transition.dispatch(
            NbNavEvent.GestureStart(
                back,
                kind,
                toIsUpper = false,
                e = e,
                x = touchX,
                width = width(),
                now = NbClock.nowNanos(),
                dir = dir,
                id = id,
            ),
        )
        taken = transition.motion?.let { it.drive == NbDrive.Finger && it.gestureId == id } == true
    }

    override fun progress(move: NbBackMove) {
        if (taken) transition.dispatch(NbNavEvent.GestureMove(move.touchX, move.progress, move.timeMillis, NbClock.nowNanos()))
    }

    override fun cancel() {
        voided = false
        val id = gestureId
        val wasTaken = taken
        gestureId = 0L
        taken = false
        if (wasTaken) transition.dispatch(NbNavEvent.GestureEnd(commit = false, now = NbClock.nowNanos(), id = id))
    }

    @Suppress("ReturnCount", "ComplexCondition") // Voided, held, resolved, a Back; the held gesture is this one.
    override fun commit() {
        val id = gestureId
        val wasTaken = taken
        gestureId = 0L
        taken = false
        // The rest of a gesture a layout change dropped does nothing (R6).
        if (voided) {
            voided = false
            return
        }
        val m = transition.motion
        if (wasTaken && m != null && m.drive == NbDrive.Finger && m.gestureId == id) {
            val before = transition.top
            transition.dispatch(NbNavEvent.GestureEnd(commit = true, now = NbClock.nowNanos(), id = id))
            if (transition.top != before) followed()
            return
        }
        // L2: the finger's gesture was resolved by something else while it held (a key Back, the caller's change):
        // its commit does nothing more, so one gesture never goes back twice.
        if (wasTaken) return
        // A gesture that never took a motion here (the platform committed without a start, R3-1; a start while the
        // motion was more than one page away): a Back like any other, caught from wherever the host's motion is.
        back()
    }

    override fun back() {
        val back = backOf(transition.state.stack) ?: return
        val before = transition.top
        transition.dispatch(NbNavEvent.Go(back, kind, toIsUpper = false, e = side, now = NbClock.nowNanos()))
        if (transition.top != before) followed()
    }

    /** The machine went back: the caller must follow (M4: an explicit acknowledgement is expected). */
    private fun followed() {
        transition.expectFollow(transition.state.stack)
        onBack()
    }

    private var gestureId = 0L
    private var taken = false

    override fun abort() {
        if (transition.gesture) voided = true
        transition.dispatch(NbNavEvent.Abort)
    }

    /** True after a layout change dropped this target's gesture, until the gesture ends or a new one starts. */
    private var voided = false

    private companion object {
        /** Gesture ids of every host target (unique in the process; 0 is none). */
        var nextGestureId = 1L
    }
}

/** The Back target of a [NbStackHost]: pops the top page. */
@Suppress("LongParameterList") // The target's parts.
private fun stackBackTarget(
    transition: NbTransition,
    scope: NbBackScope,
    memory: HostMemory<*>,
    canBack: State<Boolean>,
    side: Int,
    onBack: () -> Unit,
): HostBackTarget =
    HostBackTarget(
        transition,
        scope,
        width = { memory.width },
        canBack = { canBack.value },
        kind = NbKind.Page,
        side = side,
        backOf = { stack -> if (stack.size > 1) stack.dropLast(1) else null },
        onBack = onBack,
    )

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

/** One layer of a host: its content stacked as in a `Box` (top start, loose constraints). */
@Composable
internal fun NbLayer(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val width = maxOf(constraints.minWidth, placeables.maxOfOrNull { it.width } ?: 0)
        val height = maxOf(constraints.minHeight, placeables.maxOfOrNull { it.height } ?: 0)
        layout(width, height) { placeables.forEach { it.placeRelative(0, 0) } }
    }
}

private class SectionMemory<K : Any> : SyncMemory() {
    var first: K? = null

    /** Every section shown so far (ADR 0006: they stay composed), written after each applied composition. */
    val visited = LinkedHashSet<K>()

    /** The host's width in px (the travel of a back gesture over it). */
    var width: Float = 0f
}

/**
 * Top-level sections in bar order (ADR 0011, kind Tab, relation Section; ADR 0006): every visited section stays
 * composed; a change of [selected] is one event into the host's state machine and runs the Page geometry with the
 * higher bar index as the upper layer, on the Nav spring, whatever triggered it (bar, rail, keyboard, "See all", a
 * gesture back to the first section). Visited sections that are not shown are parked: measured, placed and drawn
 * ahead in an invisible layer, with no semantics, input or focus.
 *
 * The bar and the rail show the machine's logical section ([nbShownSection]): one value, the target of a spring from
 * its first frame, and the section a back gesture started from until its release commits (ADR 0011 rule N6).
 *
 * Back: while a section other than the first is shown and the host's branch is shown, Back (gesture or not) goes to
 * the first section in the machine at once and then calls [onBackToFirst].
 *
 * @param content one section; `active` reads true while it is the shown one (read it outside composition).
 */
@Suppress("CyclomaticComplexMethod", "LongMethod") // One host: sync, back target, parked sections, layers.
@Composable
fun <K : Any> NbSectionHost(
    selected: K,
    order: List<K>,
    onBackToFirst: () -> Unit,
    modifier: Modifier = Modifier,
    id: String = "sections",
    content: @Composable (section: K, active: () -> Boolean) -> Unit,
) {
    val transition = rememberTransition(id, listOf(selected))
    val side = layoutSide()
    val holder = rememberSaveableStateHolder()
    val memory = remember { SectionMemory<K>() }
    HostSync(transition, memory, listOf(selected)) { logical ->
        @Suppress("UNCHECKED_CAST")
        val shown = logical.last() as K
        val upper = order.indexOf(selected) > order.indexOf(shown)
        NbNavEvent.Go(listOf(selected), NbKind.Tab, toIsUpper = upper, e = side, now = NbClock.nowNanos())
    }
    @Suppress("UNCHECKED_CAST")
    val logicalTop = transition.logical.last() as K
    val layers = transition.layers()

    // The sections to compose: every one visited before (applied compositions only) and the ones of this motion.
    @Suppress("UNCHECKED_CAST")
    val visited =
        LinkedHashSet<K>(memory.visited).apply {
            add(logicalTop)
            addAll(layers as List<K>)
        }
    val back by rememberUpdatedState(onBackToFirst)
    val scope = LocalNbBackScope.current
    val first = order.first()
    val target =
        remember(transition, scope) {
            HostBackTarget(
                transition,
                scope,
                width = { memory.width },
                canBack = { true },
                kind = NbKind.Tab,
                side = side,
                backOf = { stack -> memory.first?.takeIf { it != stack.last() }?.let { listOf(it) } },
                onBack = { back() },
            )
        }
    SideEffect {
        memory.first = first
        memory.visited.addAll(visited)
        target.side = side
    }
    RegisterBackTarget(target)
    Layout(
        modifier = modifier.onSizeChanged { memory.width = it.width.toFloat() },
        content = {
            visited.forEach { section ->
                key(section) {
                    val active = remember(section) { { transition.top == section } }
                    val child = rememberChildScope(active)
                    val shown = section in layers
                    val shift = remember(section) { SectionShift(transition, section) }
                    NbLayer(
                        modifier =
                            Modifier
                                .layoutId(section)
                                .evictFocus(hidden = !(shown && section == logicalTop))
                                .poseLayer(transition, section, shift, parked = if (shown) null else id)
                                .blockInput { transition.top != section || transition.inputBlocked }
                                .then(if (shown && section == logicalTop) Modifier else Hidden),
                    ) {
                        CompositionLocalProvider(
                            LocalNbBackScope provides child,
                            LocalSectionShift provides shift,
                            LocalSectionSelected provides logicalTop,
                        ) {
                            holder.SaveableStateProvider(section.toString()) { content(section, active) }
                        }
                    }
                }
            }
        },
    ) { measurables, constraints ->
        // Every visited section is measured, placed and drawn: the ones not in the motion are parked (invisible, no
        // semantics, no input, no focus), so a tab change or the gesture back to the first section starts on a layer
        // that already exists.
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

/**
 * Content that changes in place (ADR 0011): with an [order] the values are a sequence (nights, months) and a change
 * runs the Pager kind towards the side of the newer value; without one they are unordered peers (a two-pane
 * selection, a view toggle, a filter result) and a change runs the Swap kind. Both on the Nav spring, through the same
 * state machine as every page (a change back to the value being left reverses from (p, v)). The leaving value stays
 * composed until p settles; the box clips while a change runs.
 */
@Suppress("CyclomaticComplexMethod") // One host: sync (pager or swap), step velocity, layers.
@Composable
fun <T : Any> NbSwitcher(
    value: T,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    id: String = "switcher",
    order: Comparator<T>? = null,
    content: @Composable (T) -> Unit,
) {
    val k = key(value)
    val transition = rememberTransition(id, listOf(k))
    val side = layoutSide()
    val memory = remember { HostMemory<T>() }
    val router = LocalNbBackRouter.current
    var handoff: Long? = null
    HostSync(transition, memory, listOf(k)) { logical ->
        val old = memory.values[logical.last()]
        if (order != null && old != null) {
            val forward = sign(order.compare(value, old).toFloat()).toInt().takeIf { it != 0 } ?: 1
            // A step made by a swipe continues with the swipe's speed (p per second over this pager's width). Read
            // here, consumed once the composition is applied (V4).
            val step = router?.stepHandoff?.peek()
            handoff = step?.first
            val speed = step?.second ?: 0f
            val v0 = if (memory.width > 0f) speed / memory.width else 0f
            NbNavEvent.Go(listOf(k), NbKind.Pager, toIsUpper = forward > 0, e = side, now = NbClock.nowNanos(), v0 = v0)
        } else {
            NbNavEvent.Go(listOf(k), NbKind.Swap, toIsUpper = true, e = side, now = NbClock.nowNanos())
        }
    }
    SideEffect {
        memory.values[k] = value
        handoff?.let { router?.stepHandoff?.consume(it) }
    }
    val layers = transition.layers()
    LaunchedEffect(transition.settled) { if (!transition.running) memory.values.keys.retainAll(setOf(transition.top, k)) }
    Box(modifier.onSizeChanged { memory.width = it.width.toFloat() }.then(if (layers.size > 1) Modifier.clipToBounds() else Modifier)) {
        layers.forEach { layer ->
            key(layer) {
                val v = if (layer == k) value else memory.values[layer]
                // Each layer is its own back branch: a leaving layer's hosts are no back target (ADR 0011 R4).
                val child = rememberChildScope { transition.top == layer }
                NbLayer(
                    modifier =
                        Modifier
                            .evictFocus(hidden = layer != k)
                            .zIndex(if (layer == k) 1f else 0f)
                            .poseLayer(transition, layer)
                            .blockInput { transition.top != layer }
                            .then(if (layer == k) Modifier else Hidden),
                ) { if (v != null) CompositionLocalProvider(LocalNbBackScope provides child) { content(v) } }
            }
        }
    }
}

/**
 * The Swap of content that cannot be drawn as two layers (the items of a lazy list: History's results under a view
 * toggle or a filter, ADR 0011 MIG-1), on the same state machine as [NbSwitcher]: [shown] is the value being left
 * while p < 1/2 and the new one from then on (where both are invisible, so the items change unseen), and every item
 * of it draws the Swap pose's alpha through [nbSwapLayer]. A change back during the motion reverses from (p, v).
 */
@Stable
class NbSwapState<T : Any> internal constructor(
    /** The value whose content is shown now. */
    val shown: T,
    internal val transition: NbTransition,
    internal val shownKey: Any,
)

/** An [NbSwapState] of [value] (its identity is [key]). */
@Composable
fun <T : Any> rememberNbSwap(
    value: T,
    key: (T) -> Any,
    id: String,
): NbSwapState<T> {
    val k = key(value)
    val transition = rememberTransition(id, listOf(k))
    transition.reportFrames = true
    val memory = remember { HostMemory<T>() }
    HostSync(transition, memory, listOf(k)) { NbNavEvent.Go(listOf(k), NbKind.Swap, toIsUpper = true, e = 1, now = NbClock.nowNanos()) }
    SideEffect { memory.values[k] = value }
    LaunchedEffect(transition.settled) { if (!transition.running) memory.values.keys.retainAll(setOf(transition.top, k)) }
    // The value nearest the position is the one shown (its alpha is the larger one); it changes where both are 0.
    val nearest by remember(transition) { derivedStateOf { transition.motion?.let { m -> m.keyAt(m.q) } } }
    val shownKey = nearest ?: k

    @Suppress("UNCHECKED_CAST")
    val shown = if (shownKey == k) value else (memory.values[shownKey] ?: value)
    return NbSwapState(shown, transition, shownKey)
}

/** Draws the Swap pose of [swap]'s shown content (alpha only; reported to [NbMotionProbe] once per drawn state). */
fun Modifier.nbSwapLayer(swap: NbSwapState<*>): Modifier =
    graphicsLayer {
        val m = swap.transition.motion
        if (m == null) {
            alpha = 1f
        } else {
            with(swap.transition) { applyPose(m.keyAt(m.q)) }
            swap.transition.reportNow(size.width)
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
 * The selection the fixed elements of a section (the bar, the rail) show: the section host's logical section, the
 * same in every section's layer (ADR 0011 rule N6: the target of a spring from its first frame; during a back gesture
 * the section it started from until the release commits). Outside a section host, [own].
 */
@Composable
fun <K : Any> nbShownSection(own: K): K {
    @Suppress("UNCHECKED_CAST")
    return (LocalSectionSelected.current as? K)?.takeIf { it::class == own::class } ?: own
}

/**
 * Keeps an element of a section in place while the sections change (the floating bar and rail, ADR 0011): the bar is
 * one element of the window, so a section change moves the pages under it, not the bar. Outside a section host it
 * changes nothing.
 */
@Composable
fun Modifier.nbFixedInSection(): Modifier {
    val shift = LocalSectionShift.current ?: return this
    // An offset read at placement, not a layer: the glass bar samples its backdrop exactly as before.
    return offset { IntOffset(-shift.tx().roundToInt(), 0) }
}
