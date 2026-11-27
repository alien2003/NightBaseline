package io.github.alien2003.nightbaseline.ui.interaction

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The maths of the interaction framework (ADR 0011) on the JVM, without a device: one spring object per kind (I1),
 * the release rule R0 with its two lemmas (I2: continuous, monotonic, bounded per frame; I3: never slower than a tap),
 * reversal continuity of the pure poses (R1), the gesture maps, and the registry (I12). The spring is checked twice:
 * against its closed form and frame by frame on a manual 16 ms clock with Compose's own `Animatable`, which is what
 * the hosts run.
 */
class NbPhysicsTest {
    private val nav = NbPhysics.Nav

    /** Closed form of the spring towards 0: displacement d(t) from d0 with velocity v0 (units of travel, per second). */
    private fun d(
        t: Double,
        d0: Double,
        v0: Double,
        spring: NbSpring = nav,
    ): Double {
        val w = spring.omega.toDouble()
        val z = spring.damping.toDouble()
        if (abs(z - 1.0) < 1e-9) return (d0 + (v0 + w * d0) * t) * exp(-w * t)
        val s = sqrt(z * z - 1.0)
        val r1 = -w * (z - s)
        val r2 = -w * (z + s)
        val c2 = (v0 - r1 * d0) / (r2 - r1)
        val c1 = d0 - c2
        return c1 * exp(r1 * t) + c2 * exp(r2 * t)
    }

    /** First time the remaining distance stays below [eps] (closed form, 0.1 ms resolution). */
    private fun settle(
        d0: Double,
        v0: Double,
        eps: Double = NbPhysics.P_EPS.toDouble(),
    ): Double {
        var last = 0.0
        var t = 0.0
        while (t < 3.0) {
            if (abs(d(t, d0, v0)) >= eps) last = t
            t += 1e-4
        }
        return last
    }

    @Test
    fun `I1 - every navigation kind runs on the one Nav spring object`() {
        NbKind.entries.forEach { assertSame(NbPhysics.Nav, it.spring, "$it") }
        // kindOf is total over the relations and gives each relation its own kind.
        val kinds = NbRelation.entries.map(::kindOf)
        assertEquals(NbRelation.entries.size, kinds.toSet().size)
        assertEquals(NbKind.Page, kindOf(NbRelation.Depth))
        assertEquals(NbKind.Tab, kindOf(NbRelation.Section))
        assertEquals(NbKind.Pager, kindOf(NbRelation.Sequence))
        assertEquals(NbKind.Swap, kindOf(NbRelation.Peer))
        assertEquals(NbKind.Sheet, kindOf(NbRelation.SheetOverlay))
        assertEquals(NbKind.Dialog, kindOf(NbRelation.DialogOverlay))
        assertEquals(NbKind.DockedDialog, kindOf(NbRelation.DockedOverlay))
        assertEquals(NbKind.Reveal, kindOf(NbRelation.InPlace))
    }

    @Test
    fun `the fitted Nav spring reproduces the ColorOS recording`() {
        // reference-analysis 8.1, fitted by tools/design/fit-spring.py: start offset 10 ms, the lower page at -W/3 (1-p),
        // the upper at W (1 - (1 - p)^gamma); rms below 3 px on both layers.
        val w = 1140.0
        val t0 = 0.010
        val lower = listOf(0.100 to -124.0, 0.150 to -63.0, 0.215 to -24.0, 0.285 to -8.0, 0.350 to -3.0, 0.470 to 0.0)
        val p: (Double) -> Double = { t -> if (t <= t0) 0.0 else 1.0 + d(t - t0, -1.0, 0.0) }
        val rmsLower = sqrt(lower.sumOf { (t, x) -> (-w / 3 * (1 - p(t)) - x).pow(2) } / lower.size)
        val upper = listOf(0.0167 to 41.0, 0.200 to w - 1)
        val rmsUpper =
            sqrt(upper.sumOf { (t, x) -> (w * (1 - (1 - p(t)).pow(NbPhysics.PAGE_GAMMA.toDouble())) - x).pow(2) } / upper.size)
        println("I0 fit: rms lower %.2f px, upper %.2f px".format(rmsLower, rmsUpper))
        assertTrue(rmsLower < 3.0, "lower rms $rmsLower")
        assertTrue(rmsUpper < 3.0, "upper rms $rmsUpper")
    }

    @Test
    fun `lemma 1 - below the crossing velocity the spring never crosses its target`() {
        for (a in listOf(0.05, 0.2, 0.5, 0.8, 1.0)) {
            val vMax = nav.crossingVelocity(a.toFloat()).toDouble()
            for (k in 0..20) {
                val v0 = vMax * k / 20
                var t = 0.0
                while (t < 2.0) {
                    assertTrue(d(t, -a, v0) <= 1e-9, "a=$a v0=$v0 crosses at $t")
                    t += 1e-3
                }
            }
            // Just above it, it does cross: the bound is tight.
            var crossed = false
            var t = 0.0
            while (t < 2.0 && !crossed) {
                crossed = d(t, -a, vMax * 1.05) > 1e-6
                t += 1e-3
            }
            assertTrue(crossed, "a=$a: the bound is tight")
        }
    }

    @Test
    fun `I3 - a release never settles later than a tap from rest (closed form)`() {
        val tap = settle(-1.0, 0.0)
        var worst = 0.0
        var cases = 0
        for (i in 0..19) {
            val p = i * 0.05
            val a = 1.0 - p
            val vMax = nav.crossingVelocity(a.toFloat()).toDouble()
            for (k in 0..10) {
                val t = settle(-a, vMax * k / 10)
                worst = maxOf(worst, t - tap)
                assertTrue(t <= tap + 1e-4, "p=$p v=${vMax * k / 10}: $t > $tap")
                cases++
            }
        }
        println("I3 closed form: $cases cases, tap settles in %.1f ms, worst release - tap = %.1f ms".format(tap * 1e3, worst * 1e3))
    }

    /** Runs Compose's Animatable on a manual clock of 16 ms frames; returns p per frame. */
    private fun frames(
        start: Float,
        v0: Float,
        bounded: Boolean = true,
    ): List<Float> {
        val clock = ManualClock()
        val out = ArrayList<Float>()
        runBlocking(clock) {
            val anim = Animatable(start, NbPhysics.P_EPS)
            if (bounded) anim.updateBounds(0f, 1f)
            anim.animateTo(1f, nav.spec(), v0) { out += value }
        }
        return out
    }

    @Test
    fun `I3 - frame by frame on Compose's Animatable, a release ends no later than a tap`() {
        val tap = frames(0f, 0f).size
        var cases = 0
        for (i in 0..19) {
            val p = i * 0.05f
            val vMax = nav.crossingVelocity(1f - p)
            for (k in 0..10) {
                val v = NbRelease.startVelocity(p, vMax * k / 10, 1f, nav, visibleTarget = false)
                val n = frames(p, v).size
                assertTrue(n <= tap, "p=$p v=$v: $n frames > tap $tap")
                cases++
            }
            // A flick faster than the crossing velocity towards a target that leaves the screen: no cap, the bound
            // ends the motion at the first crossing, still never after the tap.
            val fast = frames(p, vMax * 3f)
            assertTrue(fast.size <= tap, "p=$p fast: ${fast.size} > $tap")
            assertEquals(1f, fast.last(), 0f)
        }
        println("I3 frames: $cases cases, tap = $tap frames of 16 ms (${tap * 16} ms); the old 0.01-threshold page spring: ${oldPageFrames()} frames")
        assertTrue(tap * 16 <= 600, "the Nav spring settles within 600 ms")
    }

    /** The pre-framework page spring (1.0 / 220) on Compose's 0.01 default threshold over 1080 px, for the report. */
    private fun oldPageFrames(): Int {
        val clock = ManualClock()
        var n = 0
        runBlocking(clock) {
            Animatable(0f).animateTo(1080f, androidx.compose.animation.core.spring(1f, 220f)) { n++ }
        }
        return n
    }

    @Test
    fun `I2 - a gesture release is continuous, monotonic and bounded per frame`() {
        val dt = 0.016
        var cases = 0
        var maxJump = 0.0
        for (i in 1..9) {
            val p = i * 0.1f
            val vMax = nav.crossingVelocity(1f - p)
            for (vr in listOf(0f, 0.5f, 1f, 2f, 5f, 10f, 20f)) {
                val v0 = NbRelease.startVelocity(p, vr, 1f, nav, visibleTarget = false)
                val seq = frames(p, v0)
                // C1 at release: the frame at release is p itself (no jump), the next one is where the closed form from
                // (p, v0) is after one frame.
                assertEquals(p.toDouble(), seq.first().toDouble(), 1e-6, "p=$p vr=$vr release frame")
                val expected = minOf(1.0, 1.0 + d(dt, -(1.0 - p), v0.toDouble()))
                assertEquals(expected, seq[1].toDouble(), 2e-3, "p=$p vr=$vr first frame")
                // Monotonic to the end.
                var prev = p
                for (x in seq) {
                    assertTrue(x >= prev - 1e-6, "p=$p vr=$vr goes back: $prev -> $x")
                    prev = x
                }
                assertEquals(1f, seq.last(), NbPhysics.P_EPS)
                // Per-frame step bounded by the exact supremum of the speed (closed form, sampled).
                if (v0 <= vMax) {
                    var sup = 0.0
                    var t = 0.0
                    while (t < 1.0) {
                        sup = maxOf(sup, (d(t + 1e-5, -(1.0 - p), v0.toDouble()) - d(t, -(1.0 - p), v0.toDouble())) / 1e-5)
                        t += 1e-4
                    }
                    var last = p
                    for (x in seq) {
                        val jump = (x - last).toDouble()
                        maxJump = maxOf(maxJump, jump)
                        assertTrue(jump <= sup * dt + NbPhysics.P_EPS, "p=$p vr=$vr jump $jump > ${sup * dt}")
                        last = x
                    }
                }
                cases++
            }
        }
        println("I2: $cases releases, largest frame step %.3f of the width".format(maxJump))
    }

    @Test
    fun `R0 - a cancel towards a visible rest is capped and never overshoots`() {
        for (i in 1..9) {
            val p = i * 0.1f
            for (vr in listOf(-50f, -20f, -5f, 0f, 5f)) {
                val v0 = NbRelease.startVelocity(p, vr, 0f, nav, visibleTarget = true)
                assertTrue(v0 <= 0f && abs(v0) <= nav.crossingVelocity(p) + 1e-4f, "p=$p vr=$vr v0=$v0")
                val clock = ManualClock()
                runBlocking(clock) {
                    val anim = Animatable(p, NbPhysics.P_EPS)
                    anim.animateTo(0f, nav.spec(), v0) { assertTrue(value >= -1e-4f, "p=$p vr=$vr overshoot $value") }
                }
            }
        }
    }

    @Test
    fun `R1 - a change back to the state being left reverses on the same layers and draws the same frame`() {
        val w = 1000f
        for (kind in listOf(NbKind.Page, NbKind.Tab, NbKind.Pager, NbKind.Swap)) {
            for (i in 0..20) {
                val p = i / 20f
                val t = NbTransition("t", CoroutineScope(Dispatchers.Unconfined))
                t.go("A", "B", kind, toIsUpper = true, e = 1)
                t.holdAt(p)
                val before = listOf(t.poseOf("A", w), t.poseOf("B", w))
                t.go("B", "A", kind, toIsUpper = false, e = 1)
                val after = listOf(t.poseOf("A", w), t.poseOf("B", w))
                before.zip(after).forEach { (x, y) ->
                    assertEquals(x.tx, y.tx, 1e-3f, "$kind at p=$p: tx")
                    assertEquals(x.alpha, y.alpha, 1e-5f, "$kind at p=$p: alpha")
                    assertEquals(x.dim, y.dim, 1e-5f, "$kind at p=$p: dim")
                }
                assertEquals(1f - p, t.drawnP(), 1e-6f)
            }
        }
        // Rest poses: a fully shown upper page and an uncovered lower page draw untransformed.
        val (upper, _) = NbPoses.stack(1f, 1, w, dark = true)
        val (_, lower) = NbPoses.stack(0f, 1, w, dark = true)
        assertTrue(upper.atRest && lower.atRest)
    }

    @Test
    fun `gesture maps - the leaving page stays under the finger from any start`() {
        val w = 1000f
        for (p0 in listOf(0f, 0.2f, 0.5f, 0.8f)) {
            val start = NbPoses.stack(1f - p0, 1, w, false).first.tx
            for (j in 0..20) {
                val g = j / 20f
                val p = NbGestureMap.stackPop(g, p0)
                val tx = NbPoses.stack(1f - p, 1, w, false).first.tx
                assertEquals(start + (w - start) * g, tx, 0.05f, "p0=$p0 g=$g")
            }
        }
        assertEquals(0.5f, NbGestureMap.linear(0.5f, 0f), 0f)
        assertEquals(1f, NbGestureMap.linear(1f, 0.3f), 0f)
    }

    @Test
    fun `I12 - every registry spring is used, and no spring or tween literal exists outside the registry`() {
        val sources =
            File("src/main/kotlin")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .toList()
        check(sources.isNotEmpty()) { "run from the app module" }
        // Physics.kt counts too (the motion config names its springs); the declarations do not use the prefix.
        val text = sources.joinToString("\n") { it.readText() }
        NbPhysics.springs.keys.forEach { name ->
            assertTrue(Regex("""NbPhysics\.$name\b""").containsMatchIn(text) || name == "Nav" && "NbPhysics.Nav" in text, "unused: $name")
        }
        val literal = Regex("""\b(spring|tween)\([^)]*\d|\bNbSpring\(""")
        val hits =
            sources.filterNot { it.name == "Physics.kt" }.flatMap { f ->
                f.readLines().mapIndexedNotNull { i, line ->
                    val code = line.substringBefore("//")
                    "${f.path}:${i + 1}".takeIf { literal.containsMatchIn(code) && !code.trimStart().startsWith("*") }
                }
            }
        assertEquals(emptyList<String>(), hits)
    }

    @Test
    fun `release velocity rule`() {
        // Away from the target: dropped.
        assertEquals(0f, NbRelease.startVelocity(0.5f, -3f, 1f, nav, visibleTarget = false), 0f)
        // Towards a target leaving the screen: kept as is.
        assertEquals(30f, NbRelease.startVelocity(0.5f, 30f, 1f, nav, visibleTarget = false), 0f)
        // Towards a visible rest: capped at the crossing velocity.
        assertEquals(nav.crossingVelocity(0.5f), NbRelease.startVelocity(0.5f, 300f, 1f, nav, visibleTarget = true), 1e-4f)
        // Not finite, or at the target: 0.
        assertEquals(0f, NbRelease.startVelocity(0.5f, Float.NaN, 1f, nav, visibleTarget = false), 0f)
        assertEquals(0f, NbRelease.startVelocity(1f, 10f, 1f, nav, visibleTarget = false), 0f)
    }
}

/** A frame clock that advances 16 ms per frame, for frame-by-frame checks of Compose animations. */
internal class ManualClock : MonotonicFrameClock {
    private var nanos = 0L

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
        nanos += FRAME_NANOS
        return onFrame(nanos)
    }

    private companion object {
        const val FRAME_NANOS = 16_000_000L
    }
}
