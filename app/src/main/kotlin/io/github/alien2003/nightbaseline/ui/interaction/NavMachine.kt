// The reducer and its pure helpers (path, poses, springs, finger): one state machine, its cases spelled out.
@file:Suppress("TooManyFunctions", "ReturnCount", "CyclomaticComplexMethod", "ComplexCondition")

package io.github.alien2003.nightbaseline.ui.interaction

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.Immutable
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sqrt

/** What drives the position of a host's motion (ADR 0011, navigation state machine). */
enum class NbDrive {
    /** Nothing moves (the host is at rest; a motion never has this drive, the host simply has no motion). */
    Idle,

    /** A back gesture's finger writes q. */
    Finger,

    /** The Nav spring runs q to its target. */
    Spring,
}

/** One sample of a back gesture: the upper layer's travel share [u] (linear in the finger) at [timeMillis]. */
@Immutable
data class NbFingerSample(
    val timeMillis: Long,
    val u: Float,
)

/**
 * The one motion of a host (ADR 0011 round 6, the path-position model): an ordered [path] of the host's states and
 * one scalar position [q] over it. Layer i of the path draws the pose `f(kind, i - q)`, the two-layer pose of round 5
 * for `|i - q| <= 1` and nothing beyond, so at most two path layers are visible, and every navigation along the path
 * (a double Back, a double push, paging faster than the spring, a reversal) only moves the target of q from its
 * current (q, v): C1 by construction, whatever the number of steps.
 *
 * Path order: stack kinds (Page, Tab) from the bottom up (index + 1 is the layer above, coming from side [e]); Pager
 * in sequence order (index + 1 lies at side [e]); Swap two peers.
 *
 * A change that is not on the path (a reset, a replaced top, a section that is neither of the two) keeps the motion it
 * interrupts as a visual-only [residual] that runs on to its own target, drawn as one group in the place of path
 * index [slot] (the residual's target state): its layers' poses are the residual's composed with the group's.
 */
@Immutable
data class NbMotion(
    val path: List<Any>,
    val kind: NbKind,
    /** The side index + 1 lies at: +1 right, -1 left. */
    val e: Int,
    /** The position over [path] (index units). */
    val q: Float,
    /** Velocity of q, per second. */
    val v: Float,
    val drive: NbDrive,
    /** Where a spring heads (an index), or, for a finger, the index a release commits to. */
    val target: Float,
    /** The spring starts from (start, v0) on the R7 play clock. */
    val start: Float = q,
    val v0: Float = 0f,
    /** The spring's play time drawn now, ns. */
    val play: Long = 0L,
    /** The time origin of a run that continues a motion (ns), null for a run from rest (pre-advanced one frame). */
    val origin: Long? = null,
    /** Frame time of the last frame this run drew; MIN_VALUE before its first frame. */
    val lastFrame: Long = Long.MIN_VALUE,
    /** Frames this run has drawn so far. */
    val drawn: Int = 0,
    /** The index the motion started from (tests: [drawnP]). */
    val home: Int = q.roundToInt(),
    /** The index of the page a finger holds (its upper layer), -1 without a finger. */
    val grip: Int = -1,
    /** The upper layer's travel share when the finger took q (a gesture that catches a motion starts where it is). */
    val u0: Float = 0f,
    /** The touch x where the finger took q (NaN: the platform gives no position). */
    val x0: Float = Float.NaN,
    /** The host's width, px: the finger's travel over it is the upper layer's travel share. */
    val width: Float = 0f,
    val samples: List<NbFingerSample> = emptyList(),
    /** [NbClock.nowNanos] of the last finger sample. */
    val lastSampleAt: Long = Long.MIN_VALUE,
    /** Frame time of the frame that drew the last finger sample (MIN_VALUE until one has). */
    val sampleDrawnAt: Long = Long.MIN_VALUE,
    /** The logical stack a finger's commit leads to. */
    val commitStack: List<Any>? = null,
    /** Id of the gesture a finger drives (L2: a commit of a gesture that was resolved otherwise is a no-op). */
    val gestureId: Long = 0L,
    /**
     * The index the motion was asked to reach: a navigation's target, a gesture's commit side (kept by its cancel).
     * Only how the motion is reported to tests ([NbMotionProbe]); it changes nothing that is drawn.
     */
    val toward: Float = target,
    /** The motion a non-path change interrupted, running on visually (null: none). */
    val residual: NbMotion? = null,
    /** The path index whose layer is the [residual] group. */
    val slot: Int = -1,
) {
    /** The state at index [i] of the path (clamped). */
    fun keyAt(i: Float): Any = path[i.roundToInt().coerceIn(0, path.lastIndex)]

    /** The index the motion rests on unless something commits: its target for a spring, the held page for a finger. */
    val restSide: Float get() = if (drive == NbDrive.Finger) grip.toFloat() else target

    /** The state the motion heads to. */
    val targetKey: Any get() = keyAt(target)

    /** The share of the current step towards [toward] (0 one index away, 1 there): what [NbMotionProbe] reports. */
    val reportedP: Float get() = (1f - abs(toward - q)).coerceIn(0f, 1f)

    /** The distance from where the motion started ([home]). */
    val drawnP: Float get() = abs(q - home)

    /** The path indices whose layer can be visible now: `|i - q| < 1` (q itself when it is an index). */
    val visible: IntRange get() = floor(q).toInt().coerceIn(0, path.lastIndex)..ceil(q).toInt().coerceIn(0, path.lastIndex)
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
     * moving as [kind]: [toIsUpper] is true when the new top lies forward of the old one (deeper in a stack, a higher
     * bar index, later in a sequence), [e] the side forward lies at; [v0] is a hand-over speed of the change, per second
     * (a step swipe).
     */
    data class Go(
        val stack: List<Any>,
        val kind: NbKind,
        val toIsUpper: Boolean,
        val e: Int,
        val now: Long,
        val v0: Float = 0f,
    ) : NbNavEvent

    /** A back gesture [id] starts towards the logical stack [back] at touch [x] over a host [width] px wide. */
    data class GestureStart(
        val back: List<Any>,
        val kind: NbKind,
        val toIsUpper: Boolean,
        val e: Int,
        val x: Float,
        val width: Float,
        val now: Long,
        /** The finger's back direction (+1 rightwards); kept for the hosts, the map is screen space (M3). */
        val dir: Int = e,
        val id: Long = 0L,
    ) : NbNavEvent

    /** The finger is at [x] (NaN: none; the platform's [progress] is used then) at event time [timeMillis]. */
    data class GestureMove(
        val x: Float,
        val progress: Float,
        val timeMillis: Long,
        val now: Long,
    ) : NbNavEvent

    /** The finger of gesture [id] let go (0: whichever holds): [commit] goes back, else the gesture is cancelled. */
    data class GestureEnd(
        val commit: Boolean,
        val now: Long,
        val id: Long = 0L,
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
 * - **Go** sets the logical stack. A host at rest starts a spring from rest along the two-state path (old top, new
 *   top). A running motion is *caught*: a new top on its path, or one that extends the path (a deeper page of the same
 *   stack, a page further down it, the next value of a sequence beyond the one being reached), only moves the target
 *   of q from the current (q, v) (round 6, H1); a Swap to a third value replaces the invisible one of its two values;
 *   any other change keeps the interrupted motion as a residual exit group and starts a new motion from it.
 * - **GestureStart** takes the host's motion over at its current q when the held page and the page Back leads to are
 *   the pair q lies between, else from rest; the finger's offset is the upper layer's current edge, so nothing jumps.
 * - **GestureMove** maps the touch x linearly, in screen space, to the upper layer's edge (M3).
 * - **GestureEnd** commits (logical stack = Back's) or cancels, both on the spring from the finger's (q, v), with the
 *   pointer-stopped rule and the release rule R0.
 * - **Frame** advances a spring on the R7 clock (a run's first frame at most one display frame, every later one at
 *   most [NbClock.STEP_CAP_FRAMES] display frames and never more than the stall bound); when it settles the motion
 *   ends, and the host completes the change.
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

    @Suppress("ReturnCount") // Same top, instant, at rest, on the path, a swap, a residual.
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
        if (m == null) return NbNavResult(NbNavState(event.stack, fresh(state.top, newTop, event, env)))
        // On the path, or on an extension of it: a retarget of q from (q, v).
        val extended = extend(m, state, event)
        if (extended != null) {
            val t = extended.path.indexOf(newTop).toFloat()
            return NbNavResult(NbNavState(event.stack, trim(retarget(extended, t, event.now, env))))
        }
        // A third peer of a swap: the value that is invisible now is replaced (round 6 substitution rule).
        if (m.kind == NbKind.Swap && event.kind == NbKind.Swap && m.path.size == 2) {
            val hidden = if (m.q < HALF) 1 else 0
            val swapped = m.copy(path = m.path.toMutableList().also { it[hidden] = newTop })
            return NbNavResult(NbNavState(event.stack, retarget(swapped, hidden.toFloat(), event.now, env)))
        }
        // A change off the path: the interrupted motion runs on as a residual exit group in the place of its target.
        val residual = toSpring(m.copy(residual = null, slot = -1), m.path.indexOf(state.top).toFloat(), event.now, env, release = false)
        val next = fresh(state.top, newTop, event, env)
        val slot = next.path.indexOf(state.top)
        return NbNavResult(NbNavState(event.stack, next.copy(residual = residual, slot = slot)), ended = m.residual)
    }

    /** A new two-state motion from rest from [top] to [newTop]. */
    private fun fresh(
        top: Any,
        newTop: Any,
        event: NbNavEvent.Go,
        env: NbNavEnv,
    ): NbMotion {
        val forward = event.toIsUpper
        val path = if (forward) listOf(top, newTop) else listOf(newTop, top)
        val from = if (forward) 0f else 1f
        val to = 1f - from
        val base = NbMotion(path = path, kind = event.kind, e = event.e, q = from, v = 0f, drive = NbDrive.Spring, target = to)
        val handover = abs(event.v0) * sign(to - from)
        return springFrom(base, from, handover, null, event.now, env)
    }

    /**
     * The motion [m] with its path extended so that the new top of [event] is on it, or null when the change is off
     * the path. Page: the new stack and the logical one are prefixes of each other and the path is a contiguous run
     * of the longer one. Pager: the next value beyond the path's end the motion heads to. Tab, Swap: only the path.
     */
    @Suppress("ReturnCount") // Kinds and the stack union.
    private fun extend(
        m: NbMotion,
        state: NbNavState,
        event: NbNavEvent.Go,
    ): NbMotion? {
        val newTop = event.stack.last()
        if (event.kind != m.kind) return null
        if (m.kind == NbKind.Page) {
            val fallback = if (newTop in m.path) m else null
            // The chain the host knows: the logical stack below the path's first page, then the path or the rest of
            // the logical stack, whichever is longer (they must agree where both exist).
            val b = state.stack
            val j = b.indexOf(m.path.first())
            if (j < 0) return fallback
            val tail = b.subList(j, b.size)
            val overlap = minOf(tail.size, m.path.size)
            if (tail.subList(0, overlap) != m.path.subList(0, overlap)) return fallback
            val known = b.subList(0, j) + (if (m.path.size >= tail.size) m.path else tail)
            val a = event.stack
            val union =
                when {
                    a.size >= known.size && a.subList(0, known.size) == known -> a
                    known.size > a.size && known.subList(0, a.size) == a -> known
                    else -> return fallback
                }
            val at = indexOfRun(union, m.path) ?: return fallback
            val n = union.indexOf(newTop)
            val lo = minOf(at, n)
            val hi = maxOf(at + m.path.size - 1, n)
            return shift(m, union.subList(lo, hi + 1), at - lo)
        }
        if (newTop in m.path) return m
        if (m.kind == NbKind.Pager && m.drive == NbDrive.Spring && m.targetKey == state.top) {
            val t = m.target.roundToInt()
            if (event.toIsUpper && t == m.path.lastIndex) return m.copy(path = m.path + newTop)
            if (!event.toIsUpper && t == 0) return shift(m, listOf(newTop) + m.path, 1)
        }
        return null
    }

    /** The index where [run] occurs contiguously in [list], or null. */
    private fun indexOfRun(
        list: List<Any>,
        run: List<Any>,
    ): Int? {
        if (run.isEmpty() || run.size > list.size) return null
        val at = list.indexOf(run.first())
        if (at < 0 || at + run.size > list.size) return null
        return if (list.subList(at, at + run.size) == run) at else null
    }

    /** [m] on [path], whose index i is index i - [by] of the old path. */
    private fun shift(
        m: NbMotion,
        path: List<Any>,
        by: Int,
    ): NbMotion {
        if (by == 0 && path == m.path) return m
        val d = by.toFloat()
        return m.copy(
            path = path,
            q = m.q + d,
            target = m.target + d,
            start = m.start + d,
            toward = m.toward + d,
            home = m.home + by,
            grip = if (m.grip >= 0) m.grip + by else m.grip,
            slot = if (m.slot >= 0) m.slot + by else m.slot,
        )
    }

    /** Drops the path states q can no longer reach (outside the motion's range); the residual's slot stays. */
    private fun trim(m: NbMotion): NbMotion {
        var lo = floor(minOf(m.q, m.target, m.start)).toInt()
        var hi = ceil(maxOf(m.q, m.target, m.start)).toInt()
        if (m.grip >= 0) {
            lo = minOf(lo, m.grip - 1)
            hi = maxOf(hi, m.grip)
        }
        if (m.slot >= 0) {
            lo = minOf(lo, m.slot)
            hi = maxOf(hi, m.slot)
        }
        lo = lo.coerceIn(0, m.path.lastIndex)
        hi = hi.coerceIn(lo, m.path.lastIndex)
        // A motion keeps a pair: at an index it still has its neighbour (drawn Gone, never a missing layer).
        if (lo == hi) {
            if (hi < m.path.lastIndex) {
                hi++
            } else if (lo > 0) {
                lo--
            }
        }
        if (lo == 0 && hi == m.path.lastIndex) return m
        val home = m.home.coerceIn(lo, hi)
        return shift(m.copy(home = home), m.path.subList(lo, hi + 1), -lo)
    }

    /** A catch: [m] heads to index [t] from its current (q, v) (M2: velocity kept, its excursion capped). */
    private fun retarget(
        m: NbMotion,
        t: Float,
        now: Long,
        env: NbNavEnv,
    ): NbMotion = toSpring(m, t, now, env, release = false)

    private fun gestureStart(
        state: NbNavState,
        event: NbNavEvent.GestureStart,
    ): NbNavResult {
        if (event.back.isEmpty() || event.back.last() == state.top) return NbNavResult(state)
        val backTop = event.back.last()
        val m = state.motion
        val base: NbMotion
        var ended: NbMotion? = null
        if (m == null) {
            base =
                NbMotion(
                    path = listOf(backTop, state.top),
                    kind = event.kind,
                    e = event.e,
                    q = 1f,
                    v = 0f,
                    drive = NbDrive.Spring,
                    target = 0f,
                    home = 1,
                )
        } else {
            val t = m.path.indexOf(state.top)
            val b = m.path.indexOf(backTop)
            // The finger takes the motion over only between the held page and the page below it: anything else (the
            // motion is further away) stays as it runs, and the gesture's commit is a Back (the host's target).
            if (t < 1 || b != t - 1 || m.q < b || m.q > t || !m.kind.stacked) return NbNavResult(state)
            base = m
            ended = null
        }
        val grip = base.path.indexOf(state.top)
        val finger =
            base.copy(
                drive = NbDrive.Finger,
                v = 0f,
                target = (grip - 1).toFloat(),
                toward = (grip - 1).toFloat(),
                grip = grip,
                u0 = upperShare(base, grip, base.q),
                x0 = event.x,
                width = event.width,
                samples = emptyList(),
                lastSampleAt = Long.MIN_VALUE,
                sampleDrawnAt = Long.MIN_VALUE,
                commitStack = event.back,
                gestureId = event.id,
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
        // M3: screen space. The upper layer is at tx = e W u, so the finger's travel dx moves it by exactly dx: the
        // page never moves against the finger, from either edge, fresh or caught.
        val u =
            if (event.x.isNaN() || m.x0.isNaN() || m.width <= 0f) {
                (m.u0 + event.progress.coerceIn(0f, 1f)).coerceIn(0f, 1f)
            } else {
                (m.u0 + m.e * (event.x - m.x0) / m.width).coerceIn(0f, 1f)
            }
        val q = qOfUpperShare(m, m.grip, u)
        val last = m.samples.lastOrNull()?.timeMillis
        val t = if (last != null && event.timeMillis <= last) last + 1 else event.timeMillis
        val samples = (m.samples + NbFingerSample(t, u)).takeLast(MAX_SAMPLES)
        val v = trackedVelocity(samples) * slope(m, u)
        return NbNavResult(
            state.copy(motion = m.copy(q = q, v = v, samples = samples, lastSampleAt = event.now, sampleDrawnAt = Long.MIN_VALUE)),
        )
    }

    @Suppress("ReturnCount") // No finger, another gesture, instant, a spring.
    private fun gestureEnd(
        state: NbNavState,
        event: NbNavEvent.GestureEnd,
        env: NbNavEnv,
    ): NbNavResult {
        val m = state.motion
        if (m == null || m.drive != NbDrive.Finger) return NbNavResult(state)
        if (event.id != 0L && m.gestureId != 0L && event.id != m.gestureId) return NbNavResult(state)
        val stack = if (event.commit) m.commitStack ?: state.stack else state.stack
        val side = if (event.commit) (m.grip - 1).toFloat() else m.grip.toFloat()
        if (env.instant || env.durationScale <= 0f) return NbNavResult(NbNavState(stack, null), ended = m)
        return NbNavResult(NbNavState(stack, trim(toSpring(m, side, event.now, env, release = true))))
    }

    private fun abort(state: NbNavState): NbNavResult {
        val m = state.motion ?: return NbNavResult(state)
        return NbNavResult(state.copy(motion = null), ended = m)
    }

    private fun frame(
        state: NbNavState,
        event: NbNavEvent.Frame,
        env: NbNavEnv,
    ): NbNavResult {
        val m = state.motion ?: return NbNavResult(state)
        val residual = m.residual?.let { advance(it, event, env) }
        val next = advance(m, event, env)
        val keptResidual = residual?.takeIf { it.drive != NbDrive.Idle }
        if (next.drive == NbDrive.Idle && keptResidual == null) {
            return NbNavResult(state.copy(motion = null), ended = m.copy(q = m.target, v = 0f, residual = null))
        }
        val main = if (next.drive == NbDrive.Idle) m.copy(q = m.target, v = 0f, start = m.target, v0 = 0f) else next
        val out = main.copy(residual = keptResidual, slot = if (keptResidual == null) -1 else main.slot)
        return NbNavResult(state.copy(motion = trim(out)))
    }

    /** One frame of [m] alone (its residual untouched); drive Idle when it settled. */
    @Suppress("ReturnCount") // The R7 clock: finger, first two frames, continuation, end.
    private fun advance(
        m: NbMotion,
        event: NbNavEvent.Frame,
        env: NbNavEnv,
    ): NbMotion {
        val frame = event.frameNanos
        if (m.drive == NbDrive.Finger) {
            // The frame that draws the last sample: a release continues from it (R7).
            if (m.lastSampleAt == Long.MIN_VALUE || m.sampleDrawnAt != Long.MIN_VALUE) return m
            return m.copy(sampleDrawnAt = frame)
        }
        if (m.drive == NbDrive.Idle) return m
        val alreadyDrawn = event.drawnAt != Long.MIN_VALUE
        val cap = NbClock.stepCap(env.framePeriod)
        val play =
            when {
                // From rest (pre-advanced one display frame): the first two drawn frames are one display frame apart,
                // whether the run was set up by an input (its first frame comes after this event) or in a composition
                // (that composition's frame drew the first pose), so every trigger draws the same curve.
                m.origin == null && m.drawn == 0 && !alreadyDrawn -> m.play

                m.origin == null && m.drawn <= 1 -> m.play + env.framePeriod

                m.lastFrame != Long.MIN_VALUE -> m.play + (frame - m.lastFrame).coerceIn(0L, cap)

                // The first frame of a continuation (a release, a catch): at most one display frame from its origin,
                // so a long frame at a release holds the pose instead of jumping it (round 6, measurement 3 and 4).
                m.origin != null && abs(frame - m.origin) <= SAME_BASE_NANOS -> (frame - m.origin).coerceIn(0L, env.framePeriod)

                else -> m.play
            }
        val drawnNow = if (m.origin == null && m.drawn == 0 && alreadyDrawn) 2 else m.drawn + 1
        val (q, v, done) = evaluate(m, play, env)
        if (done) return m.copy(q = m.target, v = 0f, drive = NbDrive.Idle)
        return m.copy(q = q, v = v, play = play, lastFrame = frame, drawn = drawnNow)
    }

    /** The motion [m] continues as a spring towards index [side] from its current (q, v) (R0, M2, R7). */
    private fun toSpring(
        m: NbMotion,
        side: Float,
        now: Long,
        env: NbNavEnv,
        release: Boolean,
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
        val base =
            m.copy(drive = NbDrive.Spring, target = side, commitStack = null, samples = emptyList(), toward = toward, grip = -1)
        return springFrom(base, m.q, v, origin, now, env, keepAway = !release)
    }

    /**
     * A spring of [m] towards its target from [start] with velocity [v]. Towards the target R0 applies (capped towards
     * a visible rest). Away from it a release drops the velocity (R0) and a catch keeps it (M2), capped so that the
     * closed-form excursion never passes the far end of the pair q lies in. A new run from rest (no [origin], no
     * velocity) is pre-advanced one display frame (R7); a continuation keeps q and is timed from [origin].
     */
    @Suppress("LongParameterList") // A spring: motion, start state, origin, clock, world, rule.
    private fun springFrom(
        m: NbMotion,
        start: Float,
        v: Float,
        origin: Long?,
        now: Long,
        env: NbNavEnv,
        keepAway: Boolean = false,
    ): NbMotion {
        val towards = sign(m.target - start)
        val v0 =
            if (keepAway && v.isFinite() && v != 0f && v * towards < 0f) {
                val room = if (v > 0f) ceil(start) - start else start - floor(start)
                awayCap(m.kind.spring, distance = abs(m.target - start), room = room, speed = abs(v)) * sign(v)
            } else {
                val visible = visibleRest(m, start)
                val distance = abs(m.target - start)
                val raw = if (v.isFinite()) (v * towards).coerceAtLeast(0f) else 0f
                (if (visible) raw.coerceAtMost(m.kind.spring.crossingVelocity(distance)) else raw) * towards
            }
        val run = m.copy(q = start, v = v0, start = start, v0 = v0, play = 0L, lastFrame = Long.MIN_VALUE, drawn = 0)
        // A continuation (a catch, a release) or a hand-over velocity: timed from its origin, q unchanged now (C1).
        if (origin != null || v0 != 0f) return run.copy(origin = origin ?: now)
        val first = env.framePeriod
        val (q, vv, _) = evaluate(run, first, env)
        return run.copy(origin = null, play = first, q = q, v = vv)
    }

    /**
     * True when the motion's target rests on screen with the moving layer visible: a stack pair approached from below
     * (the upper layer comes to rest), and every pager and swap. Its start velocity is capped so the spring never
     * crosses it (lemma 1). A stack target approached from above takes the upper layer off screen; it is not capped,
     * and the motion ends at its first crossing (lemma 2).
     */
    private fun visibleRest(
        m: NbMotion,
        start: Float,
    ): Boolean = !m.kind.stacked || m.target > start

    /**
     * The largest speed (at most [speed]) away from a target [distance] away whose closed-form excursion stays within
     * [room] (M2): the spring's peak displacement from the target is at most `distance + room`.
     */
    internal fun awayCap(
        spring: NbSpring,
        distance: Float,
        room: Float,
        speed: Float,
    ): Float {
        if (room <= 0f || speed <= 0f) return 0f
        val limit = distance + room
        if (peak(spring, distance, speed) <= limit) return speed
        var lo = 0f
        var hi = speed
        repeat(BISECTIONS) {
            val mid = (lo + hi) / 2f
            if (peak(spring, distance, mid) <= limit) lo = mid else hi = mid
        }
        return lo
    }

    private const val BISECTIONS = 32

    /** The largest displacement from the target of a spring that starts [x0] from it moving away at [speed]. */
    internal fun peak(
        spring: NbSpring,
        x0: Float,
        speed: Float,
    ): Float {
        val w = spring.omega.toDouble()
        val z = spring.damping.toDouble()
        val x = x0.toDouble()
        val v = speed.toDouble()
        // Displacement x(t) of the closed form; the peak is where x'(t) = 0, found on a fine grid and refined.
        val f: (Double) -> Double =
            if (z > 1.0) {
                val s = sqrt(z * z - 1.0)
                val r1 = -w * (z - s)
                val r2 = -w * (z + s)
                val b = (v - r1 * x) / (r2 - r1)
                val a = x - b
                { t -> a * exp(r1 * t) + b * exp(r2 * t) }
            } else {
                { t -> (x + (v + w * x) * t) * exp(-w * t) }
            }
        var best = x
        var t = 0.0
        val dt = 1.0 / (w * PEAK_STEPS_PER_RAD)
        while (t < PEAK_HORIZON_RAD / w) {
            best = maxOf(best, f(t))
            t += dt
        }
        return best.toFloat()
    }

    private const val PEAK_STEPS_PER_RAD = 200.0
    private const val PEAK_HORIZON_RAD = 6.0

    /** (q, v, ended) of the spring of [m] at [play] ns: ended when it settled or crossed its target. */
    @Suppress("ReturnCount") // At its target, settled, past the target, running.
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
        // Lemma 2: a target that takes the upper layer off screen ends at its first crossing; nothing ever leaves the
        // path.
        if ((x - m.target) * (m.start - m.target) < 0f) return Triple(m.target, 0f, true)
        if (x < 0f || x > m.path.lastIndex) return Triple(m.target, 0f, true)
        return Triple(x, anim.getVelocityVectorFromNanos(t).value, false)
    }

    private const val MIN_SCALE = 1e-3f
    private const val HALF = 0.5f

    /** The finger's velocity of q at [now]: 0 once it rested longer than the platform's stopped-pointer time. */
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

    /** The travel share of the upper layer [index] at [q]: `1 - s^gamma`, s = 1 - (index - q) its shown share. */
    fun upperShare(
        m: NbMotion,
        index: Int,
        q: Float,
    ): Float {
        val d = (index - q).coerceIn(0f, 1f)
        if (!m.kind.stacked) return d
        return 1f - (1f - d).pow(NbPhysics.PAGE_GAMMA)
    }

    /** The upper layer's travel share of a held motion (its grip) now. */
    fun upperShare(m: NbMotion): Float = upperShare(m, if (m.grip >= 0) m.grip else ceil(m.q).toInt(), m.q)

    /** The inverse of [upperShare]: the q at which the upper layer [index] has travelled [u]. */
    fun qOfUpperShare(
        m: NbMotion,
        index: Int,
        u: Float,
    ): Float {
        val d = if (!m.kind.stacked) u.coerceIn(0f, 1f) else 1f - (1f - u.coerceIn(0f, 1f)).pow(1f / NbPhysics.PAGE_GAMMA)
        return index - d
    }

    /** dq / du at [u]. */
    private fun slope(
        m: NbMotion,
        u: Float,
    ): Float {
        if (!m.kind.stacked) return -1f
        val rest = (1f - u).coerceAtLeast(MIN_REST)
        return -rest.pow(1f / NbPhysics.PAGE_GAMMA - 1f) / NbPhysics.PAGE_GAMMA
    }

    /** The finger's velocity (travel share per second) from [samples], the platform's least-squares tracker. */
    private fun trackedVelocity(samples: List<NbFingerSample>): Float {
        if (samples.size < 2) return 0f
        val tracker = VelocityTracker1D(isDataDifferential = false)
        samples.forEach { tracker.addDataPoint(it.timeMillis, it.u) }
        val v = runCatching { tracker.calculateVelocity() }.getOrDefault(0f)
        return if (v.isFinite()) v else 0f
    }

    /**
     * The pose of the layer at offset [d] = i - q of a path of [kind] (round 6): the two-layer poses of round 5 for
     * `|d| <= 1`, invisible beyond (a page fully covered or fully out, a value one step away).
     */
    fun pathPose(
        kind: NbKind,
        d: Float,
        e: Int,
        width: Float,
        dark: Boolean,
    ): NbLayerPose =
        when {
            abs(d) >= 1f -> NbLayerPose.Gone
            kind.stacked && d >= 0f -> NbPoses.stack(1f - d, e, width, dark).first
            kind.stacked -> NbPoses.stack(-d, e, width, dark).second
            kind == NbKind.Pager -> NbLayerPose(tx = e * d * width)
            else -> NbLayerPose(alpha = (1f - 2f * abs(d)).coerceAtLeast(0f))
        }

    /** The pose of [key] in [m] (its residual group composed in), [NbLayerPose.Rest] when it is not in it. */
    fun poseOf(
        m: NbMotion,
        key: Any,
        width: Float,
        dark: Boolean,
    ): NbLayerPose {
        val res = m.residual
        // A key of the residual group draws composed while the group shows it (or the main path does not have it);
        // a key the main path brings back while the group no longer shows it is the main path's.
        val inGroup =
            res != null && key in res.path && (res.path.indexOf(key) in res.visible || key !in m.path || m.path.indexOf(key) == m.slot)
        if (res != null && m.slot >= 0 && inGroup) {
            val group = pathPose(m.kind, m.slot - m.q, m.e, width, dark)
            val own = pathPose(res.kind, res.path.indexOf(key) - res.q, res.e, width, dark)
            return group.compose(own, width)
        }
        val i = m.path.indexOf(key)
        if (i < 0) return NbLayerPose.Rest
        return pathPose(m.kind, i - m.q, m.e, width, dark)
    }

    /** The keys a motion draws, lowest first: the visible path layers, the residual group in the place of its slot. */
    fun layerKeys(m: NbMotion): List<Any> {
        val res = m.residual
        val group = res?.let { r -> (r.visible.map { r.path[it] } + r.targetKey).distinct().sortedBy { r.path.indexOf(it) } }.orEmpty()
        return buildList {
            for (i in m.visible) {
                if (res != null && i == m.slot) addAll(group) else add(m.path[i])
            }
            // The group outside the visible pair is still drawn while it moves (under or over the pair by its index).
            if (res != null && m.slot !in m.visible) {
                if (m.slot < m.visible.first) addAll(0, group) else addAll(group)
            }
        }.distinct()
    }
}
