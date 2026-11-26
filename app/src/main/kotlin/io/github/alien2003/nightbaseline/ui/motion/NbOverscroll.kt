package io.github.alien2003.nightbaseline.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign

/**
 * ColorOS-like overscroll (`tokens.md` 9.7, `motion.overscroll`): instead of Android's stretch, the content
 * translates without distortion. A pull past the edge moves the content by 24 % (slow pull) to 34 % (fast pull) of
 * the finger travel, with growing resistance; it holds while the finger rests and returns over 700 ms with
 * `easing.overscrollReturn` on release. A fling that hits the edge throws the content past it by the leftover
 * velocity x 0.047 s (at most 107 dp) over 60 ms, then returns the same way. A finger landing during the return stops
 * it (the scrollable cancels the running fling, which owns the animation). Our own implementation of the measured
 * behaviour (reference-analysis 8.2); no vendor code.
 */
class NbOverscrollEffect(
    private val density: Density,
) : OverscrollEffect {
    private var offsetX by mutableFloatStateOf(0f)
    private var offsetY by mutableFloatStateOf(0f)

    override val isInProgress: Boolean get() = offsetX != 0f || offsetY != 0f

    override val node: DelegatableNode = OffsetNode()

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        if (source != NestedScrollSource.UserInput) return performScroll(delta)
        // Runs for every drag event of every scrollable: plain floats only, and the snapshot state is read and
        // written only when the content is (or gets) translated, so an ordinary scroll costs nothing here.
        var x = offsetX
        var y = offsetY
        // 1. A drag back towards the edge first relaxes the current translation (1:1 until it is gone).
        val removedX = relaxed(x, delta.x)
        val removedY = relaxed(y, delta.y)
        x -= removedX
        y -= removedY
        val afterRelax = if (removedX == 0f && removedY == 0f) delta else Offset(delta.x + removedX, delta.y + removedY)
        // 2. The content scrolls normally.
        val consumed = performScroll(afterRelax)
        // 3. What the content could not take pulls it past the edge, against growing resistance.
        x = pull(x, afterRelax.x - consumed.x)
        y = pull(y, afterRelax.y - consumed.y)
        if (x != offsetX) offsetX = x
        if (y != offsetY) offsetY = y
        return delta
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        if (isInProgress) {
            returnToRest()
            return
        }
        val left = performFling(velocity)
        val maxThrow = with(density) { MAX_THROW.toPx() }
        val throwX = (left.x * THROW_SECONDS).coerceIn(-maxThrow, maxThrow)
        val throwY = (left.y * THROW_SECONDS).coerceIn(-maxThrow, maxThrow)
        if (abs(throwX) < MIN_THROW_PX && abs(throwY) < MIN_THROW_PX) return
        val rise = Animatable(0f)
        rise.animateTo(1f, tween(NbDurations.OVERSCROLL_RISE, easing = NbEasing.OverscrollRise)) {
            offsetX = throwX * value
            offsetY = throwY * value
        }
        returnToRest()
    }

    private suspend fun returnToRest() {
        val startX = offsetX
        val startY = offsetY
        try {
            val back = Animatable(1f)
            back.animateTo(0f, tween(NbDurations.OVERSCROLL_RETURN, easing = NbEasing.OverscrollReturn)) {
                // The return curve overshoots 1.05 at its end; never carry the content across the edge.
                val f = value.coerceAtLeast(0f)
                offsetX = startX * f
                offsetY = startY * f
            }
        } finally {
            // Cancelled by a new touch: the content stays where the finger caught it.
            if (offsetX != 0f && abs(offsetX) < SETTLE_PX) offsetX = 0f
            if (offsetY != 0f && abs(offsetY) < SETTLE_PX) offsetY = 0f
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
        val speed = (abs(leftover) / with(density) { FAST_PULL.toPx() }).coerceIn(0f, 1f)
        val follow = FOLLOW_SLOW + (FOLLOW_FAST - FOLLOW_SLOW) * speed
        val limit = with(density) { RESISTANCE_LIMIT.toPx() }
        val resistance = (1f - abs(current) / limit).coerceIn(0f, 1f)
        return current + leftover * follow * resistance
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
                }
            }
        }
    }

    private companion object {
        const val FOLLOW_SLOW = 0.24f
        const val FOLLOW_FAST = 0.34f
        const val THROW_SECONDS = 0.047f
        const val MIN_THROW_PX = 1f
        const val SETTLE_PX = 0.5f
        val MAX_THROW = 107.dp

        /** Pull speed per input event at which the follow ratio reaches [FOLLOW_FAST]. */
        val FAST_PULL = 24.dp

        /** Translation at which the pull stops (rubber-band resistance). */
        val RESISTANCE_LIMIT = 240.dp
    }
}

/** Provides [NbOverscrollEffect] to every scrollable through `LocalOverscrollFactory`. */
data class NbOverscrollFactory(
    private val density: Density,
) : OverscrollFactory {
    override fun createOverscrollEffect(): OverscrollEffect = NbOverscrollEffect(density)
}
