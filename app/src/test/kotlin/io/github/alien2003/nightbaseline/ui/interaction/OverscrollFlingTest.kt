package io.github.alien2003.nightbaseline.ui.interaction

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.E
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The overscroll's fling contract (ADR 0011, report d, emulator violation V1): Compose's `performFling` returns the
 * velocity the content **consumed**. Before the fix the effect took that as the leftover, so every fling that came to
 * rest inside the content threw the content ~40 px past its position and back (the "bump" measured on every screen,
 * absent in the sheet), and a fling that reached an edge was thrown with the wrong velocity.
 */
class OverscrollFlingTest {
    private val density = Density(2.625f)

    /** A frame clock that yields before every frame (so concurrent springs interleave) and samples after it. */
    private class SamplingClock(
        private val sample: () -> Unit,
    ) : MonotonicFrameClock {
        private var nanos = 0L

        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            yield()
            nanos += FRAME_NANOS
            val r = onFrame(nanos)
            sample()
            return r
        }
    }

    @Test
    fun `a fling that comes to rest inside the content moves no overscroll offset (no bump)`() {
        val effect = NbOverscrollEffect(density)
        val offsets = mutableListOf<Offset>()
        runBlocking(SamplingClock { offsets += effect.translation }) {
            // The content takes the whole fling (it never reaches an edge): consumed == velocity.
            effect.applyToFling(Velocity(0f, -1500f)) { it }
        }
        assertTrue(offsets.all { it == Offset.Zero }, "offsets drawn: ${offsets.filter { it != Offset.Zero }.take(5)}")
        assertFalse(effect.isInProgress)
    }

    @Test
    fun `a fling that reaches the edge throws the content with exactly the velocity it could not use`() {
        val effect = NbOverscrollEffect(density)
        var peak = 0f
        runBlocking(SamplingClock { peak = maxOf(peak, abs(effect.translation.y)) }) {
            // The content used 40 % before it hit the edge: 900 px/s are left for the offset.
            effect.applyToFling(Velocity(0f, -1500f)) { it * 0.4f }
        }
        // Critically damped from 0 with v0: the largest offset is v0 / (omega e), omega = sqrt(k).
        val expected = 900f / (sqrt(NbPhysics.Overscroll.stiffness) * E.toFloat())
        assertEquals(expected, peak, expected * 0.05f, "peak offset")
        assertEquals(Offset.Zero, effect.translation)
        assertFalse(effect.isInProgress)
    }

    @Test
    fun `the effect is in progress for as long as any return spring runs or the content is translated`() {
        val effect = NbOverscrollEffect(density)
        // Pull past the edge: the content takes nothing, so the whole drag pulls.
        effect.applyToScroll(Offset(0f, 300f), NestedScrollSource.UserInput) { Offset.Zero }
        assertTrue(effect.translation.y > 0f)
        val notInProgress = mutableListOf<Float>()
        var clock: SamplingClock? = null
        clock =
            SamplingClock {
                if (!effect.isInProgress && effect.translation != Offset.Zero) notInProgress += effect.translation.y
            }
        runBlocking(clock) {
            // A fling back towards the edge while the pull relaxes: the relax is cancelled and the throw takes over.
            effect.applyToFling(Velocity(0f, 2000f)) {
                repeat(3) { clock.withFrameNanos { } }
                Velocity.Zero
            }
        }
        assertTrue(notInProgress.isEmpty(), "translated but not in progress: $notInProgress")
        assertEquals(Offset.Zero, effect.translation)
        assertFalse(effect.isInProgress)
    }

    @Test
    fun `a drag during the return spring owns the content - the spring stops and moves nothing under the finger (R1-4)`() {
        val effect = NbOverscrollEffect(density)
        // Pulled past the top edge: the content is translated down.
        effect.applyToScroll(Offset(0f, 300f), NestedScrollSource.UserInput) { Offset.Zero }
        var clock: SamplingClock? = null
        clock = SamplingClock {}
        runBlocking(clock) {
            // The release: the content returns on its spring (no fling velocity).
            val returning = launch { effect.applyToFling(Velocity.Zero) { Velocity.Zero } }
            repeat(3) { clock.withFrameNanos { } }
            val before = effect.translation.y
            assertTrue(before > 0f, "still on its way back: $before")
            // A new finger drags 10 px away from the edge (the content takes what is left after the offset).
            effect.applyToScroll(Offset(0f, -10f), NestedScrollSource.UserInput) { it }
            val afterDrag = effect.translation.y
            assertEquals(before - 10f, afterDrag, 1e-3f, "the drag takes the offset back 1:1")
            repeat(10) { clock.withFrameNanos { } }
            // Before the fix the return spring went on under the finger: the content travelled the offset on top of
            // the finger's own travel (a flick right after a bounce went 10-28 % farther than at rest).
            assertEquals(afterDrag, effect.translation.y, 0f, "nothing but the finger moves the content")
            returning.join()
        }
    }

    private companion object {
        const val FRAME_NANOS = 16_000_000L
    }
}
