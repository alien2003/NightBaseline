package io.github.alien2003.nightbaseline.ui.layout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The device matrix of `layouts.md` 11 maps to the modes it names; the two-pane rule of 3.3 holds. */
class LayoutResolverTest {
    private fun mode(
        w: Float,
        h: Float,
        vFold: FoldInfo? = null,
        hFold: FoldInfo? = null,
    ) = resolveLayout(LayoutInputs(w, h, verticalFold = vFold, horizontalFold = hFold)).mode

    @Test
    fun `layouts md 11 device ids resolve to their modes`() {
        assertEquals(LayoutMode.Compact, mode(412f, 915f))
        assertEquals(LayoutMode.Compact, mode(360f, 640f))
        assertEquals(LayoutMode.Compact, mode(482f, 1107f))
        assertEquals(LayoutMode.WideShort, mode(915f, 412f))
        assertEquals(LayoutMode.CompactShort, mode(412f, 430f))
        assertEquals(LayoutMode.Glance, mode(340f, 352f))
        assertEquals(LayoutMode.Glance, mode(256f, 480f))
        assertEquals(LayoutMode.Glance, mode(260f, 260f))
        assertEquals(LayoutMode.Tabletop, mode(412f, 915f, hFold = FoldInfo(457f, separating = true)))
        assertEquals(LayoutMode.Medium, mode(692f, 763f, vFold = FoldInfo(346f, separating = false)))
        assertEquals(LayoutMode.Expanded, mode(951f, 1050f, vFold = FoldInfo(475f, separating = false)))
        assertEquals(LayoutMode.Book, mode(692f, 763f, vFold = FoldInfo(346f, separating = true)))
        assertEquals(LayoutMode.Tabletop, mode(763f, 692f, hFold = FoldInfo(346f, separating = true)))
        assertEquals(LayoutMode.Medium, mode(800f, 1280f))
        assertEquals(LayoutMode.Large, mode(1280f, 800f))
        assertEquals(LayoutMode.Expanded, mode(1024f, 640f))
        assertEquals(LayoutMode.ExtraLarge, mode(1600f, 900f))
    }

    @Test
    fun `two-pane rule follows the worked examples of layouts md 3_3`() {
        assertFalse(resolveLayout(LayoutInputs(692f, 763f)).twoPanes)
        assertTrue(resolveLayout(LayoutInputs(951f, 1050f, fontScale = 1f)).twoPanes)
        assertFalse(resolveLayout(LayoutInputs(951f, 1050f, fontScale = 1.35f)).twoPanes)
        assertTrue(resolveLayout(LayoutInputs(692f, 763f, verticalFold = FoldInfo(346f, separating = true))).twoPanes)
    }

    @Test
    fun `navigation per mode`() {
        assertEquals(NavigationKind.Bar, resolveLayout(LayoutInputs(412f, 915f)).navigation)
        assertEquals(NavigationKind.RailIcons, resolveLayout(LayoutInputs(915f, 412f)).navigation)
        assertEquals(NavigationKind.RailLabelled, resolveLayout(LayoutInputs(1280f, 800f)).navigation)
        assertEquals(NavigationKind.None, resolveLayout(LayoutInputs(340f, 352f)).navigation)
        // Book with a 346 dp start pane keeps the bar inside the start pane (rail would leave < 360 dp).
        assertEquals(NavigationKind.Bar, resolveLayout(LayoutInputs(692f, 763f, verticalFold = FoldInfo(346f, true))).navigation)
    }
}
