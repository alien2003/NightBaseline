package io.github.alien2003.nightbaseline.ui.interaction

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.OverscrollFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
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
 * - Fling, in any state: the fling is always performed; a non-zero o relaxes in parallel, and the velocity the content
 *   could not use (it hit the edge) seeds the return spring 1:1. Nothing is dropped: velocity in = content + offset.
 *   Compose's `performFling` returns the velocity it **consumed**, so the leftover is `velocity - consumed`; a fling
 *   that comes to rest inside the content consumes all of it and moves no offset (no bump away from an edge).
 * - Return: one spring, [NbPhysics.Overscroll], from (o, v); a finger landing during it stops it where it is (the
 *   scrollable cancels the running fling) and the drag rule applies to that o.
 *
 * Content shorter than its viewport bounces the same way (the whole drag is leftover). Our own implementation of the
 * measured behaviour; no vendor code.
 */
class NbOverscrollEffect(
    private val density: Density,
) : OverscrollEffect {
    private var offsetX by mutableFloatStateOf(0f)
    private var offsetY by mutableFloatStateOf(0f)

    /** Return springs running now (a cancelled one ends after its successor started, so a flag would clear too early). */
    private var returning = 0

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
        if (source != NestedScrollSource.UserInput) return performScroll(delta)
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

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        coroutineScope {
            // A translated content relaxes while the fling runs (it never waits for the fling, nor drops it).
            val relaxX = if (offsetX != 0f) launch { returnTo(Axis.X, 0f) } else null
            val relaxY = if (offsetY != 0f) launch { returnTo(Axis.Y, 0f) } else null
            val left = velocity - performFling(velocity)
            // The velocity the content could not use (it reached the edge) seeds the spring 1:1 from wherever the
            // offset is now: it throws the content past the edge and brings it back.
            if (abs(left.x) > MIN_VELOCITY) {
                relaxX?.cancel()
                launch { returnTo(Axis.X, left.x) }
            }
            if (abs(left.y) > MIN_VELOCITY) {
                relaxY?.cancel()
                launch { returnTo(Axis.Y, left.y) }
            }
        }
    }

    private enum class Axis { X, Y }

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

    /** Places the content at the current translation, in a layer so a change only redraws. */
    private inner class OffsetNode :
        Modifier.Node(),
        LayoutModifierNode {
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
