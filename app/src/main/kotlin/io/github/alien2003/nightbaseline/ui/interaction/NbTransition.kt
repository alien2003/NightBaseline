package io.github.alien2003.nightbaseline.ui.interaction

import androidx.activity.BackEventCompat
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

private const val NANOS_PER_MS = 1_000_000L

/**
 * The change one host is running (ADR 0011): from one state ([from]) to another ([to]) of one [kind], drawn as two
 * layers whose poses are pure functions of the host's single scalar p ([driver]). Stack and section hosts, pagers and
 * swaps all run on this class, so every one of them has the same clock, the same release rule, the same reversal and
 * the same end.
 *
 * Rules (ADR 0011): R1 a change back to the state being left reverses at (1 - p, -v), which draws the same frame;
 * R2 any other new change while one runs snaps the running one to its end first; R3 the leaving state is dropped in
 * the first frame after p settles, never later and never on a timeout; R9 a Back (gesture or not) moves first and
 * changes the host's state when its motion has settled ([goBack], [release]).
 */
@Suppress("TooManyFunctions") // The whole change: set up, run, reverse, gesture, release, abort, poses.
@Stable
class NbTransition internal constructor(
    /** Host id, reported to [NbMotionProbe]. */
    val hostId: String,
    private val scope: CoroutineScope,
) {
    // Every field [go] writes, and every field a composition reads, is snapshot state: [go] runs in composition, and a
    // composition that is discarded or run again rolls its writes back with its snapshot, so a change is set up
    // exactly once and nothing leaks out of a thrown-away composition (review V4: [requestAt] was a plain field).
    // Jobs are cancelled and state changes applied only outside composition ([runPending], gestures, [goBack]).

    /** Reduced motion (set by the host; snapshot state). */
    internal var reduced: Boolean by mutableStateOf(false)

    /** The dark theme (the covered page's dim; snapshot state). */
    internal var dark: Boolean by mutableStateOf(false)

    /** True while the app re-projects its state after a layout change ([nbReproject]); set once by the host. */
    internal var reprojecting: () -> Boolean = { false }
    internal val driver = NbDriver(NbPhysics.Nav) { reduced }

    /** The kind of the running change. */
    var kind: NbKind by mutableStateOf(NbKind.Page)
        private set

    /** The state being left, null at rest. */
    var from: Any? by mutableStateOf(null)
        private set

    /** The state being reached, null at rest. */
    var to: Any? by mutableStateOf(null)
        private set

    /** Stack kinds: [to] is the upper layer (a push) or the lower (a pop). */
    internal var toIsUpper: Boolean by mutableStateOf(true)
        private set

    /** The physical side the motion goes to: +1 right, -1 left. */
    internal var e: Int by mutableIntStateOf(1)
        private set

    /** True while a finger drives p. */
    var gesture: Boolean by mutableStateOf(false)
        private set

    private var gestureFrom = 0f
    private var gestureToken = 0
    private var pendingStart: Float? by mutableStateOf(null)
    private var pendingVelocity by mutableFloatStateOf(0f)
    private var job: Job? = null
    private var runJob: Job? = null

    /** [NbClock.nowNanos] when the pending change was asked for, or the frame a continued motion last drew (R7). */
    private var requestAt: Long? by mutableStateOf(null)

    /**
     * The state change of the running change, applied when it settles at its end (rule R9: a Back, by gesture or
     * not, changes the host's state only after its motion; review V2-1). Null when the state already changed.
     */
    private var onEnd: (() -> Unit)? by mutableStateOf(null)

    /** True while the running change has a deferred state change ([onEnd], or applied and not yet composed). */
    internal var deferred: Boolean by mutableStateOf(false)
        private set

    /** True from the moment the deferred state change was applied until the host composes it ([go] completes it). */
    private var ending: Boolean by mutableStateOf(false)

    /** Backs that arrived while a deferred Back ran: each runs once the one before has settled. */
    private var queuedBacks = 0

    /** A deferred Back that [land] ended early: the host's composition of its state change sets up nothing. */
    private var landed: Pair<Any, Any>? by mutableStateOf(null)

    /**
     * True when the layer of a state has content to draw: it emitted and measured some (rule R8). Set once by the host
     * ([NbLayer]); a change waits for its entering state's content before its time starts.
     */
    internal var hasContent: (Any) -> Boolean = { true }

    /** Bumped on every change made outside composition (gesture start, settle): the host recomposes its layers. */
    internal var version by mutableIntStateOf(0)
        private set

    /** Bumped when a change has settled and its leaving layer is gone. */
    internal var settled by mutableIntStateOf(0)
        private set

    /** Bumped when a change is set up in composition; the host's effect keyed on it runs the spring. */
    internal var runId: Int by mutableIntStateOf(0)
        private set

    /** True while a change runs or a gesture holds one. */
    val running: Boolean get() = to != null

    /** The state a deferred Back goes to (shown by fixed elements such as the bar from its start), else null. */
    val committedTo: Any? get() = if (deferred) to else null

    /** True while no layer takes input: a finger drives p, or a Back runs whose state change is still to come. */
    internal val inputBlocked: Boolean get() = gesture || deferred

    /** The p drawn now. Reads snapshot state: call it in layer lambdas. */
    fun drawnP(): Float {
        val p = driver.p
        return pendingStart ?: p
    }

    /** The layers to compose, lower first: the two states of a change, or [rest] alone. */
    internal fun layers(rest: Any): List<Any> {
        val a = from
        val b = to
        if (a == null || b == null) return listOf(rest)
        return listOf(lowerKey() ?: a, upperKey() ?: b)
    }

    private fun upperKey(): Any? =
        when (kind) {
            NbKind.Page, NbKind.Tab -> if (toIsUpper) to else from
            else -> to
        }

    private fun lowerKey(): Any? =
        when (kind) {
            NbKind.Page, NbKind.Tab -> if (toIsUpper) from else to
            else -> from
        }

    /** The pose of layer [key] of a host [width] px wide. Reads snapshot state: call it in layer lambdas. */
    fun poseOf(
        key: Any,
        width: Float,
    ): NbLayerPose {
        val p = drawnP()
        val a = from
        val b = to
        if (a == null || b == null) return NbLayerPose.Rest
        return when (kind) {
            NbKind.Page, NbKind.Tab -> {
                val s = if (toIsUpper) p else 1f - p
                val (upper, lower) = NbPoses.stack(s, e, width, dark)
                when (key) {
                    upperKey() -> upper
                    lowerKey() -> lower
                    else -> NbLayerPose.Rest
                }
            }

            NbKind.Pager -> {
                val pose = NbPoses.pager(p, e, width)
                if (key == b) {
                    pose.to
                } else if (key == a) {
                    pose.from
                } else {
                    NbLayerPose.Rest
                }
            }

            else -> {
                val pose = NbPoses.swap(p)
                if (key == b) {
                    pose.to
                } else if (key == a) {
                    pose.from
                } else {
                    NbLayerPose.Rest
                }
            }
        }
    }

    /** Draws layer [key]'s pose in a graphics layer and reports the frame to [NbMotionProbe]. */
    internal fun GraphicsLayerScope.applyPose(key: Any) {
        val width = size.width
        val pose = poseOf(key, width)
        if (key == to) report(width)
        translationX = pose.tx
        translationY = pose.ty
        scaleX = pose.scale
        scaleY = pose.scale
        alpha = pose.alpha * (1f - pose.dim)
    }

    private fun report(width: Float) {
        val sink = NbMotionProbe.frames
        val a = from
        val b = to
        if (sink != null && a != null && b != null) {
            sink(NbFrame(hostId, kind, kind.spring, drawnP(), b, a, poseOf(b, width), poseOf(a, width), gesture, width))
        }
    }

    /**
     * The host's state changed from [a] to [b] (called in composition). Starts the change, reverses the running one
     * when [b] is the state it leaves (R1), completes a deferred Back whose state change arrived (R9), or continues
     * exactly this change towards its end when it already runs: a committed run goes on as it is, while a cancel or a
     * held gesture of the same change is retargeted to the end from (p, v) (review V2-2: the state change was
     * swallowed and the cancel ran to 0, so the page then vanished in one frame).
     */
    @Suppress("ReturnCount") // A landed Back, the same change, a reversal; else a new change.
    internal fun go(
        a: Any,
        b: Any,
        kind: NbKind,
        toIsUpper: Boolean,
        e: Int,
        v0: Float = 0f,
    ) {
        val early = landed
        if (early != null) {
            landed = null
            if (early.first == a && early.second == b) return
        }
        if (to == b && from == a) {
            when {
                ending -> complete()

                deferred -> onEnd = null

                // The state changed by itself: the deferred Back must not change it again.
                gesture || driver.heading == 0f -> retarget()
            }
            return
        }
        if (ending) complete()
        if (isReverseOf(a, b, kind)) {
            reverse()
            return
        }
        // R2: the running change ends where it is; its spring is cancelled by the effect that runs the new one. A
        // deferred Back that has not changed the state yet is dropped (the navigation that came first wins).
        landed = null
        onEnd = null
        deferred = false
        gesture = false
        this.kind = kind
        from = a
        to = b
        this.toIsUpper = toIsUpper
        this.e = e
        // A layout change re-projects the state with a snap (R6): the change starts at its end.
        pendingStart = if (reprojecting()) 1f else 0f
        pendingVelocity = v0
        requestAt = NbClock.nowNanos()
        runId++
    }

    /** True when a change from [a] to [b] goes back to the state the running (tap-driven) change leaves. */
    private fun isReverseOf(
        a: Any,
        b: Any,
        kind: NbKind,
    ): Boolean = !gesture && this.kind == kind && to == a && from == b

    private fun reverse() {
        val p = drawnP()
        val v = driver.v
        val a = from
        from = to
        to = a
        toIsUpper = !toIsUpper
        if (kind == NbKind.Pager) e = -e
        pendingStart = 1f - p
        pendingVelocity = -v
        // The reversal continues the motion from its last drawn frame (R7), not from this composition.
        requestAt = driver.continueOrigin()
        runId++
    }

    /** The running cancel or held gesture of the change being reached goes on to its end from (p, v) (in composition). */
    private fun retarget() {
        val v = if (gesture) driver.trackedVelocity() else driver.v
        gesture = false
        pendingStart = drawnP()
        pendingVelocity = v
        requestAt = driver.continueOrigin()
        runId++
    }

    /** The deferred Back's state change has been composed: the change is over (in composition). */
    private fun complete() {
        from = null
        to = null
        gesture = false
        ending = false
        deferred = false
        version++
        settled++
    }

    /** A change from rest ([start] 0) whose [entering] state has nothing to draw yet waits for it (R8). */
    private fun waitsForContent(
        start: Float,
        entering: Any,
    ): Boolean = start == 0f && !reduced && !hasContent(entering)

    /** Tests: draws the change set up by [go] at [p] (as if its spring had run so far). */
    @VisibleForTesting
    internal fun holdAt(p: Float) {
        pendingStart = p
    }

    /**
     * Runs the change set up by [go]; the host calls it from an effect keyed on [runId].
     *
     * Rule R8 (a change moves only what is drawn): a change from rest whose entering state has no content yet (its
     * page was just composed and its state is still loading, or it has not been measured) holds p at its start, which
     * draws exactly the frame before the change, until the entering layer has content, at most
     * [NbPhysics.ENTER_WAIT_MS] after the request (review V2-5: the wait was counted from its first frame, so a slow
     * set-up frame came on top of it). Its first frame of motion is then at most one display frame into the spring
     * (R7, [NbPlayClock]), so the cost of composing the entering page never shows as a jump of the motion, whether or
     * not it had to wait (review V2: only a wait was covered).
     */
    internal suspend fun runPending() {
        val start = pendingStart ?: return
        job?.cancel()
        runJob = currentCoroutineContext()[Job]
        var origin = requestAt
        val entering = to
        if (entering != null && waitsForContent(start, entering)) {
            val asked = origin ?: NbClock.nowNanos()
            val budget = (NbPhysics.ENTER_WAIT_MS * NANOS_PER_MS - (NbClock.nowNanos() - asked)).coerceAtLeast(0L)
            var first = Long.MIN_VALUE
            while (true) {
                val frame = withFrameNanos { it }
                if (first == Long.MIN_VALUE) first = frame
                if (hasContent(entering) || frame - first >= budget) break
            }
            origin = NbClock.nowNanos()
        }
        driver.snapTo(start)
        pendingStart = null
        // A pager's or a swap's end state rests on screen: its start velocity is capped (lemma 1, no crossing).
        val visible = kind == NbKind.Pager || kind == NbKind.Swap
        val v0 = NbRelease.startVelocity(start, pendingVelocity, 1f, kind.spring, visibleTarget = visible)
        driver.run(1f, v0, origin)
        finish()
    }

    /**
     * Back without a gesture on this host (a button, Esc, Alt+Left, 3-button navigation, TalkBack), from the shown
     * state [a] to [b], whose state change is [then] (rule R9). The motion runs first, on the Nav spring, exactly as
     * the change [go] would set up for that state change; [then] is applied when it has settled, so the cost of the
     * state change (the pages' lifecycles, the page below becoming the shown one) falls after the last moving frame,
     * never into it (review V2-1: the commit frame stalled 70 to 190 ms and the motion then jumped). A Back that
     * catches a cancel or a held gesture of the same change continues it from (p, v) (V2-2); one that catches the
     * opposite change reverses it (R1). Under reduced motion or during a re-projection the state changes at once.
     * Called outside composition.
     */
    @Suppress("LongParameterList", "CyclomaticComplexMethod") // The change of go plus its state change; three cases.
    internal fun goBack(
        a: Any,
        b: Any,
        kind: NbKind,
        toIsUpper: Boolean,
        e: Int,
        then: () -> Unit,
    ) {
        if (reduced || reprojecting()) {
            then()
            return
        }
        if (deferred || ending) {
            queuedBacks++
            return
        }
        val p = drawnP()
        val moving: Boolean
        val v: Float
        val start: Float
        when {
            to == b && from == a && this.kind == kind -> {
                v = if (gesture) driver.trackedVelocity() else driver.v
                start = p
                moving = true
            }

            to == a && from == b && this.kind == kind -> {
                v = -driver.v
                start = 1f - p
                from = a
                to = b
                this.toIsUpper = !this.toIsUpper
                if (kind == NbKind.Pager) this.e = -this.e
                moving = true
            }

            else -> {
                drop()
                this.kind = kind
                from = a
                to = b
                this.toIsUpper = toIsUpper
                this.e = e
                v = 0f
                start = 0f
                moving = false
            }
        }
        job?.cancel()
        runJob?.cancel()
        gesture = false
        gestureToken++
        onEnd = then
        deferred = true
        pendingStart = start
        version++
        val origin = if (moving) driver.continueOrigin() else NbClock.nowNanos()
        val v0 = NbRelease.startVelocity(start, v, 1f, kind.spring, visibleTarget = false)
        job =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                driver.snapTo(start)
                pendingStart = null
                driver.run(1f, v0, origin)
                end()
            }
    }

    /**
     * A back gesture starts while a deferred Back still moves (R9): that Back lands now, its state change applied and
     * its motion at its end (R2), so the gesture goes back from the state the first one leads to, as two Backs do
     * (the gesture does not catch the page that is already leaving). Returns true when there was one; the caller then
     * takes its own states from after that Back. Called outside composition.
     */
    internal fun land(): Boolean {
        if (!deferred) return false
        val then = onEnd
        val a = from
        val b = to
        job?.cancel()
        runJob?.cancel()
        landed = if (a != null && b != null) a to b else null
        onEnd = null
        deferred = false
        ending = false
        queuedBacks = 0
        from = null
        to = null
        gesture = false
        pendingStart = null
        version++
        settled++
        // A state change already applied (ending) is not applied again.
        then?.invoke()
        return true
    }

    /** True once (and clears it) when a Back arrived during a deferred Back: the host runs it now ([goBack]). */
    internal fun takeQueuedBack(): Boolean {
        if (queuedBacks <= 0 || running) return false
        queuedBacks--
        return true
    }

    /**
     * A back gesture starts on this host: from the shown state [a] towards [b]. A gesture that catches a running
     * change towards [a] from [b] reverses it and starts where it is; one that catches its own cancel, or a Back that
     * is still moving (its state change is then not applied: the finger decides again), resumes.
     */
    internal fun beginGesture(
        a: Any,
        b: Any,
        kind: NbKind,
        toIsUpper: Boolean,
        e: Int,
    ): Int {
        // The state change of a Back was applied and is being composed: this gesture's states are stale.
        if (ending) return -1
        job?.cancel()
        runJob?.cancel()
        onEnd = null
        deferred = false
        queuedBacks = 0
        val p = drawnP()
        val start =
            when {
                to == a && from == b && this.kind == kind -> {
                    from = a
                    to = b
                    this.toIsUpper = !this.toIsUpper
                    1f - p
                }

                to == b && from == a && this.kind == kind -> {
                    p
                }

                else -> {
                    drop()
                    this.kind = kind
                    from = a
                    to = b
                    this.toIsUpper = toIsUpper
                    this.e = e
                    0f
                }
            }
        gesture = true
        gestureFrom = start
        gestureToken++
        driver.resetTracking()
        pendingStart = start
        version++
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            driver.snapTo(start)
            pendingStart = null
        }
        return gestureToken
    }

    /** The finger is at [fraction] of its travel over the host. */
    internal fun drag(
        token: Int,
        fraction: Float,
        timeMillis: Long,
    ) {
        if (!gesture || token != gestureToken) return
        val p =
            when (kind) {
                NbKind.Page, NbKind.Tab -> NbGestureMap.stackPop(fraction, gestureFrom)
                else -> NbGestureMap.linear(fraction, gestureFrom)
            }
        val stack = kind == NbKind.Page || kind == NbKind.Tab
        val slope = if (stack) NbGestureMap.stackPopSlope(fraction, gestureFrom) else 1f
        // The velocity is measured on the finger (linear) and mapped by the slope: the page leaves at the finger's speed.
        scope.launch(start = CoroutineStart.UNDISPATCHED) { driver.track(p, timeMillis, finger = fraction, slope = slope) }
    }

    /**
     * The finger let go: a commit continues to the end state on the same spring from (p, v) with no cap (the end
     * leaves the screen) and applies the state change [then] once it has settled (R9); a cancel returns to the start
     * state with the velocity capped so it never crosses it (R0). Returns false when the gesture is no longer the
     * current one (dropped by a layout change).
     */
    internal fun release(
        token: Int,
        commit: Boolean,
        then: (() -> Unit)? = null,
    ): Boolean {
        if (!gesture || token != gestureToken) return false
        gesture = false
        val at = NbClock.nowNanos()
        if (commit && then != null) {
            onEnd = then
            deferred = true
        }
        // Undispatched: the spring is set up at the release instant (R7), before the frame the release falls in.
        job =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                driver.release(if (commit) 1f else 0f, visibleTarget = !commit, at = at)
                if (commit) end() else finish()
            }
        return true
    }

    /**
     * A change that reached its end: its deferred state change, if any, is applied now (outside composition), and the
     * host's next composition completes the change ([go]); without one it is over now. If the state change does not
     * reach the host within two frames (the callback changed nothing), the change ends as it is.
     */
    private suspend fun end() {
        val then = onEnd
        if (then == null) {
            finish()
            return
        }
        onEnd = null
        ending = true
        then()
        repeat(2) {
            withFrameNanos { }
            if (!ending) return
        }
        if (ending) finish()
    }

    /**
     * Drops everything at once (a layout change): the shown state is at rest, the running gesture is void. A Back whose
     * motion was running changes the state now (the person asked for it; the re-projection follows).
     */
    internal fun abort() {
        val then = onEnd
        if (!running) return
        gestureToken++
        drop()
        version++
        settled++
        then?.invoke()
    }

    private fun drop() {
        job?.cancel()
        job = null
        runJob?.cancel()
        runJob = null
        gesture = false
        from = null
        to = null
        pendingStart = null
        onEnd = null
        deferred = false
        ending = false
    }

    private fun finish() {
        from = null
        to = null
        gesture = false
        onEnd = null
        deferred = false
        ending = false
        version++
        settled++
    }
}

/** The side a back swipe from [edge] moves the page to: right from the left edge, left from the right edge. */
internal fun edgeSide(
    edge: Int,
    layoutSide: Int,
): Int =
    when (edge) {
        BackEventCompat.EDGE_LEFT -> 1
        BackEventCompat.EDGE_RIGHT -> -1
        else -> layoutSide
    }

/** Swallows every touch on the layer while [blocked] says so (a leaving layer, a layer under a finger). */
internal fun Modifier.blockInput(blocked: () -> Boolean): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (blocked()) event.changes.forEach { it.consume() }
            }
        }
    }

/**
 * The pose layer of [key] in [transition]. A [parked] layer (the id of its host; null when it is shown) is placed and
 * drawn ahead but invisible: alpha 0, so the renderer skips it while its display lists stay recorded. The parked
 * probe runs in the layer's draw (review V9: it ran in the layer block, which proves placement only).
 */
internal fun Modifier.poseLayer(
    transition: NbTransition,
    key: Any,
    shift: SectionShift? = null,
    parked: String? = null,
): Modifier =
    graphicsLayer {
        shift?.width = size.width
        if (parked != null) {
            translationX = 0f
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
            alpha = 0f
        } else {
            with(transition) { applyPose(key) }
        }
    }.then(
        if (parked != null) {
            Modifier.drawWithContent {
                drawContent()
                NbMotionProbe.parked?.invoke(parked, key)
            }
        } else {
            Modifier
        },
    )
