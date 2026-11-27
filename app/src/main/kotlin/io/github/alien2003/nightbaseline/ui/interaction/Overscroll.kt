package io.github.alien2003.nightbaseline.ui.interaction

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * The ColorOS overscroll (ADR 0011, `reference-analysis.md` 8.2: translate, no stretch, back in about 0.7 s) as a
 * velocity- and distance-conserving state machine over one offset o per axis:
 *
 * - Pull at the edge: o grows by `r(o) * delta` with `r(o) = 0.5 (1 - |o| / 240 dp)` (it saturates by design:
 *   `o = o_max (1 - exp(-r0 * travel / o_max))`).
 * - Drag away from the edge while o != 0: the delta reduces o 1:1 (the content follows the finger) and the remainder
 *   scrolls in the same event.
 * - Fling, in any state: the fling is always performed. A fling towards the edge of a translated content takes o back
 *   first with its own distance (1:1, as a drag does) and scrolls the remainder; otherwise a non-zero o relaxes in
 *   parallel. The velocity the content could not use (it hit the edge) seeds the return spring 1:1. Nothing is
 *   dropped: velocity in = content + offset, and the distance on screen of a flick does not depend on o.
 *   Compose's `performFling` returns the velocity it **consumed**, so the leftover is `velocity - consumed`; a fling
 *   that comes to rest inside the content consumes all of it and moves no offset (no bump away from an edge).
 * - Return: one spring, [NbPhysics.Overscroll], from (o, v); a finger landing during it stops it where it is, at the
 *   down event, and the drag rule applies to that o (the finger owns the content from its first touch: review R1-4,
 *   the spring used to keep running under a new drag, so a flick right after an edge bounce travelled the remaining
 *   offset on top of its own distance). A touch that does not scroll lets the content return when it lifts.
 *
 * Content shorter than its viewport bounces the same way (the whole drag is leftover). Our own implementation of the
 * measured behaviour; no vendor code.
 */
class NbOverscrollEffect(
    private val density: Density,
) : OverscrollEffect {
    private val stateX = mutableFloatStateOf(0f)
    private val stateY = mutableFloatStateOf(0f)

    private var offsetX: Float
        get() = stateX.floatValue
        set(value) {
            stateX.floatValue = value
            NbMotionProbe.overscrollNow?.invoke(value, stateY.floatValue)
        }

    private var offsetY: Float
        get() = stateY.floatValue
        set(value) {
            stateY.floatValue = value
            NbMotionProbe.overscrollNow?.invoke(stateX.floatValue, value)
        }

    /** Return springs running now (a cancelled one ends after its successor started, so a flag would clear too early). */
    private var returning = 0

    /** True once the current touch scrolled: its lift is followed by a fling, which takes the content over. */
    private var scrolledThisTouch = false

    /** The running return spring of each axis (cancelled by a finger landing, or replaced by the next one). */
    private var jobX: Job? = null
    private var jobY: Job? = null

    override val isInProgress: Boolean get() = returning > 0 || offsetX != 0f || offsetY != 0f

    /** The translation past the edges now, px (tests). */
    internal val translation: Offset get() = Offset(offsetX, offsetY)

    override val node: DelegatableNode = OffsetNode()

    private val maxOffset: Float get() = NbPhysics.OVERSCROLL_MAX_DP * density.density

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        if (source != NestedScrollSource.UserInput) return flingScroll(delta, performScroll)
        // The finger owns the content: no return spring moves it under a drag.
        stopReturns()
        scrolledThisTouch = true
        // Plain floats; the snapshot state is written only when the content is (or gets) translated.
        var x = offsetX
        var y = offsetY
        // 1. A drag away from the edge first takes the translation back 1:1; the remainder scrolls below.
        val removedX = relaxed(x, delta.x)
        val removedY = relaxed(y, delta.y)
        x -= removedX
        y -= removedY
        val remainder = Offset(delta.x + removedX, delta.y + removedY)
        // 2. The content scrolls the remainder in the same event.
        val consumed = performScroll(remainder)
        // 3. What the content could not take pulls it past the edge.
        x = pull(x, remainder.x - consumed.x)
        y = pull(y, remainder.y - consumed.y)
        if (x != offsetX) offsetX = x
        if (y != offsetY) offsetY = y
        return delta
    }

    /**
     * A fling's own scroll (not the finger): while the content is translated and the fling moves it back towards its
     * edge, the fling's distance first takes the translation back 1:1, exactly as a drag does, and the remainder
     * scrolls. So a flick right after an edge bounce moves the content on screen exactly as far as the same flick from
     * rest (R1-4: the translation used to relax on its own spring on top of the fling's full distance).
     */
    private fun flingScroll(
        delta: Offset,
        performScroll: (Offset) -> Offset,
    ): Offset {
        val removedX = relaxed(offsetX, delta.x)
        val removedY = relaxed(offsetY, delta.y)
        if (removedX == 0f && removedY == 0f) return performScroll(delta)
        if (removedX != 0f) offsetX -= removedX
        if (removedY != 0f) offsetY -= removedY
        val remainder = Offset(delta.x + removedX, delta.y + removedY)
        val scrolled = performScroll(remainder)
        return Offset(-removedX, -removedY) + scrolled
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        // The fling owns the content from here: a spring a lift started (a touch that did not scroll) stops, so only the
        // fling and the springs below move it.
        stopReturns()
        coroutineScope {
            // A translated content the fling moves back towards its edge is taken back by the fling itself
            // ([flingScroll]); otherwise it relaxes on its spring while the fling runs (it never waits for the fling,
            // nor drops it).
            if (offsetX != 0f && !towardsEdge(offsetX, velocity.x)) startReturn(this, Axis.X, 0f)
            if (offsetY != 0f && !towardsEdge(offsetY, velocity.y)) startReturn(this, Axis.Y, 0f)
            val left = velocity - performFling(velocity)
            // The velocity the content could not use (it reached the edge) seeds the spring 1:1 from wherever the
            // offset is now: it throws the content past the edge and brings it back. A fling too weak to take the
            // whole translation back leaves the rest to the spring.
            if (abs(left.x) > MIN_VELOCITY) {
                startReturn(this, Axis.X, left.x)
            } else if (offsetX != 0f && jobX?.isActive != true) {
                startReturn(this, Axis.X, 0f)
            }
            if (abs(left.y) > MIN_VELOCITY) {
                startReturn(this, Axis.Y, left.y)
            } else if (offsetY != 0f && jobY?.isActive != true) {
                startReturn(this, Axis.Y, 0f)
            }
        }
    }

    /** True when a motion of [velocity] takes a translation of [offset] back (it moves the content towards its edge). */
    private fun towardsEdge(
        offset: Float,
        velocity: Float,
    ): Boolean = offset != 0f && velocity != 0f && sign(offset) != sign(velocity)

    private enum class Axis { X, Y }

    /** Starts the return spring of [axis] in [scope], replacing the running one of that axis. */
    private fun startReturn(
        scope: CoroutineScope,
        axis: Axis,
        velocity: Float,
    ) {
        val job = scope.launch(start = CoroutineStart.UNDISPATCHED) { returnTo(axis, velocity) }
        if (axis == Axis.X) {
            jobX?.cancel()
            jobX = job
        } else {
            jobY?.cancel()
            jobY = job
        }
    }

    /** A lift after a touch that did not scroll, with the content translated and no spring running: it returns. */
    private fun returnsAtLift(): Boolean {
        val translated = offsetX != 0f || offsetY != 0f
        val idle = jobX?.isActive != true && jobY?.isActive != true
        return !scrolledThisTouch && translated && idle
    }

    /** A finger landed: the content stays exactly where it is now. */
    private fun stopReturns() {
        jobX?.cancel()
        jobY?.cancel()
        jobX = null
        jobY = null
    }

    /** Runs the return spring of [axis] from its offset with [velocity] px/s; a throw stops at the limit and returns. */
    private suspend fun returnTo(
        axis: Axis,
        velocity: Float,
    ) {
        val start = if (axis == Axis.X) offsetX else offsetY
        if (start == 0f && velocity == 0f) return
        returning++
        val limit = maxOffset
        val anim = Animatable(start, NbPhysics.OVERSCROLL_EPS_PX).apply { updateBounds(-limit, limit) }
        val write: Animatable<Float, *>.() -> Unit = { if (axis == Axis.X) offsetX = value else offsetY = value }
        try {
            val result = anim.animateTo(0f, NbPhysics.Overscroll.spec(NbPhysics.OVERSCROLL_EPS_PX), velocity, write)
            if (result.endReason ==
                AnimationEndReason.BoundReached
            ) {
                anim.animateTo(0f, NbPhysics.Overscroll.spec(NbPhysics.OVERSCROLL_EPS_PX), 0f, write)
            }
            if (axis == Axis.X) offsetX = 0f else offsetY = 0f
        } finally {
            // Cancelled by a new touch: the content stays where the finger caught it.
            returning--
            if (axis == Axis.X && abs(offsetX) < NbPhysics.OVERSCROLL_EPS_PX) offsetX = 0f
            if (axis == Axis.Y && abs(offsetY) < NbPhysics.OVERSCROLL_EPS_PX) offsetY = 0f
        }
    }

    /**
     * Translation removed by a drag of [delta] while translated by [current]; the drag consumes the same amount with
     * the opposite sign. Moving back, the translation follows the finger 1:1 until it is gone.
     */
    private fun relaxed(
        current: Float,
        delta: Float,
    ): Float {
        if (current == 0f || delta == 0f || sign(current) == sign(delta)) return 0f
        return if (abs(delta) >= abs(current)) current else -delta
    }

    private fun pull(
        current: Float,
        leftover: Float,
    ): Float {
        if (leftover == 0f) return current
        val limit = maxOffset
        val resistance = (1f - abs(current) / limit).coerceIn(0f, 1f)
        return (current + leftover * NbPhysics.OVERSCROLL_FOLLOW * resistance).coerceIn(-limit, limit)
    }

    /**
     * Places the content at the current translation, in a layer so a change only redraws; watches the pointers (never
     * consuming them) so a finger landing stops the return at once and a touch that did not scroll lets it return.
     */
    private inner class OffsetNode :
        Modifier.Node(),
        LayoutModifierNode,
        PointerInputModifierNode {
        override fun onPointerEvent(
            pointerEvent: PointerEvent,
            pass: PointerEventPass,
            bounds: IntSize,
        ) {
            if (pass != PointerEventPass.Initial) return
            val changes = pointerEvent.changes
            if (changes.any { it.changedToDownIgnoreConsumed() }) {
                stopReturns()
                scrolledThisTouch = false
            }
            val lifted = changes.isNotEmpty() && changes.all { !it.pressed }
            // No fling follows a touch that did not scroll: the content returns from where the finger left it. After
            // a touch that scrolled, the fling that follows takes the content over (applyToFling).
            if (lifted && returnsAtLift()) {
                if (offsetX != 0f) startReturn(coroutineScope, Axis.X, 0f)
                if (offsetY != 0f) startReturn(coroutineScope, Axis.Y, 0f)
            }
        }

        override fun onCancelPointerInput() = Unit

        override fun MeasureScope.measure(
            measurable: Measurable,
            constraints: Constraints,
        ): MeasureResult {
            val placeable = measurable.measure(constraints)
            return layout(placeable.width, placeable.height) {
                placeable.placeWithLayer(0, 0) {
                    translationX = offsetX
                    translationY = offsetY
                    if (offsetX != 0f || offsetY != 0f) NbMotionProbe.overscroll?.invoke(offsetX, offsetY)
                }
            }
        }
    }

    private companion object {
        /** Leftover fling velocity (px/s) below which nothing is thrown. */
        const val MIN_VELOCITY = 1f
    }
}

/** Provides [NbOverscrollEffect] to every scrollable through `LocalOverscrollFactory`. */
data class NbOverscrollFactory(
    private val density: Density,
) : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = NbOverscrollEffect(density)
}
