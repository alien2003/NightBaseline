package io.github.alien2003.nightbaseline.ui.interaction

import androidx.annotation.VisibleForTesting
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

/**
 * The release rule R0 of ADR 0011 as a pure function: the start velocity (p per second) of the spring that finishes a
 * gesture released at ([p], [v]) towards [target].
 *
 * - Velocity away from the target is dropped (the motion never starts backwards).
 * - Towards a visible resting pose ([visibleTarget]: a cancel, a sheet returning, a pager settling) the velocity is
 *   capped at [NbSpring.crossingVelocity] of the remaining distance, so the spring reaches the target without
 *   crossing it (lemma 1).
 * - Towards a target that leaves the screen (a commit, a hide) nothing is capped: the driver's bounds end the motion
 *   at the first crossing, which is earlier than the uncapped spring would settle (lemma 2).
 */
object NbRelease {
    /** The start velocity. */
    fun startVelocity(
        p: Float,
        v: Float,
        target: Float,
        spring: NbSpring,
        visibleTarget: Boolean,
    ): Float {
        val distance = abs(target - p)
        val towards = sign(target - p)
        var speed = if (v.isFinite()) (v * towards).coerceAtLeast(0f) else 0f
        if (visibleTarget) speed = speed.coerceAtMost(spring.crossingVelocity(distance))
        // At the target (distance 0) towards is 0, so the start velocity is 0 too.
        return speed * towards
    }
}

/**
 * The clock of the framework's requests and releases (ADR 0011 rule R7), in the time base of the frame clock: the
 * platform's Choreographer frame times are `System.nanoTime`, so in the app a frame time and [nowNanos] are directly
 * comparable. Tests that drive a virtual frame clock set [nowNanos] to that clock; a clock in another base is
 * detected ([startOf]) and the run then starts at its first frame.
 */
internal object NbClock {
    /** Now, ns, in the frame clock's time base. */
    @Volatile
    @VisibleForTesting
    var nowNanos: () -> Long = System::nanoTime

    /**
     * The display's frame period, ns: the longest first step of a run from rest (rule R7). Set by [NbInteractionRoot]
     * from the window's display; 60 Hz until it is known.
     */
    @Volatile
    var framePeriodNanos: Long = DEFAULT_FRAME_NANOS

    /** A frame time further than this from [nowNanos] is not in its base (a test's virtual clock), ns. */
    private const val SAME_BASE_NANOS = 1_000_000_000L

    /** 60 Hz, ns. */
    const val DEFAULT_FRAME_NANOS = 16_666_667L

    /** [NbPhysics.FRAME_STALL_MS] in ns. */
    const val STALL_NANOS = NbPhysics.FRAME_STALL_MS * 1_000_000L

    /**
     * The time origin of a run whose first frame is at [frameNanos], asked for at [origin] ([nowNanos] at the request
     * or release, null when there is none): the earlier of the two, so the first frame already shows the motion that
     * elapsed since the request, and never later than the frame (no frame shows a time before the start).
     */
    fun startOf(
        origin: Long?,
        frameNanos: Long,
    ): Long = if (origin != null && abs(frameNanos - nowNanos()) <= SAME_BASE_NANOS) min(origin, frameNanos) else frameNanos
}

/**
 * The play time of one run (ADR 0011 rule R7, round 3): the time the spring is drawn at in each frame.
 *
 * - The first frame of a run that **continues a motion** ([moving]: a release with the finger's velocity, a reversal,
 *   a retarget) is at `frame - origin` from the origin (the frame that drew the last sample, the last frame of the
 *   motion it reverses): no frame repeats the last pose.
 * - The first frame of a run **from rest** is at most one display frame ([NbClock.framePeriodNanos]) into the spring:
 *   the screen showed the start pose until then, so the time a slow set-up frame took (composing the entering page,
 *   a first open) is never caught up as a jump (V2-3, V2-4: such runs started at p 0.2 to 0.95).
 * - Every later frame advances by the frame interval, at most [NbPhysics.FRAME_STALL_MS]: a stalled frame slows the
 *   motion instead of jumping it (V2-1: a long frame used to be caught up in one frame, hundreds of px).
 *
 * Without stalls every frame is exactly the spring at `frame - origin` (a pure function of time).
 */
internal class NbPlayClock(
    private val origin: Long?,
    private val moving: Boolean,
) {
    private var last = Long.MIN_VALUE
    private var play = 0L

    /** The play time (ns) of the frame at [frameNanos]. */
    fun at(frameNanos: Long): Long {
        play =
            if (last == Long.MIN_VALUE) {
                val cap = if (moving) NbClock.STALL_NANOS else NbClock.framePeriodNanos
                (frameNanos - NbClock.startOf(origin, frameNanos)).coerceIn(0L, cap)
            } else {
                play + (frameNanos - last).coerceIn(0L, NbClock.STALL_NANOS)
            }
        last = frameNanos
        return play
    }
}

/** How a [nbRunSpring] ended. */
internal enum class NbRunEnd {
    /** The spring settled at its target. */
    Settled,

    /** The value crossed a bound and was stopped there. */
    Bound,
}

/**
 * Runs [spring] from [start] with [v0] (units per second) towards [target] on the R7 clock ([NbPlayClock]) from
 * [origin], calling [onFrame] with the value and its velocity in every frame; the run ends when the spring settles
 * within [threshold] or the value crosses [lower] or [upper] (the value is then the bound). The one spring loop of the
 * framework: every navigation scalar, every reveal and the overscroll return run on it.
 */
@Suppress("LongParameterList") // One spring run: spring, start state, target, clock, bounds, sink.
internal suspend fun nbRunSpring(
    spring: NbSpring,
    threshold: Float,
    start: Float,
    target: Float,
    v0: Float,
    origin: Long?,
    lower: Float,
    upper: Float,
    onFrame: (value: Float, velocity: Float, frameNanos: Long) -> Unit,
): NbRunEnd {
    val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    if (scale <= 0f || (start == target && v0 == 0f)) {
        onFrame(target, 0f, Long.MIN_VALUE)
        return NbRunEnd.Settled
    }
    val anim = TargetBasedAnimation(spring.spec(threshold), Float.VectorConverter, start, target, AnimationVector1D(v0))
    val clock = NbPlayClock(origin, moving = v0 != 0f)
    var end: NbRunEnd? = null
    while (end == null) {
        withFrameNanos { frame ->
            val play = (clock.at(frame) / scale).toLong()
            val settled = anim.isFinishedFromNanos(play)
            val x = anim.getValueFromNanos(play)
            end =
                when {
                    settled -> NbRunEnd.Settled
                    x > upper || x < lower -> NbRunEnd.Bound
                    else -> null
                }
            val value = if (settled) target else x.coerceIn(lower, upper)
            onFrame(value, if (end != null) 0f else anim.getVelocityVectorFromNanos(play).value, frame)
        }
    }
    return end ?: NbRunEnd.Settled
}

/**
 * The scalar p of one host (ADR 0011): p in [0, 1], driven only by [spring] with the settle tolerance
 * [NbPhysics.P_EPS]. A gesture writes p through [track] (its velocity is measured on the same p); a release continues
 * on the same spring from (p, v) ([release]). Under reduced motion every run snaps. Every run follows rule R7
 * ([NbPlayClock]) and ends at the first crossing of a bound (0 or 1) or when the spring settles.
 *
 * Screens never see this class's mutators: hosts in this package own it.
 */
@Stable
class NbDriver internal constructor(
    internal var spring: NbSpring,
    initial: Float = 0f,
    private val reduced: () -> Boolean,
) {
    private var value by mutableFloatStateOf(initial.coerceIn(0f, 1f))
    private var velocity = 0f
    private var running by mutableStateOf(false)
    private val mutex = MutatorMutex()
    private val tracker = VelocityTracker1D(isDataDifferential = false)
    private var lastTrack = Long.MIN_VALUE

    /** [NbClock.nowNanos] when the last gesture sample arrived. */
    private var lastSampleAt = Long.MIN_VALUE

    /** The frame time of the frame that drew the last sample (MIN_VALUE until it is drawn). */
    private var lastDrawnAt = Long.MIN_VALUE
    private var samples = 0

    /** The frame time of the last frame a run drew (the origin of a run that continues it: a reversal, a retarget). */
    internal var lastFrameAt: Long = Long.MIN_VALUE
        private set

    /** The target of the running (or last) run: 1 or 0. */
    internal var heading: Float = 1f
        private set

    /** The current p (snapshot state: read it in draw or layer lambdas). */
    val p: Float get() = value

    /** The current velocity, p per second. */
    val v: Float get() = velocity

    /** True while the spring runs. */
    val isRunning: Boolean get() = running

    /** The slope dp / d(finger) of the last sample ([track]): the finger's velocity times it is p's velocity. */
    private var lastSlope = 1f

    /**
     * The velocity of p measured from the samples given to [track], p per second (0 when there are too few): the
     * finger's own velocity, measured on its travel (linear in the finger), times the slope of the gesture map at the
     * last sample. A map that is not linear in the finger (the page pose, `stackPop`) does not bend the measurement:
     * the page leaves the release at exactly the finger's speed (gesture-release velocity continuity).
     */
    internal fun trackedVelocity(): Float {
        val velocity = runCatching { tracker.calculateVelocity() }.getOrDefault(0f) * lastSlope
        return if (velocity.isFinite()) velocity else 0f
    }

    /**
     * The finger's velocity at the release instant [at] ([NbClock.nowNanos]): the tracked velocity, or 0 when the
     * finger had rested longer than [NbPhysics.POINTER_STOPPED_MS] before it let go (the platform's rule for a
     * pointer that stopped; the tracker alone cannot know it, as no sample arrives while the finger rests).
     */
    internal fun releaseVelocity(at: Long): Float = if (moving(at)) trackedVelocity() else 0f

    /**
     * The time origin of a release at [at]: a finger still moving when it let go continues from its last sample, from
     * the frame that drew it (the platform reports no position between the last move and the lift, so the page goes
     * on from the last drawn (p, v) as the finger did, instead of holding that pose until the release and the frame
     * after it); a finger that had rested starts from rest at the release.
     */
    internal fun releaseOrigin(at: Long): Long =
        when {
            !moving(at) -> at
            lastDrawnAt != Long.MIN_VALUE -> lastDrawnAt
            else -> lastSampleAt
        }

    /** The origin of a run that continues the current motion: its last drawn frame, else now. */
    internal fun continueOrigin(): Long = if (lastFrameAt != Long.MIN_VALUE) lastFrameAt else NbClock.nowNanos()

    private fun moving(at: Long): Boolean =
        lastSampleAt != Long.MIN_VALUE && at - lastSampleAt <= NbPhysics.POINTER_STOPPED_MS * NANOS_PER_MS

    /** Puts p at [value] at once (stops a running spring). */
    internal suspend fun snapTo(value: Float) {
        mutex.mutate {
            this.value = value.coerceIn(0f, 1f)
            velocity = 0f
        }
    }

    /**
     * A gesture moved p to [value] at [timeMillis] (the event's time); the finger is at [finger] of its travel there
     * and the gesture map's slope dp / d(finger) is [slope] (p itself and 1 for a linear gesture).
     */
    internal suspend fun track(
        value: Float,
        timeMillis: Long,
        finger: Float = value,
        slope: Float = 1f,
    ) {
        val t = if (timeMillis <= lastTrack) lastTrack + 1 else timeMillis
        lastTrack = t
        lastSampleAt = NbClock.nowNanos()
        lastDrawnAt = Long.MIN_VALUE
        val id = ++samples
        tracker.addDataPoint(t, finger)
        lastSlope = slope
        snapTo(value)
        // The frame that draws this sample: a release continues from it (the finger's pose is drawn in that frame).
        withFrameNanos {
            if (id == samples) {
                lastDrawnAt = it
                lastFrameAt = it
            }
        }
    }

    /** Clears the gesture samples (a new gesture starts). */
    internal fun resetTracking() {
        tracker.resetTracking()
        lastSlope = 1f
        lastTrack = Long.MIN_VALUE
        lastSampleAt = Long.MIN_VALUE
        lastDrawnAt = Long.MIN_VALUE
        samples++
    }

    /**
     * Runs p to [target] on [spring] from the current p with start velocity [v0], from the time origin [origin]
     * ([NbClock.nowNanos] of the request, or the frame a continued motion last drew; null: the first frame), on the
     * R7 clock ([NbPlayClock]); a snap under reduced motion.
     */
    internal suspend fun run(
        target: Float,
        v0: Float = 0f,
        origin: Long? = null,
    ) {
        heading = target
        if (reduced()) {
            snapTo(target)
            return
        }
        mutex.mutate {
            running = true
            try {
                nbRunSpring(spring, NbPhysics.P_EPS, value, target, v0, origin, 0f, 1f) { x, v, frame ->
                    value = x
                    velocity = v
                    if (frame != Long.MIN_VALUE) lastFrameAt = frame
                }
            } finally {
                running = false
            }
        }
    }

    /**
     * Finishes a gesture released at [at] ([NbClock.nowNanos]): towards [target] from the finger's velocity at the
     * release by rule R0, timed from the finger's last known motion ([releaseOrigin], R7).
     */
    internal suspend fun release(
        target: Float,
        visibleTarget: Boolean,
        at: Long = NbClock.nowNanos(),
    ) {
        val v0 = NbRelease.startVelocity(p, releaseVelocity(at), target, spring, visibleTarget)
        val origin = releaseOrigin(at)
        resetTracking()
        run(target, v0, origin)
    }

    /** Stops a running spring where it is. */
    internal suspend fun stop() {
        mutex.mutate { velocity = 0f }
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
    }
}

/** What a press sample measures. */
enum class NbPressSample {
    /** The press scale. */
    Scale,

    /** The light's strength. */
    Glow,

    /** The pressed highlight's level (the grey state layer of buttons, rows and plain clickables). */
    Highlight,
}

/** One drawn frame of a host, for tests (ADR 0011 I1, I2, I7, I9). */
data class NbFrame(
    /** The host. */
    val host: String,
    /** The kind of the change. */
    val kind: NbKind,
    /** The spring object that drives it. */
    val spring: NbSpring,
    /** p in this frame. */
    val p: Float,
    /** The state being reached and the one being left (keys of the host). */
    val to: Any?,
    val from: Any?,
    /** The poses drawn. */
    val toPose: NbLayerPose,
    val fromPose: NbLayerPose,
    /** True while a finger drives p. */
    val gesture: Boolean,
    /** Frame time of the host's clock, ns (from the drawn layer). */
    val width: Float,
)

/**
 * Test hook of the framework (debug and tests only set it): every drawn frame of a running change reports an [NbFrame],
 * every scroll container registers itself, every fling reports its velocity. A no-op in the app.
 */
object NbMotionProbe {
    /** Receives the frames of every host. */
    @Volatile
    var frames: ((NbFrame) -> Unit)? = null

    /** Receives (container id, velocity) for every fling the framework's scroll containers start. */
    @Volatile
    var flings: ((String, Float) -> Unit)? = null

    /** Receives (container id, px) when a fling ends: the distance the fling itself scrolled the content. */
    @Volatile
    var flingEnds: ((String, Float) -> Unit)? = null

    /** Receives every drawn press sample: the scale or the light of a pressable element (the press model test). */
    @Volatile
    var press: ((element: Any, sample: NbPressSample, value: Float) -> Unit)? = null

    /** Receives the overscroll translation (x, y px) of every frame in which some content is translated past an edge. */
    @Volatile
    var overscroll: ((Float, Float) -> Unit)? = null

    /** Receives the overscroll translation (x, y px) whenever it is written, back to 0 included (its value now). */
    @Volatile
    var overscrollNow: ((Float, Float) -> Unit)? = null

    /** Receives (host id, layer key) whenever a parked layer (placed and drawn ahead, invisible) updates its layer. */
    @Volatile
    var parked: ((String, Any) -> Unit)? = null
}
