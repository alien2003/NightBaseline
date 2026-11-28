package io.github.alien2003.nightbaseline.ui.interaction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Property tests of the navigation state machine (ADR 0011 navigation N1 to N8, round 6 path-position model): random
 * sequences over every kind (Page stacks with pushes, pops, double Backs, resets and replaced tops; Tab sections;
 * Pager steps and jumps; Swap peers), every environment (normal, slower and faster duration scales, instant changes),
 * frames at random intervals including stalls and frame times earlier than the input's clock, the drawnAt path, back
 * gestures (start, moves, cancel, commit) and layout changes, on the pure reducer.
 *
 * Asserted after every event: the logical stack equals an independent instant model; the motion rests on the logical
 * top; at most two path layers are visible; no layer's pose changes at any event but a frame (and a frame changes it by
 * at most the spring's own travel over the step bound): there is no third-state jump left (H1); at every catch q is
 * unchanged and v keeps its sign and is reduced only by its caps (M2); a frame advances a spring by at most the step
 * cap, the first frame of a continuation by at most one display frame; and every sequence settles.
 *
 * On 0a363a5 the third-state rule made the running upper layer vanish in one event (the old test asserted that jump):
 * the pose-continuity check of this test fails there for every double Back, double push, fast pager step and reset.
 */
class NavMachineTest {
    private val period = NbClock.DEFAULT_FRAME_NANOS
    private val width = 1080f

    private enum class Host { Page, Tab, Pager, Swap }

    /** The instant model: the logical intents of the generator applied at once, with no motion. */
    private class Instant(
        var stack: List<Any>,
    ) {
        /** A back gesture holds the page and would go back when committed. */
        var live = false
        var liveBack: List<Any>? = null
    }

    private fun firstStep(scale: Float = 1f): Float {
        val r =
            NbNavReducer.reduce(
                NbNavState(listOf("a")),
                NbNavEvent.Go(listOf("a", "b"), NbKind.Page, true, 1, 0L),
                NbNavEnv(durationScale = scale),
            )
        return r.state.motion!!.q
    }

    /** What a state draws, lowest first: every drawn key and its pose. */
    private fun drawn(state: NbNavState): List<Pair<Any, NbLayerPose>> {
        val m = state.motion ?: return listOf(state.top to NbLayerPose.Rest)
        return NbNavReducer.layerKeys(m).map { it to NbNavReducer.poseOf(m, it, width, dark = true) }
    }

    /** One screen column: the opaque layer on top there, its translation and its brightness. */
    private data class Column(
        val key: Any?,
        val tx: Float,
        val light: Float,
    )

    /**
     * The screen of a drawn state as [COLUMNS] columns: per column the top-most layer that covers it (pages are
     * opaque and as wide as the host); fading layers (a swap) blend, so there the per-key alpha is compared instead.
     */
    private fun screen(layers: List<Pair<Any, NbLayerPose>>): List<Column> =
        (0 until COLUMNS).map { c ->
            val x = (c + 0.5f) * width / COLUMNS
            val hit =
                layers.lastOrNull { (_, p) ->
                    val local = x - p.tx
                    p.alpha > 0f && local >= maxOf(0f, p.clipStart) && local < minOf(width, p.clipEnd)
                }
            if (hit == null) Column(null, 0f, 0f) else Column(hit.first, hit.second.tx, hit.second.alpha * (1f - hit.second.dim))
        }

    /**
     * The largest change between two drawn states: (px, brightness). A column whose top layer stays the same moves by
     * its translation change; a column whose top layer changes must lie within the edge travel of either state (an
     * edge crossed it), else the whole width jumped. Fading layers are compared per key by alpha.
     */
    private fun poseJump(
        a: List<Pair<Any, NbLayerPose>>,
        b: List<Pair<Any, NbLayerPose>>,
    ): Pair<Float, Float> {
        val fades = (a + b).any { (_, p) -> p.alpha in 0.001f..0.999f }
        if (fades) {
            val ma = a.toMap()
            val mb = b.toMap()
            var alpha = 0f
            for (k in ma.keys + mb.keys) {
                val x = ma[k] ?: NbLayerPose.Gone
                val y = mb[k] ?: NbLayerPose.Gone
                alpha = maxOf(alpha, abs(x.alpha * (1f - x.dim) - y.alpha * (1f - y.dim)))
            }
            return 0f to alpha
        }
        val sa = screen(a)
        val sb = screen(b)
        // The edges of either state: the covering layers' left and right borders.
        val edges =
            (a + b).filter { (_, p) -> p.alpha > 0f }.flatMap { (_, p) ->
                listOf(p.tx + maxOf(0f, p.clipStart), p.tx + minOf(width, p.clipEnd))
            }
        var tx = 0f
        var light = 0f
        sa.zip(sb).forEachIndexed { c, (x, y) ->
            if (x.key == y.key) {
                tx = maxOf(tx, abs(x.tx - y.tx))
                light = maxOf(light, abs(x.light - y.light))
            } else {
                val at = (c + 0.5f) * width / COLUMNS
                val nearest = edges.minOfOrNull { abs(it - at) } ?: width
                // The column changed layer: as far as the nearest edge that could have crossed it.
                tx = maxOf(tx, nearest)
            }
        }
        return tx to light
    }

    /** One spring frame's largest pose travel for the q step [dq]: the page pose is steepest (2.2 W per index). */
    private fun frameBound(dq: Float): Pair<Float, Float> = (NbPhysics.PAGE_GAMMA * width * dq + 1f) to (2f * dq + 1e-3f)

    @Test
    @Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth", "ComplexCondition")
    fun `random sequences over every kind and environment keep the stack, poses continuous (H1), C1 catches (M2), and settle`() {
        val first = firstStep()
        assertTrue("a run from rest is pre-advanced one frame: $first", first > 0f && first < 0.2f)
        val counts = HashMap<String, Int>()

        fun count(what: String) {
            counts[what] = (counts[what] ?: 0) + 1
        }
        repeat(SEQUENCES) { seed ->
            val rnd = Random(seed)
            val host = Host.entries[seed % Host.entries.size]
            val scale = listOf(1f, 1f, 0.5f, 2f)[(seed / Host.entries.size) % 4]
            val kind =
                when (host) {
                    Host.Page -> NbKind.Page
                    Host.Tab -> NbKind.Tab
                    Host.Pager -> NbKind.Pager
                    Host.Swap -> NbKind.Swap
                }
            var state = NbNavState(listOf<Any>(0))
            val inst = Instant(listOf(0))
            var now = 1_000_000_000L
            var next = 1
            var x = 0f
            repeat(EVENTS) {
                val before = state
                val m0 = before.motion
                val instant = rnd.nextInt(40) == 0
                val env = NbNavEnv(instant = instant, framePeriod = period, durationScale = scale)
                val top = before.top as Int
                val event: NbNavEvent =
                    when (val roll = rnd.nextInt(100)) {
                        in 0..39 -> {
                            when (host) {
                                Host.Page -> {
                                    val s = before.stack
                                    val target =
                                        when (roll % 6) {
                                            0, 1 -> s + next++
                                            2 -> if (s.size > 1) s.dropLast(1) else s + next++
                                            3 -> if (s.size > 2) s.take(1) else s + next++
                                            4 -> listOf<Any>(next++)
                                            else -> s.dropLast(1) + next++
                                        }.ifEmpty { listOf<Any>(next++) }
                                    stackGo(s, target, 1)
                                }

                                Host.Tab -> {
                                    val sec = rnd.nextInt(3)
                                    NbNavEvent.Go(listOf(sec), NbKind.Tab, toIsUpper = sec > top, e = 1, now = now)
                                }

                                Host.Pager -> {
                                    val step = if (roll % 7 == 0) 5 else 1
                                    val fwd = rnd.nextBoolean()
                                    val v0 = if (roll % 5 == 0) rnd.nextFloat() * 6f else 0f
                                    NbNavEvent.Go(listOf(top + if (fwd) step else -step), NbKind.Pager, fwd, 1, now, v0)
                                }

                                Host.Swap -> {
                                    NbNavEvent.Go(listOf(rnd.nextInt(5)), NbKind.Swap, toIsUpper = true, e = 1, now = now)
                                }
                            }
                        }

                        in 40..47 -> {
                            val back = if (host == Host.Page) before.stack.dropLast(1) else listOf<Any>(0)
                            if (kind.stacked && back.isNotEmpty() && back.last() != top) {
                                x = rnd.nextFloat() * 40f
                                NbNavEvent.GestureStart(back, kind, false, 1, x, width, now)
                            } else {
                                NbNavEvent.Frame(now)
                            }
                        }

                        in 48..59 -> {
                            x = (x + (rnd.nextFloat() - 0.4f) * 200f).coerceIn(0f, width)
                            NbNavEvent.GestureMove(x, x / width, now / NANOS_PER_MS, now)
                        }

                        in 60..65 -> {
                            NbNavEvent.GestureEnd(commit = rnd.nextBoolean(), now = now)
                        }

                        66 -> {
                            NbNavEvent.Abort
                        }

                        else -> {
                            // Frames, some drawn already (set up in a composition), some earlier than the input clock.
                            val drawnAt = if (rnd.nextInt(6) == 0) now else Long.MIN_VALUE
                            val early = if (rnd.nextInt(8) == 0) rnd.nextLong(1, 8) * NANOS_PER_MS else 0L
                            NbNavEvent.Frame(now - early, drawnAt)
                        }
                    }
                var result = NbNavReducer.reduce(before, event, env)
                // The host turns a gesture's commit that took no motion into a Back (HostBackTarget).
                var asBack: NbNavEvent.Go? = null
                if (event is NbNavEvent.GestureEnd && event.commit && !inst.live && m0?.drive != NbDrive.Finger && host == Host.Page) {
                    if (before.stack.size > 1) {
                        asBack = NbNavEvent.Go(before.stack.dropLast(1), kind, false, 1, now)
                        result = NbNavReducer.reduce(before, asBack, env)
                    }
                }
                // The instant model.
                when (event) {
                    is NbNavEvent.Go -> {
                        inst.stack = event.stack
                        // A Go to the shown top changes nothing on screen; any other ends a held gesture.
                        if (event.stack.last() != before.top) inst.live = false
                    }

                    is NbNavEvent.GestureStart -> {
                        val held = result.state.motion?.drive == NbDrive.Finger
                        // From rest a gesture always takes the page.
                        if (m0 == null) assertTrue("seed $seed: a gesture from rest holds the page", held)
                        inst.live = held
                        inst.liveBack = event.back
                    }

                    is NbNavEvent.GestureEnd -> {
                        if (inst.live && event.commit) inst.stack = inst.liveBack!!
                        if (asBack != null) inst.stack = asBack.stack
                        inst.live = false
                    }

                    NbNavEvent.Abort -> {
                        inst.live = false
                    }

                    else -> {
                        Unit
                    }
                }
                state = result.state
                val m = state.motion
                val where = "seed $seed ($host x$scale) event $it $event"
                assertTrue(where, state.stack.isNotEmpty() && state.stack.distinct().size == state.stack.size)
                assertEquals("$where: the stack of the instant model", inst.stack, state.stack)
                assertEquals("$where: a finger drives q exactly while a gesture is held", inst.live, m?.drive == NbDrive.Finger)
                if (m != null) {
                    assertTrue("$where: q on the path", m.q >= 0f && m.q <= m.path.lastIndex && m.v.isFinite())
                    assertEquals("$where: the motion rests on the logical top", state.top, m.keyAt(m.restSide))
                    assertTrue("$where: no Idle motion", m.drive != NbDrive.Idle)
                    assertTrue("$where: at most two path layers visible", m.visible.count() <= 2)
                    m.residual?.let { r ->
                        assertTrue(
                            "$where: the residual has its slot",
                            m.slot in m.path.indices && r.targetKey == m.path[m.slot],
                        )
                    }
                }
                // Poses: continuous at every input; a frame moves them by at most the spring's own step.
                val (tx, alpha) = poseJump(drawn(before), drawn(state))
                // The one documented discontinuity: a third change off the path while a residual still runs snaps
                // that older residual to its end (counted below, it must stay rare).
                val droppedResidual =
                    (event is NbNavEvent.Go || asBack != null) && m0?.residual != null && m?.residual != null && m.residual !== m0.residual
                if (droppedResidual) count("dropped")
                val exempt = env.instant || event is NbNavEvent.Abort || event is NbNavEvent.GestureMove || droppedResidual
                if (!exempt) {
                    // The pose follows q (Lipschitz in the travel), and q travels only in frames (by the spring's
                    // own step) or by the first step of a run from rest (R7).
                    val dq = travel(before, state)
                    val (maxTx, maxAlpha) = frameBound(dq)
                    assertTrue("$where: tx jump $tx > $maxTx\n$m0\n$m\n${drawn(before)}\n${drawn(state)}", tx <= maxTx)
                    assertTrue("$where: alpha jump $alpha > $maxAlpha", alpha <= maxAlpha)
                    val fresh = m != null && (m0 == null || (m.residual != null && m.residual !== m0.residual))
                    val allowed =
                        when {
                            event is NbNavEvent.Frame && m0 != null && (m0.drive == NbDrive.Spring || m0.residual != null) -> {
                                // The overdamped Nav spring (zeta 1.2) never runs faster than 1.51 omega |x0| + 1.81 |v0|.
                                val w = NbPhysics.Nav.omega

                                fun speed(r: NbMotion) = 1.6f * w * abs(r.start - r.target) + 1.9f * abs(r.v0) + 1e-3f
                                val step = NbClock.stepCap(period) / 1e9f / scale.coerceAtMost(1f)
                                (if (m0.drive == NbDrive.Spring) speed(m0) * step else 1e-4f) +
                                    (m0.residual?.let { speed(it) * step } ?: 0f)
                            }

                            fresh -> {
                                firstStep(scale) * 1.01f + 1e-4f
                            }

                            else -> {
                                1e-4f
                            }
                        }
                    assertTrue("$where: q travelled $dq > $allowed\n$m0\n$m", dq <= allowed)
                }
                // C1 at every Go that catches the running spring along its path (q unchanged; M2 velocity rule).
                if (event is NbNavEvent.Go && !env.instant && m0 != null && m != null && m0.drive == NbDrive.Spring &&
                    m.residual === m0.residual &&
                    m.kind == m0.kind && m0.kind != NbKind.Swap
                ) {
                    count("catch")
                    val common = m0.path.first { it in m.path }
                    val shift = (m.path.indexOf(common) - m0.path.indexOf(common)).toFloat()
                    assertEquals("$where: q is continuous", m0.q + shift, m.q, 1e-4f)
                    if (m0.v != 0f && m.v != 0f) assertTrue("$where: v keeps its sign ${m0.v} -> ${m.v}", m.v * m0.v > 0f)
                    assertTrue("$where: v is never increased", abs(m.v) <= abs(m0.v) + 1e-4f)
                    val away = m0.v * (m.target - m.q) < 0f
                    if (away) {
                        count("reversal")
                        val room = if (m0.v > 0f) kotlin.math.ceil(m.q) - m.q else m.q - kotlin.math.floor(m.q)
                        val cap = NbNavReducer.awayCap(NbPhysics.Nav, abs(m.target - m.q), room, abs(m0.v))
                        assertEquals("$where: M2 keeps v up to the excursion cap", cap, abs(m.v), 1e-3f)
                    }
                }
                if (m != null && m0 != null && m.residual != null && m0.residual !== m.residual) count("residual")
                if (event is NbNavEvent.Go && m0 != null && m != null && m.path.size > 2) count("path>2")
                // R7: the step bound and the continuation's first frame.
                if (event is NbNavEvent.Frame && m0 != null && m != null && m0.drive == NbDrive.Spring && m.start == m0.start &&
                    m.v0 == m0.v0
                ) {
                    assertTrue("$where: step bound", m.play - m0.play <= NbClock.stepCap(period))
                    if (m0.origin != null && m0.lastFrame == Long.MIN_VALUE) assertTrue("$where: first frame", m.play <= period)
                }
                now += if (rnd.nextInt(20) == 0) STALL_MS * NANOS_PER_MS else rnd.nextLong(1, 20) * NANOS_PER_MS
            }
            // Every sequence settles: the finger lets go and frames run.
            if (state.motion?.drive == NbDrive.Finger) {
                state = NbNavReducer.reduce(state, NbNavEvent.GestureEnd(commit = false, now = now), NbNavEnv(durationScale = scale)).state
            }
            var frames = 0
            while (state.motion != null && frames < SETTLE_FRAMES) {
                now += period
                state = NbNavReducer.reduce(state, NbNavEvent.Frame(now), NbNavEnv(durationScale = scale)).state
                frames++
            }
            assertNull("seed $seed settles within $SETTLE_FRAMES frames", state.motion)
            assertEquals("seed $seed: final stack", inst.stack, state.stack)
        }
        val need = mapOf("catch" to 2000, "reversal" to 200, "residual" to 100, "path>2" to 200)
        need.forEach { (k, n) -> assertTrue("the generator met $k ${counts[k]} times (>= $n)", (counts[k] ?: 0) >= n) }
    }

    /** Runs [s] through frames of [period] until rest; returns the drawn states. */
    private fun settle(
        s0: NbNavState,
        start: Long,
        env: NbNavEnv = NbNavEnv(),
    ): List<NbNavState> {
        var s = s0
        var now = start
        val out = ArrayList<NbNavState>()
        var n = 0
        while (s.motion != null && n < SETTLE_FRAMES) {
            now += period
            s = NbNavReducer.reduce(s, NbNavEvent.Frame(now), env).state
            out += s
            n++
        }
        return out
    }

    /** Every key's offsets from the position (its main path d, its residual d) in [s]. */
    private fun offsets(s: NbNavState): Map<Any, Pair<Float, Float>> {
        val m = s.motion ?: return mapOf(s.top to (0f to 0f))
        val out = HashMap<Any, Pair<Float, Float>>()
        m.path.forEachIndexed { i, k -> out[k] = (i - m.q) to 0f }
        m.residual?.let { r ->
            r.path.forEachIndexed { i, k ->
                if (i in r.visible || k !in m.path || m.path.indexOf(k) == m.slot) out[k] = (m.slot - m.q) to (i - r.q)
            }
        }
        return out
    }

    /** The largest change of a key's offsets between [a] and [b] (index units): the q travel of that step. */
    private fun travel(
        a: NbNavState,
        b: NbNavState,
    ): Float {
        // Only what is drawn counts: keys visible in either state.
        val shown = (drawn(a) + drawn(b)).filter { (_, p) -> p.alpha > 0f }.map { it.first }.toSet()
        val oa = offsets(a).filterKeys { it in shown }
        val ob = offsets(b).filterKeys { it in shown }
        val ra =
            a.motion
                ?.residual
                ?.path
                .orEmpty()
        val rb =
            b.motion
                ?.residual
                ?.path
                .orEmpty()
        return oa.keys.filter { it in ob }.maxOfOrNull { k ->
            val (ga, wa) = oa[k]!!
            val (gb, wb) = ob[k]!!
            when {
                // Into a new residual group: its own offset continues the old one, the group starts at rest.
                k !in ra && k in rb -> abs(ga - wb) + abs(gb)

                // Out of a residual (it settled): the group's offset continues as the key's own.
                k in ra && k !in rb -> abs(ga - gb) + abs(wa)

                else -> abs(ga - gb) + abs(wa - wb)
            }
        } ?: 0f
    }

    /**
     * Continuity in pose space: every step changes the screen by at most the pose functions' Lipschitz bound times the
     * position's travel (2.2 W per index for the stack pair, W for the pager, 2 per index for a fade): what a jump of
     * a layer without a travel of q (round 5's third state) violates at once.
     */
    private fun assertContinuous(
        what: String,
        states: List<NbNavState>,
    ) {
        states.zipWithNext().forEachIndexed { i, (a, b) ->
            val (tx, alpha) = poseJump(drawn(a), drawn(b))
            val dq = travel(a, b)
            assertTrue("$what frame $i: tx jump $tx px for a travel of $dq", tx <= NbPhysics.PAGE_GAMMA * width * dq + 2f)
            assertTrue("$what frame $i: alpha jump $alpha for a travel of $dq", alpha <= 2f * dq + 1e-3f)
        }
    }

    @Test
    fun `H1 - a double Back during a pop retargets the one position, the next page comes in under it, no layer jumps`() {
        var s = NbNavState(listOf<Any>("a", "b", "c"))
        val all = ArrayList<NbNavState>()
        var now = 0L

        fun on(e: NbNavEvent) {
            s = NbNavReducer.reduce(s, e).state
            all += s
        }
        on(NbNavEvent.Go(listOf("a", "b"), NbKind.Page, false, 1, now))
        repeat(5) {
            now += period
            on(NbNavEvent.Frame(now))
        }
        val mid = s.motion!!
        on(NbNavEvent.Go(listOf("a"), NbKind.Page, false, 1, now))
        val m = s.motion!!
        assertEquals("one path over the three pages", listOf<Any>("a", "b", "c"), m.path)
        assertEquals("q unchanged", mid.q + 1f, m.q, 1e-5f)
        assertEquals("v unchanged (towards the new target)", mid.v, m.v, 1e-5f)
        all += settle(s, now)
        assertContinuous("double Back", all)
        assertEquals(listOf<Any>("a"), all.last().stack)
    }

    @Test
    fun `H1 - pager Next three times within 100 ms runs one position over four values, at most two layers, no jump`() {
        var s = NbNavState(listOf<Any>(0))
        val all = ArrayList<NbNavState>()
        var now = 0L
        for (v in 1..3) {
            s = NbNavReducer.reduce(s, NbNavEvent.Go(listOf(v), NbKind.Pager, true, 1, now)).state
            all += s
            repeat(2) {
                now += period
                s = NbNavReducer.reduce(s, NbNavEvent.Frame(now)).state
                all += s
                assertTrue("at most two layers", NbNavReducer.layerKeys(s.motion!!).size <= 2)
            }
        }
        assertEquals(3, s.motion!!.targetKey)
        all += settle(s, now)
        assertContinuous("pager x3", all)
        assertEquals(listOf<Any>(3), all.last().stack)
    }

    @Test
    fun `H1 - a deep link while a page pushes keeps the push as a residual group and slides the new page over it`() {
        var s = NbNavState(listOf<Any>("home"))
        val all = ArrayList<NbNavState>()
        var now = 0L
        s = NbNavReducer.reduce(s, NbNavEvent.Go(listOf("home", "metric"), NbKind.Page, true, 1, now)).state
        all += s
        repeat(4) {
            now += period
            s = NbNavReducer.reduce(s, NbNavEvent.Go(listOf("home", "metric"), NbKind.Page, true, 1, now)).state
            s = NbNavReducer.reduce(s, NbNavEvent.Frame(now)).state
            all += s
        }
        // The link replaces the top (home, night): off the path of the push.
        s = NbNavReducer.reduce(s, stackGo(s.stack, listOf("home", "night"), 1)).state
        all += s
        assertTrue("the push runs on as a residual", s.motion!!.residual != null)
        all += settle(s, now)
        assertContinuous("deep link", all)
        assertEquals(listOf<Any>("home", "night"), all.last().stack)
    }

    @Test
    fun `M2 and REV-1 - a reversal at mid p keeps v exactly below the cap, stays on the pair, and rests without a jump`() {
        for (kind in listOf(NbKind.Page, NbKind.Pager, NbKind.Swap, NbKind.Tab)) {
            var s = NbNavState(listOf<Any>("a"))
            var now = 0L
            s = NbNavReducer.reduce(s, NbNavEvent.Go(listOf("b"), kind, true, 1, now)).state
            while (s.motion!!.q < 0.5f) {
                now += period
                s = NbNavReducer.reduce(s, NbNavEvent.Frame(now)).state
            }
            val m0 = s.motion!!
            assertTrue("$kind moving forward", m0.v > 0f)
            s = NbNavReducer.reduce(s, NbNavEvent.Go(listOf("a"), kind, false, 1, now)).state
            val m = s.motion!!
            assertEquals("$kind: q unchanged", m0.q, m.q, 0f)
            val cap = NbNavReducer.awayCap(kind.spring, m.q, 1f - m.q, m0.v)
            assertEquals("$kind: v' = v up to the excursion cap", minOf(m0.v, cap), m.v, 1e-4f)
            assertTrue("$kind: v kept (not dropped to 0)", m.v > 0f)
            val frames = settle(s, now)
            var peak = m.q
            frames.forEach { f -> f.motion?.let { peak = maxOf(peak, it.q) } }
            assertTrue("$kind: never past the far bound ($peak)", peak <= 1f)
            assertContinuous("$kind reversal", listOf(s) + frames)
            assertEquals(listOf<Any>("a"), frames.last().stack)
        }
    }

    @Test
    fun `a gesture that starts while a cancel returns takes the page over where it is, and its commit pops (R3-1)`() {
        var s = NbNavState(listOf<Any>("history", "night"))
        var now = 0L

        fun on(e: NbNavEvent) {
            s = NbNavReducer.reduce(s, e).state
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
        assertTrue("the cancel is returning", returning.drive == NbDrive.Spring && returning.target == 1f && returning.q < 1f)
        val edge = width * NbNavReducer.upperShare(returning, 1, returning.q)
        // The second gesture: its first event changes nothing on screen.
        on(NbNavEvent.GestureStart(listOf("history"), NbKind.Page, false, 1, 500f, width, now))
        assertEquals(returning.q, s.motion!!.q, 0f)
        // Then the page edge follows the finger exactly, from where it was.
        for (dx in listOf(50f, 120f, 260f, 180f, 300f)) {
            now += period
            on(NbNavEvent.GestureMove(500f + dx, 0f, now / NANOS_PER_MS, now))
            assertEquals("edge follows the finger by $dx", edge + dx, width * NbNavReducer.upperShare(s.motion!!), 1f)
            on(NbNavEvent.Frame(now))
        }
        on(NbNavEvent.GestureEnd(commit = true, now = now))
        assertEquals(listOf<Any>("history"), s.stack)
        var last = s.motion!!.q
        var frames = 0
        while (s.motion != null) {
            now += period
            on(NbNavEvent.Frame(now))
            val q = s.motion?.q ?: 0f
            assertTrue("monotonic to the end", q <= last)
            last = q
            frames++
        }
        assertTrue("the commit moves over several frames ($frames), never in one", frames >= MIN_COMMIT_FRAMES)
    }

    @Test
    fun `a commit the platform sends without a start during a cancel's return continues it, never a jump (R3-1)`() {
        var s = NbNavState(listOf<Any>("history", "night"))
        var now = 0L

        fun on(e: NbNavEvent) {
            s = NbNavReducer.reduce(s, e).state
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
        val q0 = s.motion!!.q
        on(NbNavEvent.Go(listOf("history"), NbKind.Page, toIsUpper = false, e = 1, now = now))
        assertEquals("caught where it is", q0, s.motion!!.q, 0f)
        val all = listOf(s) + settle(s, now)
        assertContinuous("commit without a start", all)
        assertTrue(all.size >= MIN_COMMIT_FRAMES)
    }

    @Test
    fun `measurement 3 and 4 - a long frame at a release holds the pose, the first frame after it advances one frame step`() {
        var s = NbNavState(listOf<Any>("a", "b"))
        var now = 0L
        s = NbNavReducer.reduce(s, NbNavEvent.GestureStart(listOf("a"), NbKind.Page, false, 1, 0f, width, now)).state
        for (i in 1..8) {
            now += period
            s = NbNavReducer.reduce(s, NbNavEvent.GestureMove(i * 50f, 0f, now / NANOS_PER_MS, now)).state
            s = NbNavReducer.reduce(s, NbNavEvent.Frame(now)).state
        }
        val released = NbNavReducer.reduce(s, NbNavEvent.GestureEnd(commit = true, now = now)).state
        val m0 = released.motion!!
        // The release frame takes 180 ms (the commit's work); the next frame arrives then.
        now += 180 * NANOS_PER_MS
        val first = NbNavReducer.reduce(released, NbNavEvent.Frame(now)).state.motion!!
        assertTrue("first frame play ${first.play} <= one frame", first.play <= period)
        val oneStep = abs(m0.v) * period / 1e9f + NbPhysics.Nav.omega * period / 1e9f
        assertTrue("q advanced ${abs(first.q - m0.q)} <= one frame step $oneStep", abs(first.q - m0.q) <= oneStep)
        // And a later long frame advances at most the step cap.
        val after = NbNavReducer.reduce(NbNavState(released.stack, first), NbNavEvent.Frame(now + 300 * NANOS_PER_MS)).state.motion
        if (after != null) assertTrue("step cap", after.play - first.play <= NbClock.stepCap(period))
    }

    @Test
    fun `M3 - the finger maps in screen space - from the right edge the page never moves against the finger`() {
        // A push entering from the right is caught by a swipe from the right edge (moving left).
        var s = NbNavState(listOf<Any>("a"))
        s = NbNavReducer.reduce(s, NbNavEvent.Go(listOf("a", "b"), NbKind.Page, true, 1, 0L)).state
        repeat(6) { s = NbNavReducer.reduce(s, NbNavEvent.Frame((it + 1) * period)).state }
        s = NbNavReducer.reduce(s, NbNavEvent.GestureStart(listOf("a"), NbKind.Page, false, -1, 1000f, width, 0L, dir = -1)).state
        var lastTx = NbNavReducer.poseOf(s.motion!!, "b", width, false).tx
        for (x in listOf(960f, 920f, 880f, 840f)) {
            s = NbNavReducer.reduce(s, NbNavEvent.GestureMove(x, 0f, x.toLong(), 0L)).state
            val tx = NbNavReducer.poseOf(s.motion!!, "b", width, false).tx
            assertTrue("the page moves with the finger (left) or stays: $lastTx -> $tx", tx <= lastTx + 1e-3f)
            assertEquals("by exactly the finger's travel", lastTx - 40f, tx, 1f)
            lastTx = tx
        }
        // A fresh gesture from the right edge on a section host (the page leaves to the right): a leftward finger
        // never pushes it right.
        var t = NbNavState(listOf<Any>("history"))
        t = NbNavReducer.reduce(t, NbNavEvent.GestureStart(listOf("home"), NbKind.Tab, false, 1, 1000f, width, 0L, dir = -1)).state
        t = NbNavReducer.reduce(t, NbNavEvent.GestureMove(800f, 0f, 1, 0L)).state
        assertEquals("no move against the finger", 0f, NbNavReducer.poseOf(t.motion!!, "history", width, false).tx, 1e-3f)
    }

    @Test
    fun `L2 - a commit of a gesture that a key Back resolved while the finger held does not go back again`() {
        var s = NbNavState(listOf<Any>("a", "b", "c"))
        s = NbNavReducer.reduce(s, NbNavEvent.GestureStart(listOf("a", "b"), NbKind.Page, false, 1, 0f, width, 0L, id = 7L)).state
        s = NbNavReducer.reduce(s, NbNavEvent.GestureMove(200f, 0f, 1, 0L)).state
        // A key Back while the finger holds: the machine goes back (b).
        s = NbNavReducer.reduce(s, NbNavEvent.Go(listOf("a", "b"), NbKind.Page, false, 1, 0L)).state
        assertEquals(listOf<Any>("a", "b"), s.stack)
        // The gesture's end arrives for id 7: nothing more happens.
        s = NbNavReducer.reduce(s, NbNavEvent.GestureEnd(commit = true, now = 0L, id = 7L)).state
        assertEquals(listOf<Any>("a", "b"), s.stack)
    }

    @Test
    fun `the gesture map is linear in the finger and has no hysteresis - the same x draws the same edge both ways`() {
        var s =
            NbNavReducer
                .reduce(
                    NbNavState(listOf<Any>("a", "b")),
                    NbNavEvent.GestureStart(listOf("a"), NbKind.Page, false, 1, 20f, width, 0L),
                ).state
        val seen = HashMap<Float, Float>()
        var t = 0L
        for (x in listOf(100f, 300f, 500f, 700f, 500f, 300f, 100f, 300f, 700f)) {
            t += 16
            s = NbNavReducer.reduce(s, NbNavEvent.GestureMove(x, 0f, t, t * NANOS_PER_MS)).state
            val m = s.motion!!
            assertEquals("edge = finger travel at $x", x - 20f, width * NbNavReducer.upperShare(m), 1f)
            seen[x]?.let { assertEquals("the same x draws the same q", it, m.q, 0f) }
            seen[x] = m.q
        }
    }

    @Test
    fun `a Back while the finger holds a stopped page continues from rest velocity (pointer-stopped rule, R0-1)`() {
        var s = NbNavState(listOf<Any>("a", "b"))
        s = NbNavReducer.reduce(s, NbNavEvent.GestureStart(listOf("a"), NbKind.Page, false, 1, 0f, width, 0L)).state
        var now = 0L
        for (i in 1..6) {
            now += 10 * NANOS_PER_MS
            s = NbNavReducer.reduce(s, NbNavEvent.GestureMove(i * 60f, 0f, now / NANOS_PER_MS, now)).state
        }
        assertTrue("the finger was moving", s.motion!!.v < 0f)
        now += 100 * NANOS_PER_MS
        s = NbNavReducer.reduce(s, NbNavEvent.Go(listOf("a"), NbKind.Page, false, 1, now)).state
        assertEquals(0f, s.motion!!.v, 0f)
        assertEquals(NbDrive.Spring, s.motion!!.drive)
    }

    private companion object {
        const val SEQUENCES = 480
        const val EVENTS = 90
        const val SETTLE_FRAMES = 400
        const val STALL_MS = 220L
        const val NANOS_PER_MS = 1_000_000L
        const val MIN_COMMIT_FRAMES = 8

        /** The largest pose change of one 60 Hz Nav frame from any start of these cases (px of a 1080 px page). */
        const val MAX_FRAME_TX = 400f
        const val MAX_FRAME_ALPHA = 0.35f
        const val COLUMNS = 108
    }
}
