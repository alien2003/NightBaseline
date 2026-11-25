package io.github.alien2003.nightbaseline.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Kotlin theme against `docs/design/tokens.md`, mirroring `tools/design/contrast_check.py` so it runs in
 * `./gradlew test`: every colour token has the Markdown value, every pair of the pairs table is in the Kotlin list,
 * and every pair reaches its ratio in both themes (text >= 4.5, large / graphic / focus >= 3.0; glass backgrounds
 * composited over pure white and pure black, the worse counts).
 */
class ContrastTest {
    private val themes = listOf("light" to LightNbColors, "dark" to DarkNbColors)

    @Test
    fun `every pair reaches its minimum in both themes`() {
        val failures =
            themes.flatMap { (theme, colors) ->
                NbContrastPairs.mapNotNull { pair ->
                    val fg = checkNotNull(colors.byTokenName(pair.fg)) { "unknown ${pair.fg}" }
                    val bg = checkNotNull(colors.byTokenName(pair.bg)) { "unknown ${pair.bg}" }
                    assertTrue(fg.alpha >= 0.999f, "${pair.name}: foreground ${pair.fg} must be opaque")
                    val ratio = NbContrast.ratio(fg, bg)
                    "${pair.name} ($theme): %.2f < %.1f".format(ratio, pair.min).takeIf { ratio < pair.min }
                }
            }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `no pair minimum is below the rule of its kind`() {
        NbContrastPairs.forEach { pair -> assertTrue(pair.min >= pair.kind.floor, "${pair.name}: ${pair.min} < ${pair.kind.floor}") }
    }

    @Test
    fun `the Kotlin pairs are exactly the pairs table of tokens md`() {
        assertEquals(TokensDoc.pairs, NbContrastPairs)
    }

    @Test
    fun `every text or on-colour token is the foreground of a pair`() {
        val used = NbContrastPairs.map { it.fg }.toSet()
        val textTokens =
            NbColorTokenNames.filter { name ->
                val segments = name.split('.')
                "text" in segments.take(2) || segments.any { it.matches(Regex("on[A-Z]\\w*")) }
            }
        assertEquals(emptyList<String>(), textTokens.filterNot { it in used })
    }

    @Test
    fun `every Kotlin colour token has the value of tokens md`() {
        val docNames = TokensDoc.colors.keys
        assertEquals(docNames, NbColorTokenNames, "token names differ from tokens.md")
        val mismatches =
            themes.flatMap { (theme, colors) ->
                docNames.mapNotNull { name ->
                    val (rgb, alpha) = TokensDoc.resolve(name, dark = theme == "dark")
                    val actual = checkNotNull(colors.byTokenName(name))
                    val expected = Color(0xFF000000.toInt() or rgb).copy(alpha = (alpha / 100).toFloat())
                    "$name ($theme): ${actual.hex()} != ${expected.hex()}".takeIf { !same(actual, expected) }
                }
            }
        assertEquals(emptyList<String>(), mismatches)
    }

    @Test
    fun `contrast math matches WCAG reference values`() {
        assertEquals(21.0, NbContrast.ratio(Color.Black, Color.White), 0.01)
        assertEquals(1.0, NbContrast.ratio(Color.White, Color.White), 0.001)
        // #767676 on white is the classic 4.54:1.
        assertEquals(4.54, NbContrast.ratio(Color(0xFF767676), Color.White), 0.01)
    }

    private fun same(
        a: Color,
        b: Color,
    ): Boolean =
        listOf(a.red to b.red, a.green to b.green, a.blue to b.blue, a.alpha to b.alpha)
            .all { (x, y) -> abs((x * 255).roundToInt() - (y * 255).roundToInt()) <= 1 }

    private companion object {
        const val PERCENT = 100
    }

    private fun Color.hex(): String =
        "#%02X%02X%02X@%d".format(
            (red * 255).roundToInt(),
            (green * 255).roundToInt(),
            (blue * 255).roundToInt(),
            (alpha * PERCENT).roundToInt(),
        )
}
