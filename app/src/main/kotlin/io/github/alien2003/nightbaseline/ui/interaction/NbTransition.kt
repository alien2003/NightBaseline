package io.github.alien2003.nightbaseline.ui.interaction

import androidx.activity.BackEventCompat
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

/**
 * The runtime of one host's navigation state machine (ADR 0011): it holds the host's [NbNavState] as snapshot state,
 * applies every input through [NbNavReducer] at once ([dispatch]), pumps display frames into it while a spring runs,
 * and draws the two layers of the motion as pure functions of its p. Stack and section hosts, pagers and swaps all run
 * on this class, so they share one reducer, one clock, one release rule, one catch rule and one end.
 */
@Suppress("TooManyFunctions") // Dispatch, pump, layers, poses, probe.
@Stable
class NbTransition internal constructor(
    /** Host id, reported to [NbMotionProbe]. */
    val hostId: String,
    private val scope: CoroutineScope,
    initial: List<Any>,
) {
    /** Reduced motion (set by the host; snapshot state). */
    internal var reduced: Boolean by mutableStateOf(false)

    /** The dark theme (the covered page's dim; snapshot state). */
    internal var dark: Boolean by mutableStateOf(false)

    /** True while the app re-projects its state after a layout change ([nbReproject]); set once by the host. */
    internal var reprojecting: () -> Boolean = { false }

    /** The host's state: the logical stack and the one motion. Snapshot state (a discarded composition rolls it back). */
    var state: NbNavState by mutableStateOf(NbNavState(initial))
        private set

    /** Bumped whenever the motion ends and the host is at rest (the host then drops the leaving layer). */
    internal var settled by mutableIntStateOf(0)
        private set

    /** When the pose set up by the last input was first drawn ([NbClock.nowNanos]), MIN_VALUE until then. */
    private var drawnAt = Long.MIN_VALUE
    private var pumping = false

    private val logicalState = derivedStateOf { state.stack }
    private val movingState = derivedStateOf { state.motion != null }

    /** The logical stack, for compositions (changes only when the stack does, not with every frame of p). */
    internal val logical: List<Any> get() = logicalState.value

    /** [running], for compositions. */
    internal val moving: Boolean get() = movingState.value

    /** The structure the composition reads: the layers (lower first) and the logical top. */
    private val structure by derivedStateOf {
        val m = state.motion
        if (m == null) listOf(state.top) else listOf(m.lowerKey, m.upperKey)
    }

    /** The motion, null at rest. */
    val motion: NbMotion? get() = state.motion

    /** The kind of the running motion (Page at rest). */
    val kind: NbKind get() = state.motion?.kind ?: NbKind.Page

    /** The state being left, null at rest. */
    val from: Any? get() = state.motion?.from

    /** The state being reached, null at rest. */
    val to: Any? get() = state.motion?.to

    /** True while a finger drives p. */
    val gesture: Boolean get() = state.motion?.drive == NbDrive.Finger

    /** True while a motion runs or a finger holds one. */
    val running: Boolean get() = state.motion != null

    /** The logical top: what the host shows at rest, what Back leaves, what fixed elements (the bar) show. */
    val top: Any get() = state.top

    /** True while no layer takes input: a finger drives p. */
    internal val inputBlocked: Boolean get() = gesture

    /** The p drawn now. Reads snapshot state: call it in layer lambdas. */
    fun drawnP(): Float = state.motion?.p ?: 0f

    /** The layers to compose, lower first (reads only what changes the structure). */
    internal fun layers(): List<Any> = structure

    private fun env(scale: Float = 1f): NbNavEnv =
        NbNavEnv(instant = reduced || reprojecting(), framePeriod = NbClock.framePeriodNanos, durationScale = scale)

    /**
     * Applies [event] at once (ADR 0011: no deferred, queued or replayed input). In composition ([inComposition]) the
     * write is snapshot state of that composition and the frame pump starts once it is applied ([NbPump]); outside
     * (input callbacks) the pump starts now, so the next display frame already moves.
     */
    internal fun dispatch(
        event: NbNavEvent,
        inComposition: Boolean = false,
    ): NbNavResult {
        val before = state
        val result = NbNavReducer.reduce(before, event, env())
        if (result.state != before) state = result.state
        if (event !is NbNavEvent.Frame) drawnAt = Long.MIN_VALUE
        if (result.ended != null && result.state.motion == null) settled++
        if (!inComposition) ensurePump()
        return result
    }

    /** Tests: draws the running motion at [p] (as if its spring had run so far). */
    @VisibleForTesting
    internal fun holdAt(p: Float) {
        val m = state.motion ?: return
        state = state.copy(motion = m.copy(p = p))
    }

    /** Starts the frame pump while a motion exists (call after a composition that dispatched is applied). */
    @Suppress("ReturnCount") // At rest, a held finger, already pumping.
    internal fun ensurePump() {
        val m = state.motion ?: return
        if (m.drive == NbDrive.Finger) {
            // A held finger needs no frames (the app stays idle while it rests): only the frame that draws its last
            // sample is recorded, the origin of its release (R7).
            if (m.sampleDrawnAt == Long.MIN_VALUE && m.lastSampleAt != Long.MIN_VALUE) {
                scope.launch(start = CoroutineStart.UNDISPATCHED) { withFrameNanos { frame(it, 1f) } }
            }
            return
        }
        if (pumping) return
        pumping = true
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val scale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
                while (state.motion?.drive == NbDrive.Spring) withFrameNanos { frame(it, scale) }
            } finally {
                pumping = false
            }
        }
    }

    private fun frame(
        nanos: Long,
        scale: Float,
    ) {
        val result = NbNavReducer.reduce(state, NbNavEvent.Frame(nanos, drawnAt), env(scale))
        if (result.state != state) state = result.state
        if (result.ended != null && result.state.motion == null) settled++
    }

    /** The pose of layer [key] of a host [width] px wide. Reads snapshot state: call it in layer lambdas. */
    @Suppress("CyclomaticComplexMethod") // Three kinds of pose, two layers each.
    fun poseOf(
        key: Any,
        width: Float,
    ): NbLayerPose {
        val m = state.motion ?: return NbLayerPose.Rest
        val p = m.p
        return when (m.kind) {
            NbKind.Page, NbKind.Tab -> {
                val s = if (m.toIsUpper) p else 1f - p
                val (upper, lower) = NbPoses.stack(s, m.e, width, dark)
                when (key) {
                    m.upperKey -> upper
                    m.lowerKey -> lower
                    else -> NbLayerPose.Rest
                }
            }

            NbKind.Pager -> {
                val pose = NbPoses.pager(p, m.e, width)
                when (key) {
                    m.to -> pose.to
                    m.from -> pose.from
                    else -> NbLayerPose.Rest
                }
            }

            else -> {
                val pose = NbPoses.swap(p)
                when (key) {
                    m.to -> pose.to
                    m.from -> pose.from
                    else -> NbLayerPose.Rest
                }
            }
        }
    }

    /** Draws layer [key]'s pose in a graphics layer and reports the frame to [NbMotionProbe]. */
    internal fun GraphicsLayerScope.applyPose(key: Any) {
        val width = size.width
        val pose = poseOf(key, width)
        val m = state.motion
        // The pose set up by the last input is on screen once the layer it brings in has drawn it.
        if (m != null && key == m.to && drawnAt == Long.MIN_VALUE) drawnAt = NbClock.nowNanos()
        if (m != null && key == m.to) report(m, width)
        translationX = pose.tx
        translationY = pose.ty
        scaleX = pose.scale
        scaleY = pose.scale
        alpha = pose.alpha * (1f - pose.dim)
    }

    /** The motion state last reported: one report per drawn state, however many layers or items draw it. */
    private var reported: NbMotion? = null

    private fun report(
        m: NbMotion,
        width: Float,
    ) {
        val sink = NbMotionProbe.frames ?: return
        if (reported === m) return
        reported = m
        // Reported towards the state the motion was asked to reach (a gesture and its cancel: the commit side).
        val reached = m.keyAt(m.toward)
        val left = m.keyAt(1f - m.toward)
        val p = if (m.toward >= 1f) m.p else 1f - m.p
        sink(NbFrame(hostId, m.kind, m.kind.spring, p, reached, left, poseOf(reached, width), poseOf(left, width), gesture, width))
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
