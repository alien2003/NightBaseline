package io.github.alien2003.nightbaseline.ui.interaction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/**
 * `tokens.md` 9.1 and 9.2 mirror the registry (ADR 0011): every spring with its damping and stiffness, and every
 * constant with its value, in both directions. A change of one without the other fails here.
 */
class MotionTokensDocTest {
    private val doc = File("../docs/design/tokens.md").readText()

    private fun section(title: String): List<List<String>> {
        val start = doc.indexOf(title)
        check(start >= 0) { "no section $title" }
        val end = doc.indexOf("\n### ", start + title.length).takeIf { it > 0 } ?: doc.length
        return doc
            .substring(start, end)
            .lines()
            .filter { it.startsWith("| `") }
            .map { row -> row.trim('|').split("|").map { it.trim().trim('`') } }
    }

    @Test
    fun `the springs of tokens md are the registry`() {
        val documented = section("### 9.1 Springs").associate { it[0] to (it[1].toFloat() to it[2].toFloat()) }
        val registry = NbPhysics.springs.mapValues { (_, s) -> s.damping to s.stiffness }
        assertEquals(registry, documented)
    }

    @Test
    fun `the constants of tokens md are the registry`() {
        val documented =
            section("### 9.2 Constants").associate { row ->
                val v = row[1]
                row[0] to if ("/" in v) v.split("/").let { it[0].toFloat() / it[1].toFloat() } else v.toFloat()
            }
        val registry =
            mapOf(
                "P_EPS" to NbPhysics.P_EPS,
                "OVERSCROLL_EPS_PX" to NbPhysics.OVERSCROLL_EPS_PX,
                "GLOW_IN_MS" to NbPhysics.GLOW_IN_MS.toFloat(),
                "GLOW_OUT_MS" to NbPhysics.GLOW_OUT_MS.toFloat(),
                "PARALLAX" to NbPhysics.PARALLAX,
                "DIM" to NbPhysics.DIM,
                "PAGE_GAMMA" to NbPhysics.PAGE_GAMMA,
                "DIALOG_START_SCALE" to NbPhysics.DIALOG_START_SCALE,
                "DIALOG_OPAQUE_AT" to NbPhysics.DIALOG_OPAQUE_AT,
                "REVEAL_RISE_DP" to NbPhysics.REVEAL_RISE_DP,
                "RADIO_PULSE" to NbPhysics.RADIO_PULSE,
                "SWITCH_STRETCH" to NbPhysics.SWITCH_STRETCH,
                "SWITCH_STRETCH_PEAK" to NbPhysics.SWITCH_STRETCH_PEAK,
                "PRESS_SCALE_SMALL" to NbPhysics.PRESS_SCALE_SMALL,
                "PRESS_SCALE_LARGE" to NbPhysics.PRESS_SCALE_LARGE,
                "OVERSCROLL_FOLLOW" to NbPhysics.OVERSCROLL_FOLLOW,
                "OVERSCROLL_MAX_DP" to NbPhysics.OVERSCROLL_MAX_DP,
                "STEP_SWIPE_DP" to NbPhysics.STEP_SWIPE_DP,
                "POINTER_STOPPED_MS" to NbPhysics.POINTER_STOPPED_MS.toFloat(),
                "ENTER_WAIT_MS" to NbPhysics.ENTER_WAIT_MS.toFloat(),
            )
        assertEquals(registry.keys, documented.keys)
        registry.forEach { (name, value) -> assertEquals(value, documented.getValue(name), 1e-6f, name) }
    }

    @Test
    fun `the kinds of tokens md are the framework's kinds`() {
        val documented = section("### 9.3 Kinds and poses").map { it[0] }.toSet()
        // The kinds table rows start with the kind name, not a code span; read them separately.
        val rows =
            doc
                .substringAfter("### 9.3 Kinds and poses")
                .substringBefore("\n### ")
                .lines()
                .filter { it.startsWith("| ") && !it.startsWith("| kind") && !it.startsWith("|---") }
                .map { it.trim('|').split("|")[0].trim() }
                .toSet()
        assertEquals(NbKind.entries.map { it.name }.toSet(), rows + documented.filter { it.isNotEmpty() })
    }
}
