package io.github.alien2003.nightbaseline.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Contrast of the shell tokens (`ShellTokens.kt`, WP2) in both themes: text >= 4.5:1, graphics >= 3:1, translucent
 * shell tints taken at their worst case over pure white and pure black (`NbContrast.ratio`). Kept apart from
 * `ContrastTest` (WP1) until the sweep folds the shell tokens into `NbColors`.
 */
class ShellContrastTest {
    private class ShellPair(
        val name: String,
        val fg: (ShellColors, NbColors) -> Color,
        val bg: (ShellColors, NbColors) -> Color,
        val min: Double,
    )

    private val pairs =
        listOf(
            ShellPair("nav selected on bar", { s, _ -> s.navSelected }, { s, _ -> s.barTint }, TEXT),
            ShellPair("nav unselected on bar", { s, _ -> s.navUnselected }, { s, _ -> s.barTint }, TEXT),
            ShellPair("nav selected on solid bar", { s, _ -> s.navSelected }, { s, _ -> s.barSolidBottom }, TEXT),
            ShellPair("nav unselected on solid bar", { s, _ -> s.navUnselected }, { s, _ -> s.barSolidBottom }, TEXT),
            ShellPair("round button glyph", { s, _ -> s.convexGlyph }, { s, _ -> s.convexBottom }, GRAPHIC),
            ShellPair("white on accent fill top", { _, _ -> Color.White }, { s, _ -> s.primaryFillTop }, TEXT),
            ShellPair("white on accent fill bottom", { _, _ -> Color.White }, { s, _ -> s.primaryFillBottom }, TEXT),
            ShellPair("dialog title", { _, c -> c.textPrimary }, { s, _ -> s.dialogTint }, TEXT),
            ShellPair("dialog message", { _, c -> c.textSecondary }, { s, _ -> s.dialogTint }, TEXT),
            ShellPair("sheet title", { _, c -> c.onGlassPrimary }, { s, _ -> s.sheetTint }, TEXT),
        )

    @Test
    fun `every shell pair reaches its minimum in both themes`() {
        val themes = listOf(Triple("light", LightShellColors, LightNbColors), Triple("dark", DarkShellColors, DarkNbColors))
        val failures =
            themes.flatMap { (theme, shell, colors) ->
                pairs.mapNotNull { pair ->
                    val ratio = NbContrast.ratio(pair.fg(shell, colors), pair.bg(shell, colors))
                    "${pair.name} ($theme): %.2f < %.1f".format(ratio, pair.min).takeIf { ratio < pair.min }
                }
            }
        assertEquals(emptyList<String>(), failures)
    }

    private companion object {
        const val TEXT = 4.5
        const val GRAPHIC = 3.0
    }
}
