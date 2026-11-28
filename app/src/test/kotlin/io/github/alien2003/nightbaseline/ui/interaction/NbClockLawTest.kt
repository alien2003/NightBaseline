package io.github.alien2003.nightbaseline.ui.interaction

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The clock law of round 3 (ADR 0011 R7) on the hosts' own driver, frame by frame on a manual clock, each case written
 * against the measured defect it fixes and failing without the fix:
 *
 * - From rest, the first frame is at most one display frame into the spring, however long the set-up frame took
 *   (V2-3, V2-4: tap and key changes started at p 0.2 to 0.95; the first sheet open stalled and then jumped).
 * - A stalled frame advances the motion by at most [NbPhysics.FRAME_STALL_MS] (V2-1: a long commit frame was caught up
 *   in one frame, hundreds of px).
 * - Without stalls every frame is the spring at `frame - origin` (pure in time; the round 2 law is kept).
 * - A release starts with the finger's velocity (gesture-release velocity continuity), measured on the hosts' p.
 */
class NbClockLawTest {
    private val nav = NbPhysics.Nav
    private val clock = ManualClock()
    private var saved: () -> Long = System::nanoTime
    private var savedPeriod = NbClock.framePeriodNanos

    @BeforeEach
    fun setUp() {
        saved = NbClock.nowNanos
        savedPeriod = NbClock.framePeriodNanos
        NbClock.nowNanos = { clock.now }
        NbClock.framePeriodNanos = PERIOD
    }

    @AfterEach
    fun tearDown() {
        NbClock.nowNanos = saved
        NbClock.framePeriodNanos = savedPeriod
    }

    /** The Nav spring from [p0] with [v0] towards 1 after [t] s (closed form, overdamped). */
    private fun spring(
        t: Double,
        p0: Double = 0.0,
        v0: Double = 0.0,
    ): Double {
        val w = nav.omega.toDouble()
        val z = nav.damping.toDouble()
        val s = sqrt(z * z - 1.0)
        val r1 = -w * (z - s)
        val r2 = -w * (z + s)
        val d0 = p0 - 1.0
        val c2 = (v0 - r1 * d0) / (r2 - r1)
        val c1 = d0 - c2
        return 1.0 + c1 * exp(r1 * t) + c2 * exp(r2 * t)
    }

    /** Runs the driver from [p0] with [v0] towards 1 from [origin]; returns (frame time, p) of every frame. */
    private fun run(
        p0: Float,
        v0: Float,
        origin: Long?,
    ): List<Pair<Long, Float>> {
        val out = ArrayList<Pair<Long, Float>>()
        val driver = NbDriver(nav) { false }
        runBlocking(clock) {
            driver.snapTo(p0)
            clock.afterFrame = { out += clock.now to driver.p }
            driver.run(1f, v0, origin)
            clock.afterFrame = null
        }
        return out
    }

    @Test
    fun `from rest - a run asked for long before its first frame starts one display frame into the spring`() {
        for (late in longArrayOf(40, 120, 200, 450)) {
            val origin = clock.now - late * MS
            val frames = run(0f, 0f, origin)
            val first = frames.first().second.toDouble()
            // Old law (origin = request): the spring at late + 16 ms, e.g. p 0.98 for 200 ms (V2-4 measured 0.2-0.95).
            // (Compose's spring runs on whole milliseconds: one 60 Hz frame is 16 ms of it.)
            assertEquals(spring(PERIOD_MS / 1e3), first, 2e-3, "late $late ms: the first frame is one display frame in")
            assertTrue(first < 0.1, "late $late ms: the start is visible ($first)")
            // Every later frame: one more frame interval each.
            frames.forEachIndexed { k, (_, p) ->
                val t = PERIOD_MS / 1e3 + k * ManualClock.FRAME_NANOS / 1e9
                if (p < 1f - NbPhysics.P_EPS) assertEquals(spring(t), p.toDouble(), 2e-3, "late $late frame $k")
            }
        }
    }

    @Test
    fun `stall - a long frame advances the motion by at most FRAME_STALL_MS, never by its own length`() {
        val stall = 200L * MS
        clock.gaps.addAll(listOf(ManualClock.FRAME_NANOS, ManualClock.FRAME_NANOS, stall))
        val origin = clock.now
        val frames = run(0f, 0f, origin)
        var play = 0L
        var last = origin
        var maxStep = 0.0
        var previous = 0.0
        frames.forEachIndexed { k, (frame, p) ->
            play += if (k == 0) minOf(frame - last, PERIOD) else minOf(frame - last, NbPhysics.FRAME_STALL_MS * MS)
            last = frame
            val expected = spring(play / 1e9)
            if (p < 1f - NbPhysics.P_EPS) assertEquals(expected, p.toDouble(), 2e-3, "frame $k at ${play / MS} ms of play")
            maxStep = maxOf(maxStep, p - previous)
            previous = p.toDouble()
        }
        // The largest step of the whole run is the spring's own over FRAME_STALL_MS at its fastest, never the 200 ms.
        val bound = (0 until 1000).maxOf { i -> spring((i + NbPhysics.FRAME_STALL_MS) / 1e3) - spring(i / 1e3) }
        assertTrue(maxStep <= bound + 1e-3, "largest step $maxStep <= $bound")
        // Old law: the stalled frame showed the spring at 232 ms (p ~ 0.99) straight after 32 ms (p ~ 0.25).
        assertTrue(frames[2].second < spring(0.232) - 0.2, "the stalled frame is not caught up (${frames[2].second})")
    }

    @Test
    @Suppress("NestedBlockDepth") // Cases x leads x frames.
    fun `no stall - every frame is the spring at frame minus origin, for a start from rest and a continuation`() {
        for ((p0, v0) in listOf(0f to 0f, 0.3f to 4f, 0.6f to 1.5f)) {
            for (lead in longArrayOf(1, 8, 15)) {
                val origin = clock.now + ManualClock.FRAME_NANOS - lead * MS
                val frames = run(p0, v0, origin)
                frames.forEach { (frame, p) ->
                    val t = (frame - origin) / 1e9
                    if (p < 1f - NbPhysics.P_EPS) {
                        assertEquals(spring(t, p0.toDouble(), v0.toDouble()), p.toDouble(), 2e-3, "p0 $p0 v0 $v0 lead $lead")
                    }
                }
            }
        }
    }

    @Test
    @Suppress("NestedBlockDepth") // Speeds x ends x samples.
    fun `release - the page starts with the finger's velocity, measured on p (gesture-release velocity continuity)`() {
        // The finger crosses the host at a constant speed (share per second); p is the stack pop map of its travel.
        val w = 1080f
        for (speed in floatArrayOf(1f, 2.5f, 5f)) {
            for (end in floatArrayOf(0.3f, 0.5f, 0.7f)) {
                // Through the hosts' own gesture path: the navigation reducer's back gesture fed with the finger.
                var state =
                    NbNavReducer
                        .reduce(
                            NbNavState(listOf("below", "top")),
                            NbNavEvent.GestureStart(listOf("below"), NbKind.Page, toIsUpper = false, e = 1, x = 0f, width = w, now = 0L),
                        ).state
                val steps = 8
                val dt = ManualClock.FRAME_NANOS / MS
                var t = 0L
                for (k in steps downTo 0) {
                    val u = end - speed * k * dt / 1e3f
                    if (u < 0f) continue
                    state = NbNavReducer.reduce(state, NbNavEvent.GestureMove(u * w, u, t, t * MS)).state
                    t += dt
                }
                val v = NbNavReducer.velocityAt(state.motion!!, (t - dt) * MS)
                // dp/dt = dp/du * du/dt at the release, from the map's derivative.
                val h = 1e-3f
                val dpdu = (NbGestureMap.stackPop(end + h, 0f) - NbGestureMap.stackPop(end - h, 0f)) / (2 * h)
                val truth = dpdu * speed
                assertTrue(abs(v - truth) <= 0.01f * truth, "speed $speed end $end: v $v vs finger $truth")
                // And the edge of the leaving page moves at the finger's speed in px/s.
                val s = 1f - NbGestureMap.stackPop(end, 0f)
                val dEdge = w * NbPhysics.PAGE_GAMMA * s.pow(NbPhysics.PAGE_GAMMA - 1f) * v
                assertEquals(speed * w, dEdge, 0.01f * speed * w, "speed $speed end $end: the edge at the finger's speed")
            }
        }
    }

    private companion object {
        const val MS = 1_000_000L

        /** A 60 Hz display. */
        const val PERIOD = 16_666_667L

        /** [PERIOD] in whole ms, as Compose's spring sees it. */
        const val PERIOD_MS = 16L
    }
}
