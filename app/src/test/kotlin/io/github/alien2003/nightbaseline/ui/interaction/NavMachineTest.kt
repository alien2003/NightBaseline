package io.github.alien2003.nightbaseline.ui.interaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Property tests of the navigation state machine (ADR 0011, navigation N1 to N5): random sequences of taps (pushes),
 * Backs, back gestures (start, moves, cancel, commit) and display frames at random times, on the pure reducer.
 *
 * Asserted after every event: the logical stack is consistent and equals the stack obtained by applying the same
 * logical events instantly; the motion is consistent with it (it rests on the logical top); p and v are continuous
 * at every event that catches a motion (C1: p unchanged, v unchanged or reduced by R0, never reversed); a new motion
 * from rest starts at most one display frame into the spring; no frame advances a spring by more than the stall bound;
 * at most one motion per host; and every sequence settles.
 *
 * Without the machine (round 4: deferred, queued and landed Backs) these properties did not hold: a Back queued during
 * a deferred Back was replayed on a host that could no longer go back (R9-3), a stale queued Back popped a page opened
 * later (R9-4), and a gesture that landed a deferred Back to the last page popped an empty stack (R9-2).
 */
class NavMachineTest {
    private val period = NbClock.DEFAULT_FRAME_NANOS
    private val env = NbNavEnv(framePeriod = period)
    private val width = 1080f

    /** The instant model: the same logical events applied at once, with no motion. */
    private class Instant {
        val stack = mutableListOf<Any>(0)

        /** A back gesture is held and would pop when committed. */
        var live = false
    }

    private fun firstStep(): Float {
        val m = NbMotion("a", "b", NbKind.Page, true, 1, 0f, 0f, NbDrive.Spring, 1f)
        val r = NbNavReducer.reduce(NbNavState(listOf("a")), NbNavEvent.Go(listOf("a", "b"), NbKind.Page, true, 1, 0L), env)
        return r.state.motion?.p ?: m.p
    }

    @Test
    // One property run: generator, models, checks.
    @Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth", "ComplexCondition")
    fun `random event sequences keep the stack consistent, C1 at every catch, one motion, and settle`() {
        val first = firstStep()
        assertTrue("a run from rest is pre-advanced one frame: $first", first > 0f && first < 0.2f)
        var catches = 0
        var thirdStates = 0
        repeat(SEQUENCES) { seed ->
            val rnd = Random(seed)
            var state = NbNavState(listOf<Any>(0))
            val inst = Instant()
            var now = 1_000_000_000L
            var next = 1
            var x = 0f
            repeat(EVENTS) {
                val before = state
                val m0 = before.motion
                val event: NbNavEvent =
                    when (rnd.nextInt(100)) {
                        in 0..14 -> {
                            NbNavEvent.Go(before.stack + next++, NbKind.Page, toIsUpper = true, e = 1, now = now)
                        }

                        in 15..29 -> {
                            if (before.stack.size > 1) {
                                NbNavEvent.Go(before.stack.dropLast(1), NbKind.Page, toIsUpper = false, e = 1, now = now)
                            } else {
                                NbNavEvent.Frame(now)
                            }
                        }

                        in 30..39 -> {
                            if (before.stack.size > 1) {
                                x = rnd.nextFloat() * 40f
                                NbNavEvent.GestureStart(before.stack.dropLast(1), NbKind.Page, false, 1, x, width, now)
                            } else {
                                NbNavEvent.Frame(now)
                            }
                        }

                        in 40..59 -> {
                            x = (x + (rnd.nextFloat() - 0.4f) * 200f).coerceIn(0f, width)
                            NbNavEvent.GestureMove(x, x / width, now / NANOS_PER_MS, now)
                        }

                        in 60..67 -> {
                            NbNavEvent.GestureEnd(commit = rnd.nextBoolean(), now = now)
                        }

                        in 68..69 -> {
                            NbNavEvent.Abort
                        }

                        else -> {
                            NbNavEvent.Frame(now)
                        }
                    }
                // The instant model.
                when (event) {
                    is NbNavEvent.Go -> {
                        if (event.stack.size < inst.stack.size) {
                            inst.stack.removeAt(inst.stack.lastIndex)
                        } else {
                            inst.stack += event.stack.last()
                        }
                        inst.live = false
                    }

                    is NbNavEvent.GestureStart -> {
                        inst.live = true
                    }

                    is NbNavEvent.GestureEnd -> {
                        if (inst.live && event.commit) inst.stack.removeAt(inst.stack.lastIndex)
                        inst.live = false
                    }

                    NbNavEvent.Abort -> {
                        inst.live = false
                    }

                    else -> {
                        Unit
                    }
                }
                val result = NbNavReducer.reduce(before, event, env)
                state = result.state
                val m = state.motion
                val where = "seed $seed event $it $event"
                // The stack.
                assertTrue(where, state.stack.isNotEmpty() && state.stack.distinct().size == state.stack.size)
                assertEquals("$where: the stack of the instant model", inst.stack, state.stack)
                assertEquals("$where: a finger drives p exactly while a gesture is held", inst.live, m?.drive == NbDrive.Finger)
                // The motion.
                if (m != null) {
                    assertTrue(where, m.from != m.to && m.p in 0f..1f && m.v.isFinite())
                    assertEquals("$where: the motion rests on the logical top", state.top, m.keyAt(m.restSide))
                    assertTrue("$where: no Idle motion", m.drive != NbDrive.Idle)
                }
                if (result.ended != null) assertTrue("$where: an ended motion is not the current one", result.ended !== m)
                // C1 at every event that catches the running motion (same pair of states).
                val catchEvent = event !is NbNavEvent.Frame && event !is NbNavEvent.GestureMove
                if (m0 != null && m != null && catchEvent && m.from == m0.from && m.to == m0.to) {
                    catches++
                    assertEquals("$where: p is continuous", m0.p, m.p, 0f)
                    if (m.drive == NbDrive.Spring && event !is NbNavEvent.GestureStart) {
                        val v = NbNavReducer.velocityAt(m0, (event as? NbNavEvent.Go)?.now ?: (event as? NbNavEvent.GestureEnd)?.now ?: now)
                        // R0: the velocity is kept, or reduced (capped towards a visible rest, dropped when away from
                        // the target), never reversed or increased.
                        assertTrue("$where: v $v -> ${m.v}", m.v * v >= 0f && abs(m.v) <= abs(v) + 1e-4f)
                    }
                }
                // A new motion from rest starts one display frame into the spring.
                if (m != null && m0 == null && event is NbNavEvent.Go) assertEquals(where, first, m.p, 1e-6f)
                if (m != null && m0 != null && event is NbNavEvent.Go && (m.from != m0.from || m.to != m0.to)) {
                    thirdStates++
                    assertEquals("$where: R2 ends the running motion", m0, result.ended)
                    assertEquals("$where: and starts from rest", first, m.p, 1e-6f)
                }
                // R7: no frame advances a spring by more than the stall bound.
                if (event is NbNavEvent.Frame && m0 != null && m != null && m0.drive == NbDrive.Spring && m.start == m0.start) {
                    assertTrue("$where: stall bound", m.play - m0.play <= NbClock.STALL_NANOS)
                }
                now += if (rnd.nextInt(20) == 0) STALL_MS * NANOS_PER_MS else rnd.nextLong(1, 20) * NANOS_PER_MS
            }
            // Every sequence settles: the finger lets go and frames run.
            if (state.motion?.drive == NbDrive.Finger) {
                state = NbNavReducer.reduce(state, NbNavEvent.GestureEnd(commit = false, now = now), env).state
                inst.live = false
            }
            var frames = 0
            while (state.motion != null && frames < SETTLE_FRAMES) {
                now += period
                state = NbNavReducer.reduce(state, NbNavEvent.Frame(now), env).state
                frames++
            }
            assertNull("seed $seed settles within $SETTLE_FRAMES frames", state.motion)
            assertEquals("seed $seed: final stack", inst.stack, state.stack)
        }
        assertTrue("the generator caught running motions ($catches) and met third states ($thirdStates)", catches > 500 && thirdStates > 50)
    }

    @Test
    fun `a gesture that starts while a cancel returns takes the page over where it is, and its commit pops (R3-1)`() {
        var s = NbNavState(listOf<Any>("history", "night"))
        var now = 0L

        fun on(e: NbNavEvent) {
            s = NbNavReducer.reduce(s, e, env).state
        }
        on(NbNavEvent.GestureStart(listOf("history"), NbKind.Page, false, 1, 0f, width, now))
        for (i in 1..10) {
            now += period
            on(NbNavEvent.GestureMove(i * 30f, 0f, now / NANOS_PER_MS, now))
            on(NbNavEvent.Frame(now))
        }
        on(NbNavEvent.GestureEnd(commit = false, now = now))
        repeat(4) {
            now += period
            on(NbNavEvent.Frame(now))
        }
        val returning = s.motion!!
        assertTrue("the cancel is returning", returning.drive == NbDrive.Spring && returning.target == 0f && returning.p > 0f)
        val edge = width * NbNavReducer.upperShare(returning, returning.p)
        // The second gesture: its first event changes nothing on screen.
        on(NbNavEvent.GestureStart(listOf("history"), NbKind.Page, false, 1, 500f, width, now))
        assertEquals(returning.p, s.motion!!.p, 0f)
        // Then the page edge follows the finger exactly, from where it was.
        for (dx in listOf(50f, 120f, 260f, 180f, 300f)) {
            now += period
            on(NbNavEvent.GestureMove(500f + dx, 0f, now / NANOS_PER_MS, now))
            val m = s.motion!!
            assertEquals("edge follows the finger by $dx", edge + dx, width * NbNavReducer.upperShare(m, m.p), 1f)
            on(NbNavEvent.Frame(now))
        }
        on(NbNavEvent.GestureEnd(commit = true, now = now))
        assertEquals(listOf<Any>("history"), s.stack)
        var last = s.motion!!.p
        var frames = 0
        while (s.motion != null) {
            now += period
            on(NbNavEvent.Frame(now))
            val p = s.motion?.p ?: 1f
            assertTrue("monotonic to the end", p >= last)
            last = p
            frames++
        }
        assertTrue("the commit moves over several frames ($frames), never in one", frames >= MIN_COMMIT_FRAMES)
    }

    @Test
    fun `a commit the platform sends without a start during a cancel's return continues it, never a jump (R3-1)`() {
        var s = NbNavState(listOf<Any>("history", "night"))
        var now = 0L

        fun on(e: NbNavEvent) {
            s = NbNavReducer.reduce(s, e, env).state
        }
        on(NbNavEvent.GestureStart(listOf("history"), NbKind.Page, false, 1, 0f, width, now))
        for (i in 1..8) {
            now += period
            on(NbNavEvent.GestureMove(i * 40f, 0f, now / NANOS_PER_MS, now))
            on(NbNavEvent.Frame(now))
        }
        on(NbNavEvent.GestureEnd(commit = false, now = now))
        repeat(3) {
            now += period
            on(NbNavEvent.Frame(now))
        }
        val p0 = s.motion!!.p
        // The host's target turns a commit without a gesture into a Back: one Go event.
        on(NbNavEvent.Go(listOf("history"), NbKind.Page, toIsUpper = false, e = 1, now = now))
        assertEquals("caught where it is", p0, s.motion!!.p, 0f)
        var last = p0
        var frames = 0
        while (s.motion != null) {
            now += period
            on(NbNavEvent.Frame(now))
            val p = s.motion?.p ?: 1f
            assertTrue("monotonic from where the cancel was", p >= last - 1e-6f)
            assertTrue("no jump: ${p - last}", p - last <= MAX_FRAME_STEP)
            last = p
            frames++
        }
        assertTrue(frames >= MIN_COMMIT_FRAMES)
    }

    @Test
    fun `the gesture map is linear in the finger and has no hysteresis - the same x draws the same edge both ways`() {
        val start =
            NbNavReducer.reduce(
                NbNavState(listOf<Any>("a", "b")),
                NbNavEvent.GestureStart(listOf("a"), NbKind.Page, false, 1, 20f, width, 0L),
                env,
            )
        var s = start.state
        val seen = HashMap<Float, Float>()
        var t = 0L
        for (x in listOf(100f, 300f, 500f, 700f, 500f, 300f, 100f, 300f, 700f)) {
            t += 16
            s = NbNavReducer.reduce(s, NbNavEvent.GestureMove(x, 0f, t, t * NANOS_PER_MS), env).state
            val m = s.motion!!
            val edge = width * NbNavReducer.upperShare(m, m.p)
            assertEquals("edge = finger travel at $x", x - 20f, edge, 1f)
            seen[x]?.let { assertEquals("the same x draws the same p", it, m.p, 0f) }
            seen[x] = m.p
        }
    }

    @Test
    fun `a Back while the finger holds a stopped page continues from rest velocity (pointer-stopped rule, R0-1)`() {
        var s = NbNavState(listOf<Any>("a", "b"))
        s = NbNavReducer.reduce(s, NbNavEvent.GestureStart(listOf("a"), NbKind.Page, false, 1, 0f, width, 0L), env).state
        var now = 0L
        for (i in 1..6) {
            now += 10 * NANOS_PER_MS
            s = NbNavReducer.reduce(s, NbNavEvent.GestureMove(i * 60f, 0f, now / NANOS_PER_MS, now), env).state
        }
        assertTrue("the finger was moving", s.motion!!.v > 0f)
        // The finger rests 100 ms, then a Back (3-button, a key) arrives: no stale velocity.
        now += 100 * NANOS_PER_MS
        s = NbNavReducer.reduce(s, NbNavEvent.Go(listOf("a"), NbKind.Page, false, 1, now), env).state
        assertEquals(0f, s.motion!!.v, 0f)
        assertEquals(NbDrive.Spring, s.motion!!.drive)
    }

    private companion object {
        const val SEQUENCES = 400
        const val EVENTS = 80
        const val SETTLE_FRAMES = 180
        const val STALL_MS = 220L
        const val NANOS_PER_MS = 1_000_000L
        const val MIN_COMMIT_FRAMES = 8

        /** The largest p step of the Nav spring in one 60 Hz frame from any of these starts. */
        const val MAX_FRAME_STEP = 0.2f
    }
}
