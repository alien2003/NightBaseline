package io.github.alien2003.nightbaseline.ui.interaction

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Immutable
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import kotlin.math.abs
import kotlin.math.pow

/** What drives the p of a host's motion (ADR 0011, navigation state machine). */
enum class NbDrive {
    /** Nothing moves (the host is at rest; a motion never has this drive, the host simply has no motion). */
    Idle,

    /** A back gesture's finger writes p. */
    Finger,

    /** The Nav spring runs p to its target. */
    Spring,
}

/** One sample of a back gesture: the upper layer's travel share [u] (linear in the finger) at [timeMillis]. */
@Immutable
data class NbFingerSample(
    val timeMillis: Long,
    val u: Float,
)

/**
 * The one motion of a host (ADR 0011): the change between two shown states [from] (p = 0) and [to] (p = 1) of one
 * [kind], drawn as two layers whose poses are pure functions of p. [drive] says who moves p now; [target] is where a
 * spring heads, or, for a finger, the side a release commits to. Reversing or retargeting a motion only changes its
 * target: the layers, the pose functions, p and v stay, so every catch is C1-continuous by construction.
 */
@Immutable
data class NbMotion(
    val from: Any,
    val to: Any,
    val kind: NbKind,
    /** Stack kinds (Page, Tab): [to] is the upper layer (a push) or the lower one (a pop). */
    val toIsUpper: Boolean,
    /** The physical side the upper layer comes from or leaves to: +1 right, -1 left. */
    val e: Int,
    val p: Float,
    /** Velocity of p, per second. */
    val v: Float,
    val drive: NbDrive,
    val target: Float,
    /** The spring starts from (start, v0) on the R7 play clock. */
    val start: Float = p,
    val v0: Float = 0f,
    /** The spring's play time drawn now, ns. */
    val play: Long = 0L,
    /** The time origin of a run that continues a motion (ns), null for a run from rest (pre-advanced one frame). */
    val origin: Long? = null,
    /** Frame time of the last frame this run drew; MIN_VALUE before its first frame. */
    val lastFrame: Long = Long.MIN_VALUE,
    /** Frames this run has drawn so far. */
    val drawn: Int = 0,
    /** The upper layer's travel share when the finger took p (a gesture that catches a motion starts where it is). */
    val u0: Float = 0f,
    /** The touch x where the finger took p (NaN: the platform gives no position). */
    val x0: Float = Float.NaN,
    /** The host's width, px: the finger's travel over it is the upper layer's travel share. */
    val width: Float = 0f,
    /** The side the finger travels to going back: +1 right (a swipe from the left edge), -1 left. */
    val dir: Int = e,
    val samples: List<NbFingerSample> = emptyList(),
    /** [NbClock.nowNanos] of the last finger sample. */
    val lastSampleAt: Long = Long.MIN_VALUE,
    /** Frame time of the frame that drew the last finger sample (MIN_VALUE until one has). */
    val sampleDrawnAt: Long = Long.MIN_VALUE,
    /** The logical stack a finger's commit leads to. */
    val commitStack: List<Any>? = null,
    /**
     * The side the motion was asked to reach: a navigation's target, a gesture's commit side (kept by its cancel).
     * Only how the motion is reported to tests ([NbMotionProbe]); it changes nothing that is drawn.
     */
    val toward: Float = target,
) {
    /** The key the motion rests on once it reaches [side] (0: [from], 1: [to]). */
    fun keyAt(side: Float): Any = if (side >= 1f) to else from

    /** The side the motion rests on unless something commits: its target for a spring, the start for a finger. */
    val restSide: Float get() = if (drive == NbDrive.Finger) 1f - target else target

    /** The upper layer of a stack pair. */
    val upperKey: Any get() = if (kind.stacked && !toIsUpper) from else to

    /** The lower layer of a stack pair. */
    val lowerKey: Any get() = if (kind.stacked && !toIsUpper) to else from
}

/** Page and Tab draw the stack pair (an upper layer over a lower one). */
internal val NbKind.stacked: Boolean get() = this == NbKind.Page || this == NbKind.Tab

/**
 * The navigation state of one host: its logical [stack] (the stack every input has already been applied to: the last
 * element is what the host shows once at rest, what Back goes back from, and what fixed elements such as the bar
 * show) and its single [motion] (null at rest).
 */
@Immutable
data class NbNavState(
    val stack: List<Any>,
    val motion: NbMotion? = null,
) {
    /** The logical top. */
    val top: Any get() = stack.last()
}

/** The inputs of the navigation reducer. Every input of every host is one of these, applied at once. */
sealed interface NbNavEvent {
    /**
     * A navigation (tap, link, bar, rail, key, Back button, a state change of the host's caller) to the logical [stack],
     * moving as [kind] ([toIsUpper], [e]); [v0] is a hand-over velocity of p per second (a step swipe).
     */
    data class Go(
        val stack: List<Any>,
        val kind: NbKind,
        val toIsUpper: Boolean,
        val e: Int,
        val now: Long,
        val v0: Float = 0f,
    ) : NbNavEvent

    /** A back gesture starts towards the logical stack [back] at touch [x] over a host [width] px wide. */
    data class GestureStart(
        val back: List<Any>,
        val kind: NbKind,
        val toIsUpper: Boolean,
        val e: Int,
        val x: Float,
        val width: Float,
        val now: Long,
        /** The finger's back direction (+1 rightwards); the page side [e] by default. */
        val dir: Int = e,
    ) : NbNavEvent

    /** The finger is at [x] (NaN: none; the platform's [progress] is used then) at event time [timeMillis]. */
    data class GestureMove(
        val x: Float,
        val progress: Float,
        val timeMillis: Long,
        val now: Long,
    ) : NbNavEvent

    /** The finger let go: [commit] goes back, else the gesture is cancelled. */
    data class GestureEnd(
        val commit: Boolean,
        val now: Long,
    ) : NbNavEvent

    /** A display frame at [frameNanos]; [drawnAt] is when the pose set up by the last input was first drawn (or MIN). */
    data class Frame(
        val frameNanos: Long,
        val drawnAt: Long = Long.MIN_VALUE,
    ) : NbNavEvent

    /** A layout change (ADR 0011 R6): the motion ends at once where it rests; a finger's gesture is void. */
    data object Abort : NbNavEvent
}

/** What the reducer needs from the world. */
@Immutable
data class NbNavEnv(
    /** Reduced motion, or a layout re-projection: every change is instant. */
    val instant: Boolean = false,
    /** The display's frame period, ns (R7). */
    val framePeriod: Long = NbClock.DEFAULT_FRAME_NANOS,
    /** The platform's animation duration scale (0 = animations off). */
    val durationScale: Float = 1f,
)

/** The reducer's result: the new state and the motion that ended in this event, if one did. */
@Immutable
data class NbNavResult(
    val state: NbNavState,
    val ended: NbMotion? = null,
)

/**
 * The navigation state machine of ADR 0011: a pure function from (state, event) to the next state. There is no
 * deferred, queued or replayed input and no token: every event is applied at once.
 *
 * - **Go** sets the logical stack. A host at rest starts a spring from rest towards the new top. A running motion is
 *   *caught*: a new top that is one of its two states only changes its target, from its current (p, v) (a reversal, a
 *   retarget, a Back during a finger hold: C1-continuous); a third state ends the running motion where it was heading
 *   (rule R2) and starts the new one from rest.
 * - **GestureStart** takes the host's motion over at its current p when the motion is between the logical top and the
 *   page Back leads to (a cancel still returning, a push still entering), else it starts from rest; the finger's
 *   offset is the upper layer's current edge, so nothing jumps.
 * - **GestureMove** maps the touch x linearly to the upper layer's edge (edge = edge at the start + finger travel,
 *   over the host width): a pure function of x, no hysteresis.
 * - **GestureEnd** commits (logical stack = Back's) or cancels, both on the spring from the finger's (p, v), with the
 *   pointer-stopped rule and the release rule R0.
 * - **Frame** advances a spring on the R7 clock; when it settles at its target the motion ends, and the host
 *   completes the change (the leaving page leaves the composition, its entry its lifecycle).
 */
object NbNavReducer {
    /** Most finger samples kept (the platform tracker uses the last 20). */
    private const val MAX_SAMPLES = 20

    /** A frame time further than this from the clock is not in its base (a test's virtual clock), ns. */
    private const val SAME_BASE_NANOS = 1_000_000_000L

    private const val NANOS_PER_MS = 1_000_000L

    /** The smallest remaining travel share the gesture slope is taken at (the map is vertical at the end). */
    private const val MIN_REST = 1e-3f

    /** Applies [event] to [state]. */
    fun reduce(
        state: NbNavState,
        event: NbNavEvent,
        env: NbNavEnv = NbNavEnv(),
    ): NbNavResult =
        when (event) {
            is NbNavEvent.Go -> go(state, event, env)
            is NbNavEvent.GestureStart -> gestureStart(state, event)
            is NbNavEvent.GestureMove -> gestureMove(state, event)
            is NbNavEvent.GestureEnd -> gestureEnd(state, event, env)
            is NbNavEvent.Frame -> frame(state, event, env)
            NbNavEvent.Abort -> abort(state)
        }

    @Suppress("ReturnCount") // Same top, instant, at rest, caught, third state.
    private fun go(
        state: NbNavState,
        event: NbNavEvent.Go,
        env: NbNavEnv,
    ): NbNavResult {
        if (event.stack.isEmpty()) return NbNavResult(state)
        val newTop = event.stack.last()
        val m = state.motion
        // The top did not change (the stack below it did, or the caller repeats the state the model already shows).
        if (newTop == state.top) return NbNavResult(state.copy(stack = event.stack))
        if (env.instant || env.durationScale <= 0f) {
            return NbNavResult(NbNavState(event.stack, null), ended = m)
        }
        if (m != null && (newTop == m.to || newTop == m.from)) {
            val side = if (newTop == m.to) 1f else 0f
            return NbNavResult(NbNavState(event.stack, toSpring(m, side, event.now, env)))
        }
        // R2: a third state. The running motion ends where it was heading (the logical top), the new one starts there.
        val fresh =
            NbMotion(
                from = state.top,
                to = newTop,
                kind = event.kind,
                toIsUpper = event.toIsUpper,
                e = event.e,
                p = 0f,
                v = 0f,
                drive = NbDrive.Spring,
                target = 1f,
            )
        return NbNavResult(NbNavState(event.stack, springFrom(fresh, 0f, event.v0, null, event.now, env)), ended = m)
    }

    private fun gestureStart(
        state: NbNavState,
        event: NbNavEvent.GestureStart,
    ): NbNavResult {
        if (event.back.isEmpty() || event.back.last() == state.top) return NbNavResult(state)
        val backTop = event.back.last()
        val m = state.motion
        val caught = m != null && setOf(m.from, m.to) == setOf(state.top, backTop)
        var ended: NbMotion? = null
        val base =
            if (m != null && caught) {
                // The finger takes the motion over where it is: the commit side is the side of Back's top.
                m.copy(target = if (m.to == backTop) 1f else 0f)
            } else {
                ended = m
                NbMotion(
                    from = state.top,
                    to = backTop,
                    kind = event.kind,
                    toIsUpper = event.toIsUpper,
                    e = event.e,
                    p = 0f,
                    v = 0f,
                    drive = NbDrive.Spring,
                    target = 1f,
                )
            }
        val finger =
            base.copy(
                drive = NbDrive.Finger,
                v = 0f,
                u0 = upperShare(base, base.p),
                x0 = event.x,
                width = event.width,
                dir = event.dir,
                toward = base.target,
                samples = emptyList(),
                lastSampleAt = Long.MIN_VALUE,
                sampleDrawnAt = Long.MIN_VALUE,
                commitStack = event.back,
                origin = null,
                lastFrame = Long.MIN_VALUE,
            )
        return NbNavResult(state.copy(motion = finger), ended = ended)
    }

    private fun gestureMove(
        state: NbNavState,
        event: NbNavEvent.GestureMove,
    ): NbNavResult {
        val m = state.motion
        if (m == null || m.drive != NbDrive.Finger) return NbNavResult(state)
        val g =
            if (event.x.isNaN() || m.x0.isNaN() || m.width <= 0f) {
                event.progress.coerceIn(0f, 1f)
            } else {
                m.dir * (event.x - m.x0) / m.width
            }
        val u = (m.u0 + g).coerceIn(0f, 1f)
        val p = pOfUpperShare(m, u)
        val last = m.samples.lastOrNull()?.timeMillis
        val t = if (last != null && event.timeMillis <= last) last + 1 else event.timeMillis
        val samples = (m.samples + NbFingerSample(t, u)).takeLast(MAX_SAMPLES)
        val v = trackedVelocity(samples) * slope(m, u)
        return NbNavResult(
            state.copy(motion = m.copy(p = p, v = v, samples = samples, lastSampleAt = event.now, sampleDrawnAt = Long.MIN_VALUE)),
        )
    }

    @Suppress("ReturnCount") // No finger, instant, a spring.
    private fun gestureEnd(
        state: NbNavState,
        event: NbNavEvent.GestureEnd,
        env: NbNavEnv,
    ): NbNavResult {
        val m = state.motion
        if (m == null || m.drive != NbDrive.Finger) return NbNavResult(state)
        val stack = if (event.commit) m.commitStack ?: state.stack else state.stack
        val side = if (event.commit) m.target else 1f - m.target
        if (env.instant || env.durationScale <= 0f) return NbNavResult(NbNavState(stack, null), ended = m)
        return NbNavResult(NbNavState(stack, toSpring(m, side, event.now, env)))
    }

    private fun abort(state: NbNavState): NbNavResult {
        val m = state.motion ?: return NbNavResult(state)
        return NbNavResult(state.copy(motion = null), ended = m)
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod") // The R7 clock: finger, first two frames, continuation, end.
    private fun frame(
        state: NbNavState,
        event: NbNavEvent.Frame,
        env: NbNavEnv,
    ): NbNavResult {
        val m = state.motion ?: return NbNavResult(state)
        val frame = event.frameNanos
        if (m.drive == NbDrive.Finger) {
            // The frame that draws the last sample: a release continues from it (R7).
            if (m.lastSampleAt == Long.MIN_VALUE || m.sampleDrawnAt != Long.MIN_VALUE) return NbNavResult(state)
            return NbNavResult(state.copy(motion = m.copy(sampleDrawnAt = frame)))
        }
        val stall = NbClock.STALL_NANOS
        val alreadyDrawn = event.drawnAt != Long.MIN_VALUE
        val play =
            when {
                // From rest (pre-advanced one display frame): the first two drawn frames are one display frame apart,
                // whether the run was set up by an input (its first frame comes after this event) or in a composition
                // (that composition's frame drew the first pose), so every trigger draws the same curve.
                m.origin == null && m.drawn == 0 && !alreadyDrawn -> m.play

                m.origin == null && m.drawn <= 1 -> m.play + env.framePeriod

                m.lastFrame != Long.MIN_VALUE -> m.play + (frame - m.lastFrame).coerceIn(0L, stall)

                m.origin != null && abs(frame - m.origin) <= SAME_BASE_NANOS -> (frame - m.origin).coerceIn(0L, stall)

                else -> m.play
            }
        val drawnNow = if (m.origin == null && m.drawn == 0 && alreadyDrawn) 2 else m.drawn + 1
        val (p, v, done) = evaluate(m, play, env)
        if (done) return NbNavResult(state.copy(motion = null), ended = m.copy(p = m.target, v = 0f))
        return NbNavResult(state.copy(motion = m.copy(p = p, v = v, play = play, lastFrame = frame, drawn = drawnNow)))
    }

    /** The motion [m] continues as a spring towards [side] from its current (p, v) (R0, R7). */
    private fun toSpring(
        m: NbMotion,
        side: Float,
        now: Long,
        env: NbNavEnv,
    ): NbMotion {
        if (m.drive == NbDrive.Spring && m.target == side) return m
        val v = velocityAt(m, now)
        // A navigation asks for its side; a gesture's release keeps the side the gesture asked for.
        val toward = if (m.drive == NbDrive.Finger) m.toward else side
        val origin =
            when {
                m.drive == NbDrive.Finger && m.sampleDrawnAt != Long.MIN_VALUE && moving(m, now) -> m.sampleDrawnAt
                m.drive == NbDrive.Finger && moving(m, now) -> m.lastSampleAt
                m.drive == NbDrive.Spring && m.lastFrame != Long.MIN_VALUE -> m.lastFrame
                else -> now
            }
        val base = m.copy(drive = NbDrive.Spring, target = side, commitStack = null, samples = emptyList(), toward = toward)
        return springFrom(base, m.p, v, origin, now, env)
    }

    /**
     * A spring of [m] towards its target from [start] with velocity [v] (R0: capped towards a visible rest). A new run
     * from rest (no [origin], no velocity) is pre-advanced one display frame (R7: the first frame that draws it is one
     * frame into the spring, so a tap's or a Back's first frame already moves); a continuation keeps p and is timed
     * from [origin].
     */
    @Suppress("LongParameterList") // A spring: motion, start state, origin, clock, world.
    private fun springFrom(
        m: NbMotion,
        start: Float,
        v: Float,
        origin: Long?,
        now: Long,
        env: NbNavEnv,
    ): NbMotion {
        val v0 = NbRelease.startVelocity(start, v, m.target, m.kind.spring, visibleTarget = visibleRest(m))
        val run = m.copy(p = start, v = v0, start = start, v0 = v0, play = 0L, lastFrame = Long.MIN_VALUE, drawn = 0)
        // A continuation (a catch, a release) or a hand-over velocity: timed from its origin, p unchanged now (C1).
        if (origin != null || v0 != 0f) return run.copy(origin = origin ?: now)
        val first = env.framePeriod
        val (p, vv, _) = evaluate(run, first, env)
        return run.copy(origin = null, play = first, p = p, v = vv)
    }

    /**
     * True when the motion's target state rests on screen with the moving layer visible (a cancel of a back gesture,
     * a pager or a swap settling): its start velocity is capped so the spring never crosses it (lemma 1). A target that
     * takes the upper layer off screen (a pop, a commit) is not capped; the bound ends it at its first crossing.
     */
    private fun visibleRest(m: NbMotion): Boolean {
        if (!m.kind.stacked) return true
        // The upper layer leaves the screen at the side where it is not shown.
        val upperGone = if (m.toIsUpper) 0f else 1f
        return m.target != upperGone
    }

    /** (p, v, ended) of the spring of [m] at [play] ns. */
    @Suppress("ReturnCount") // At its target, settled, past a bound, running.
    private fun evaluate(
        m: NbMotion,
        play: Long,
        env: NbNavEnv,
    ): Triple<Float, Float, Boolean> {
        if (m.start == m.target && m.v0 == 0f) return Triple(m.target, 0f, true)
        val anim =
            TargetBasedAnimation(m.kind.spring.spec(NbPhysics.P_EPS), Float.VectorConverter, m.start, m.target, AnimationVector1D(m.v0))
        val t = (play / env.durationScale.coerceAtLeast(MIN_SCALE)).toLong()
        if (anim.isFinishedFromNanos(t)) return Triple(m.target, 0f, true)
        val x = anim.getValueFromNanos(t)
        if (x > 1f || x < 0f) return Triple(m.target, 0f, true)
        return Triple(x, anim.getVelocityVectorFromNanos(t).value, false)
    }

    private const val MIN_SCALE = 1e-3f

    /** The finger's velocity of p at [now]: 0 once it rested longer than the platform's stopped-pointer time. */
    internal fun velocityAt(
        m: NbMotion,
        now: Long,
    ): Float =
        when {
            m.drive != NbDrive.Finger -> m.v
            moving(m, now) -> m.v
            else -> 0f
        }

    private fun moving(
        m: NbMotion,
        now: Long,
    ): Boolean = m.lastSampleAt != Long.MIN_VALUE && now - m.lastSampleAt <= NbPhysics.POINTER_STOPPED_MS * NANOS_PER_MS

    /** The upper layer's travel share at [p]: `1 - s^gamma`, s its shown share (stack kinds); p itself otherwise. */
    fun upperShare(
        m: NbMotion,
        p: Float,
    ): Float {
        if (!m.kind.stacked) return p
        val s = if (m.toIsUpper) p else 1f - p
        return 1f - s.coerceIn(0f, 1f).pow(NbPhysics.PAGE_GAMMA)
    }

    /** The inverse of [upperShare]. */
    fun pOfUpperShare(
        m: NbMotion,
        u: Float,
    ): Float {
        if (!m.kind.stacked) return u.coerceIn(0f, 1f)
        val s = (1f - u.coerceIn(0f, 1f)).pow(1f / NbPhysics.PAGE_GAMMA)
        return (if (m.toIsUpper) s else 1f - s).coerceIn(0f, 1f)
    }

    /** dp / du at [u]. */
    private fun slope(
        m: NbMotion,
        u: Float,
    ): Float {
        if (!m.kind.stacked) return 1f
        val rest = (1f - u).coerceAtLeast(MIN_REST)
        val ds = -rest.pow(1f / NbPhysics.PAGE_GAMMA - 1f) / NbPhysics.PAGE_GAMMA
        return if (m.toIsUpper) ds else -ds
    }

    /** The finger's velocity (travel share per second) from [samples], the platform's least-squares tracker. */
    private fun trackedVelocity(samples: List<NbFingerSample>): Float {
        if (samples.size < 2) return 0f
        val tracker = VelocityTracker1D(isDataDifferential = false)
        samples.forEach { tracker.addDataPoint(it.timeMillis, it.u) }
        val v = runCatching { tracker.calculateVelocity() }.getOrDefault(0f)
        return if (v.isFinite()) v else 0f
    }
}
