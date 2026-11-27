package io.github.alien2003.nightbaseline.ui.interaction

import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker1D
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs

/**
 * Rule A of ADR 0011, the one axis lock of the app: a horizontal gesture claims the pointer only when the finger has
 * moved more than the touch slop **and** more horizontally than vertically at that moment; otherwise the vertical
 * scroll keeps it. Once claimed, horizontal moves are consumed and reported to [onDrag]; [onEnd] gets the total
 * horizontal travel and the release velocity (px/s); a gesture taken by someone else ends with [onCancel].
 */
@Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements") // One state machine: slop, claim, drag, end.
private suspend fun PointerInputScope.horizontalGestures(
    onStart: (PointerInputChange) -> Unit,
    onDrag: (PointerInputChange, Float) -> Unit,
    onEnd: (total: Float, velocity: Float) -> Unit,
    onCancel: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        var dx = 0f
        var dy = 0f
        var claimed = false
        val tracker = VelocityTracker1D(isDataDifferential = false)
        tracker.addDataPoint(down.uptimeMillis, down.position.x)
        var total = 0f
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (claimed) onEnd(total, runCatching { tracker.calculateVelocity() }.getOrDefault(0f))
                break
            }
            if (!claimed) {
                if (change.isConsumed) break
                val delta = change.positionChange()
                dx += delta.x
                dy += delta.y
                if (abs(dx) > slop || abs(dy) > slop) {
                    if (abs(dx) > slop && abs(dx) > abs(dy)) {
                        claimed = true
                        onStart(change)
                        change.consume()
                    } else {
                        break
                    }
                }
            } else {
                if (change.isConsumed) {
                    onCancel()
                    break
                }
                val delta = change.positionChange().x
                total += delta
                tracker.addDataPoint(change.uptimeMillis, change.position.x)
                onDrag(change, delta)
                change.consume()
            }
        }
    }
}

/**
 * A horizontal step swipe (Night previous / next, month): rule A, and on release the decision of the fling physics
 * (ADR 0011 R0): the platform decay projects where the travel would come to rest; past [threshold] towards the start
 * edge steps to the previous value, towards the end edge to the next one. Start-to-end (in the layout direction) is
 * "previous". A null callback means there is no neighbour on that side.
 */
@Composable
fun Modifier.nbStepSwipe(
    threshold: Dp,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
): Modifier {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val previous by rememberUpdatedState(onPrevious)
    val next by rememberUpdatedState(onNext)
    return pointerInput(rtl) {
        val decay = splineBasedDecay<Float>(this)
        val limit = threshold.toPx()
        horizontalGestures(
            onStart = {},
            onDrag = { _, _ -> },
            onEnd = { total, velocity ->
                val projected = decay.calculateTargetValue(total, velocity)
                // Towards the end edge in the layout direction = previous.
                val towardsEnd = if (rtl) -projected else projected
                when {
                    towardsEnd > limit -> previous?.invoke()
                    towardsEnd < -limit -> next?.invoke()
                }
            },
            onCancel = {},
        )
    }
}

/**
 * Scrubbing a chart: a tap selects at its position ([onTap]); a horizontal drag (rule A) selects under the finger
 * on every move ([onScrub], with [onScrubStart] at the claim).
 */
fun Modifier.nbScrub(
    key: Any?,
    onTap: (Offset) -> Unit,
    onScrubStart: () -> Unit,
    onScrub: (Offset) -> Unit,
): Modifier =
    pointerInput(key) { detectTapGestures { onTap(it) } }
        .pointerInput(key) {
            horizontalGestures(
                onStart = { onScrubStart() },
                onDrag = { change, _ -> onScrub(change.position) },
                onEnd = { _, _ -> },
                onCancel = {},
            )
        }
