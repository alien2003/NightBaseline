package io.github.alien2003.nightbaseline.ui.interaction

import androidx.activity.BackEventCompat
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

/**
 * The change one host is running (ADR 0011): from one state ([from]) to another ([to]) of one [kind], drawn as two
 * layers whose poses are pure functions of the host's single scalar p ([driver]). Stack and section hosts, pagers and
 * swaps all run on this class, so every one of them has the same clock, the same release rule, the same reversal and
 * the same end.
 *
 * Rules (ADR 0011): R1 a change back to the state being left reverses at (1 - p, -v), which draws the same frame;
 * R2 any other new change while one runs snaps the running one to its end first; R3 the leaving state is dropped in
 * the first frame after p settles, never later and never on a timeout.
 */
@Suppress("TooManyFunctions") // The whole change: set up, run, reverse, gesture, release, abort, poses.
@Stable
class NbTransition internal constructor(
    /** Host id, reported to [NbMotionProbe]. */
    val hostId: String,
    private val scope: CoroutineScope,
) {
    internal var reduced: Boolean = false
    internal var dark: Boolean = false
    internal val driver = NbDriver(NbPhysics.Nav) { reduced }

    /** The kind of the running change. */
    var kind: NbKind = NbKind.Page
        private set

    /** The state being left, null at rest. */
    var from: Any? = null
        private set

    /** The state being reached, null at rest. */
    var to: Any? = null
        private set

    /** Stack kinds: [to] is the upper layer (a push) or the lower (a pop). */
    internal var toIsUpper: Boolean = true
        private set

    /** The physical side the motion goes to: +1 right, -1 left. */
    internal var e: Int = 1
        private set

    /** True while a finger drives p. */
    var gesture: Boolean = false
        private set

    private var gestureFrom = 0f
    private var gestureToken = 0
    private var pendingStart: Float? = null
    private var pendingVelocity = 0f
    private var job: Job? = null
    private var runJob: Job? = null

    /** Bumped on every change made outside composition (gesture start, settle): the host recomposes its layers. */
    internal var version by mutableIntStateOf(0)
        private set

    /** Bumped when a change has settled and its leaving layer is gone. */
    internal var settled by mutableIntStateOf(0)
        private set

    /** Bumped when a change is set up in composition; the host's effect keyed on it runs the spring. */
    internal var runId: Int = 0
        private set

    /** True while a change runs or a gesture holds one. */
    val running: Boolean get() = to != null

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
     * when [b] is the state it leaves (R1), or does nothing when exactly this change already runs (a committed gesture).
     */
    internal fun go(
        a: Any,
        b: Any,
        kind: NbKind,
        toIsUpper: Boolean,
        e: Int,
    ) {
        if (to == b && from == a) return
        if (isReverseOf(a, b, kind)) {
            reverse()
            return
        }
        drop()
        this.kind = kind
        from = a
        to = b
        this.toIsUpper = toIsUpper
        this.e = e
        // A layout change re-projects the state with a snap (R6): the change starts at its end.
        pendingStart = if (NbReproject.active) 1f else 0f
        pendingVelocity = 0f
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
        runId++
    }

    /** Tests: draws the change set up by [go] at [p] (as if its spring had run so far). */
    @VisibleForTesting
    internal fun holdAt(p: Float) {
        pendingStart = p
    }

    /** Runs the change set up by [go]; the host calls it from an effect keyed on [runId]. */
    internal suspend fun runPending() {
        val start = pendingStart ?: return
        job?.cancel()
        runJob = currentCoroutineContext()[Job]
        driver.snapTo(start)
        pendingStart = null
        val v0 = NbRelease.startVelocity(start, pendingVelocity, 1f, kind.spring, visibleTarget = false)
        driver.run(1f, v0)
        finish()
    }

    /**
     * A back gesture starts on this host: from the shown state [a] towards [b]. A gesture that catches a running
     * change towards [a] from [b] reverses it and starts where it is; one that catches its own cancel resumes.
     */
    internal fun beginGesture(
        a: Any,
        b: Any,
        kind: NbKind,
        toIsUpper: Boolean,
        e: Int,
    ): Int {
        job?.cancel()
        runJob?.cancel()
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

    /** The finger is at [fraction] of the platform's back progress. */
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
        scope.launch(start = CoroutineStart.UNDISPATCHED) { driver.track(p, timeMillis) }
    }

    /**
     * The finger let go: [commit] continues to the end state on the same spring from (p, v) with no cap (the end
     * leaves the screen), a cancel returns to the start state with the velocity capped so it never crosses it (R0).
     * Returns false when the gesture is no longer the current one (dropped by a layout change).
     */
    internal fun release(
        token: Int,
        commit: Boolean,
    ): Boolean {
        if (!gesture || token != gestureToken) return false
        gesture = false
        job =
            scope.launch {
                driver.release(if (commit) 1f else 0f, visibleTarget = !commit)
                finish()
            }
        return true
    }

    /** Drops everything at once (a layout change): the shown state is at rest, the running gesture is void. */
    internal fun abort() {
        if (!running) return
        gestureToken++
        drop()
        version++
        settled++
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
    }

    private fun finish() {
        from = null
        to = null
        gesture = false
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

/** The pose layer of [key] in [transition]. */
internal fun Modifier.poseLayer(
    transition: NbTransition,
    key: Any,
    shift: SectionShift? = null,
): Modifier =
    graphicsLayer {
        shift?.width = size.width
        with(transition) { applyPose(key) }
    }
