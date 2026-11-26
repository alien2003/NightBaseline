package io.github.alien2003.nightbaseline.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The control colours of `ControlTokens.kt` (WP3 of `coloros-conformance.md`) against the ColorOS surfaces they sit
 * on (`coloros-conformance.md` 4.1: dark window #000000 and card #1A1A1A, light window #F0F1F2 and card #FFFFFF):
 * text >= 4.5:1, essential graphics (switch on, checked and unchecked radio, tab underline) >= 3:1. The off switch
 * track (2.16:1) is a recorded canon exception: the knob position carries the state (section 1.2).
 */
class ControlContrastTest {
    private data class Surfaces(
        val window: Color,
        val card: Color,
    )

    private val themes =
        listOf(
            Triple("light", LightNbControlColors, Surfaces(Color(0xFFF0F1F2), Color.White)),
            Triple("dark", DarkNbControlColors, Surfaces(Color.Black, Color(0xFF1A1A1A))),
        )

    @Test
    fun `control text and graphics reach their minimum in both themes`() {
        val failures =
            themes.flatMap { (theme, c, s) ->
                val selected = c.selectedRow.compositeOver(s.card)
                // The one segmented control is the period selector (Chart.kt); its track and thumb are data tokens.
                val data = if (theme == "dark") DarkNbDataColors else LightNbDataColors
                listOf(
                    Check("item title on card", c.title, s.card, TEXT),
                    Check("summary on card", c.summary, s.card, TEXT),
                    Check("summary on selected row", c.summary, selected, TEXT),
                    Check("section header on window", c.sectionHeader, s.window, TEXT),
                    Check("footer on window", c.sectionHeader, s.window, TEXT),
                    Check("text button on card", c.accentText, s.card, TEXT),
                    Check("text button on window", c.accentText, s.window, TEXT),
                    Check("button label on fill top", c.onFill, c.fillTop, TEXT),
                    Check("button label on fill bottom", c.onFill, c.fillBottom, TEXT),
                    Check("button label under gloss", c.onFill, c.gloss.compositeOver(c.fillTop), LARGE),
                    Check("secondary button label", c.title, c.secondaryFill.compositeOver(s.card), TEXT),
                    Check("tab selected on window", c.tabSelected, s.window, TEXT),
                    Check("tab unselected on window", c.tabUnselected, s.window, TEXT),
                    Check("chip label", c.summary, c.chip.compositeOver(s.window), TEXT),
                    Check("chip selected label", c.chipSelectedLabel, c.chipSelected.compositeOver(s.window), TEXT),
                    Check("segment label", c.summary, data.segmentTrack.compositeOver(s.card), TEXT),
                    Check("segment label on window", c.summary, data.segmentTrack.compositeOver(s.window), TEXT),
                    Check("segment selected label", c.title, data.segmentThumbBottom, TEXT),
                    Check("switch on track", c.accent, s.card, GRAPHIC),
                    Check("checked radio", c.accent, s.card, GRAPHIC),
                    Check("unchecked radio ring", c.radioRing, s.card, GRAPHIC),
                    Check("selected day number", c.onFill, c.fillTop, TEXT),
                    Check("today number", c.accentText, s.card, TEXT),
                    Check("step dot current", c.accent, s.window, GRAPHIC),
                ).mapNotNull { check ->
                    val ratio = NbContrast.ratio(check.fg, check.bg)
                    "${check.name} ($theme): %.2f < %.1f".format(ratio, check.min).takeIf { ratio < check.min }
                }
            }
        assertEquals(emptyList<String>(), failures)
    }

    private data class Check(
        val name: String,
        val fg: Color,
        val bg: Color,
        val min: Double,
    )

    private companion object {
        const val TEXT = 4.5
        const val LARGE = 3.0
        const val GRAPHIC = 3.0
    }
}
