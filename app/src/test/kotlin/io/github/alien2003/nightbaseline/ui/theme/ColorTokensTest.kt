package io.github.alien2003.nightbaseline.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The Kotlin colour tokens against the colour tables of `docs/design/tokens.md`: same names, same values in both
 * themes (aliases resolved), and the ColorOS canon invariants of `coloros-conformance.md` 4.1 that the tables
 * alone cannot express.
 */
class ColorTokensTest {
    private val themes = listOf("light" to LightNbColors, "dark" to DarkNbColors)

    @Test
    fun `every Kotlin colour token has the value of tokens md`() {
        val docNames = TokensDoc.colors.keys
        assertEquals(docNames, NbColorTokenNames, "token names differ from tokens.md")
        val mismatches =
            themes.flatMap { (theme, colors) ->
                docNames.mapNotNull { name ->
                    val (rgb, alpha) = TokensDoc.resolve(name, dark = theme == "dark")
                    val actual = checkNotNull(colors.byTokenName(name))
                    val expected = Color(0xFF000000.toInt() or rgb).copy(alpha = (alpha / PERCENT).toFloat())
                    "$name ($theme): ${actual.hex()} != ${expected.hex()}".takeIf { !same(actual, expected) }
                }
            }
        assertEquals(emptyList<String>(), mismatches)
    }

    @Test
    fun `neutrals are hue free as in the references`() {
        val neutrals =
            listOf(
                "color.background",
                "color.surface.1",
                "color.surface.2",
                "color.surface.3",
                "color.surface.elevated",
                "color.text.primary",
                "color.text.secondary",
                "color.text.tertiary",
                "color.separator",
                "color.outline",
                "color.chevron",
                "color.control.track",
                "color.control.switchOff",
                "color.status.neutral",
                "color.band.fill",
                "color.onGlass.primary",
                "color.onGlass.secondary",
            )
        val tinted =
            themes.flatMap { (theme, colors) ->
                neutrals.mapNotNull { name ->
                    val c = checkNotNull(colors.byTokenName(name)) { name }
                    val channels = listOf(c.red, c.green, c.blue).map { (it * MAX).roundToInt() }
                    // The light window #F0F1F2 is the one measured near-neutral (1-2 levels of blue).
                    "$name ($theme) ${c.hex()}".takeIf { channels.max() - channels.min() > MAX_TINT }
                }
            }
        assertEquals(emptyList<String>(), tinted)
    }

    @Test
    fun `dark window is true black and cards are the measured grey`() {
        assertEquals(Color.Black, DarkNbColors.background)
        assertEquals(Color(0xFF1A1A1A), DarkNbColors.surface1)
        assertEquals(Color(0xFFF0F1F2), LightNbColors.background)
        assertEquals(Color.White, LightNbColors.surface1)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `retired tokens resolve to their neutral replacements`() {
        themes.forEach { (theme, c) ->
            assertEquals(c.primary, c.accent, "accent ($theme)")
            assertEquals(c.surface3, c.primaryContainer, "primaryContainer ($theme)")
            assertEquals(c.surface1, c.statusMovedContainer, "status container ($theme)")
            assertEquals(Color.Transparent, c.glassRimOuter, "rimOuter ($theme)")
        }
    }

    @Test
    fun `shell glass is translucent and the solid fallbacks are opaque`() {
        themes.forEach { (theme, c) ->
            listOf(c.glassBar, c.glassTopBar, c.glassSheet, c.glassDialog).forEach {
                assertTrue(it.alpha < 1f && it.alpha >= MIN_GLASS_ALPHA, "glass alpha ($theme) ${it.hex()}")
            }
            listOf(c.solidBarTop, c.solidBarBottom, c.solidSheet, c.solidDialog, c.solidTooltip).forEach {
                assertEquals(1f, it.alpha, "solid ($theme)")
            }
        }
    }

    private fun same(
        a: Color,
        b: Color,
    ): Boolean =
        listOf(a.red to b.red, a.green to b.green, a.blue to b.blue, a.alpha to b.alpha)
            .all { (x, y) -> abs((x * MAX).roundToInt() - (y * MAX).roundToInt()) <= 1 }

    private fun Color.hex(): String =
        "#%02X%02X%02X@%d".format(
            (red * MAX).roundToInt(),
            (green * MAX).roundToInt(),
            (blue * MAX).roundToInt(),
            (alpha * PERCENT).roundToInt(),
        )

    private companion object {
        const val PERCENT = 100.0
        const val MAX = 255
        const val MAX_TINT = 2
        const val MIN_GLASS_ALPHA = 0.7f
    }
}
