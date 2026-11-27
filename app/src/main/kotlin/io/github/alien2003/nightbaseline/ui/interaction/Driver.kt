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

    /** A frame time further than this from [nowNanos] is not in its base (a test's virtual clock), ns. */
    private const val SAME_BASE_NANOS = 1_000_000_000L

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
 * The scalar p of one host (ADR 0011): p in [0, 1], driven only by [spring] with the settle tolerance
 * [NbPhysics.P_EPS]. A gesture writes p through [track] (its velocity is measured on the same p); a release continues
 * on the same spring from (p, v) ([release]). Under reduced motion every run snaps.
 *
 * Rule R7 (time origin): a run is a pure function of the time since it was asked for. Its origin is the instant of
 * the request ([NbClock.nowNanos] at the tap's composition, at the finger's release), and every frame draws the
 * spring at `frame time - origin`: the first frame after a release already moves by the time that passed (no frame
 * repeats the release pose, which a run started at its first frame does: Compose's `Animatable` shows its start value
 * in its first frame), and a long frame catches up exactly as the spring would have moved. The run ends at the first
 * crossing of a bound (0 or 1) or when the spring settles.
 *
 * Screens never see this class's mutators: hosts in this package own it.
 */
@Stable
class NbDriver internal constructor(
    internal var spring: NbSpring,
    private val reduced: () -> Boolean,
) {
    private var value by mutableFloatStateOf(0f)
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

    /** The current p (snapshot state: read it in draw or layer lambdas). */
    val p: Float get() = value

    /** The current velocity, p per second. */
    val v: Float get() = velocity

    /** True while the spring runs. */
    val isRunning: Boolean get() = running

    /** The velocity measured from the samples given to [track], p per second (0 when there are too few). */
    internal fun trackedVelocity(): Float {
        val velocity = runCatching { tracker.calculateVelocity() }.getOrDefault(0f)
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

    private fun moving(at: Long): Boolean =
        lastSampleAt != Long.MIN_VALUE && at - lastSampleAt <= NbPhysics.POINTER_STOPPED_MS * NANOS_PER_MS

    /** Puts p at [value] at once (stops a running spring). */
    internal suspend fun snapTo(value: Float) {
        mutex.mutate {
            this.value = value.coerceIn(0f, 1f)
            velocity = 0f
        }
    }

    /** A gesture moved p to [value] at [timeMillis] (the event's time). */
    internal suspend fun track(
        value: Float,
        timeMillis: Long,
    ) {
        val t = if (timeMillis <= lastTrack) lastTrack + 1 else timeMillis
        lastTrack = t
        lastSampleAt = NbClock.nowNanos()
        lastDrawnAt = Long.MIN_VALUE
        val id = ++samples
        tracker.addDataPoint(t, value)
        snapTo(value)
        // The frame that draws this sample: a release continues from it (the finger's pose is drawn in that frame).
        withFrameNanos { if (id == samples) lastDrawnAt = it }
    }

    /** Clears the gesture samples (a new gesture starts). */
    internal fun resetTracking() {
        tracker.resetTracking()
        lastTrack = Long.MIN_VALUE
        lastSampleAt = Long.MIN_VALUE
        lastDrawnAt = Long.MIN_VALUE
        samples++
    }

    /**
     * Runs p to [target] on [spring] from the current p with start velocity [v0], from the time origin [origin]
     * ([NbClock.nowNanos] of the request; null: the first frame); a snap under reduced motion.
     */
    internal suspend fun run(
        target: Float,
        v0: Float = 0f,
        origin: Long? = null,
    ) {
        if (reduced()) {
            snapTo(target)
            return
        }
        mutex.mutate {
            val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
            val start = value
            if (scale <= 0f || (start == target && v0 == 0f)) {
                value = target
                velocity = 0f
                return@mutate
            }
            val anim = TargetBasedAnimation(spring.spec(), Float.VectorConverter, start, target, AnimationVector1D(v0))
            running = true
            try {
                var t0 = Long.MIN_VALUE
                var done = false
                while (!done) {
                    withFrameNanos { frame ->
                        if (t0 == Long.MIN_VALUE) t0 = NbClock.startOf(origin, frame)
                        val play = ((frame - t0) / scale).toLong().coerceAtLeast(0L)
                        val settled = anim.isFinishedFromNanos(play)
                        val x = anim.getValueFromNanos(play)
                        done = settled || x > 1f || x < 0f
                        value = if (settled) target else x.coerceIn(0f, 1f)
                        velocity = if (done) 0f else anim.getVelocityVectorFromNanos(play).value
                    }
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
