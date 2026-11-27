package io.github.alien2003.nightbaseline.ui.interaction

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Stable
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import kotlin.math.abs
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
 * The scalar p of one host (ADR 0011): p in [0, 1], driven only by [spring] with the settle tolerance
 * [NbPhysics.P_EPS]. A gesture writes p through [track] (its velocity is measured on the same p); a release continues
 * on the same spring from (p, v) ([release]). Under reduced motion every run snaps.
 *
 * Screens never see this class's mutators: hosts in this package own it.
 */
@Stable
class NbDriver internal constructor(
    internal var spring: NbSpring,
    private val reduced: () -> Boolean,
) {
    private val animatable: Animatable<Float, AnimationVector1D> =
        Animatable(0f, NbPhysics.P_EPS).apply { updateBounds(0f, 1f) }
    private val tracker = VelocityTracker1D(isDataDifferential = false)
    private var lastTrack = Long.MIN_VALUE

    /** The current p (snapshot state: read it in draw or layer lambdas). */
    val p: Float get() = animatable.value

    /** The current velocity, p per second. */
    val v: Float get() = animatable.velocity

    /** True while the spring runs. */
    val isRunning: Boolean get() = animatable.isRunning

    /** The velocity measured from the samples given to [track], p per second (0 when there are too few). */
    internal fun trackedVelocity(): Float {
        val velocity = runCatching { tracker.calculateVelocity() }.getOrDefault(0f)
        return if (velocity.isFinite()) velocity else 0f
    }

    /** Puts p at [value] at once. */
    internal suspend fun snapTo(value: Float) {
        animatable.snapTo(value.coerceIn(0f, 1f))
    }

    /** A gesture moved p to [value] at [timeMillis]. */
    internal suspend fun track(
        value: Float,
        timeMillis: Long,
    ) {
        val t = if (timeMillis <= lastTrack) lastTrack + 1 else timeMillis
        lastTrack = t
        tracker.addDataPoint(t, value)
        animatable.snapTo(value.coerceIn(0f, 1f))
    }

    /** Clears the gesture samples (a new gesture starts). */
    internal fun resetTracking() {
        tracker.resetTracking()
        lastTrack = Long.MIN_VALUE
    }

    /** Runs p to [target] on [spring] from the current p with start velocity [v0]; a snap under reduced motion. */
    internal suspend fun run(
        target: Float,
        v0: Float = 0f,
    ) {
        if (reduced()) {
            animatable.snapTo(target)
            return
        }
        animatable.animateTo(target, spring.spec(), initialVelocity = v0)
    }

    /** Finishes a gesture: towards [target] from the tracked velocity by rule R0. */
    internal suspend fun release(
        target: Float,
        visibleTarget: Boolean,
    ) {
        val v0 = NbRelease.startVelocity(p, trackedVelocity(), target, spring, visibleTarget)
        resetTracking()
        run(target, v0)
    }

    /** Stops a running spring where it is. */
    internal suspend fun stop() {
        animatable.stop()
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
}
