package io.github.alien2003.nightbaseline.nav

import io.github.alien2003.nightbaseline.ui.layout.LayoutMode
import io.github.alien2003.nightbaseline.ui.layout.NavigationKind
import io.github.alien2003.nightbaseline.ui.layout.resolveLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Deep-link parsing (untrusted input) and the mapping of WindowManager's folds to the layout inputs. */
class DeepLinksAndWindowTest {
    @Test
    fun `night and fix links are parsed, the extra is a fallback, everything else is ignored`() {
        assertEquals(DeepLink.Night("2026-11-24"), DeepLinks.parse("nightbaseline://night/2026-11-24", null))
        assertEquals(DeepLink.Night("2026-11-20"), DeepLinks.parse(null, "2026-11-20"))
        assertEquals(DeepLink.Fix(FixProblem.Permission), DeepLinks.parse("nightbaseline://fix/permission", null))
        assertEquals(DeepLink.Fix(FixProblem.HealthConnect), DeepLinks.parse("nightbaseline://fix/health-connect", null))
        assertEquals(DeepLink.Fix(FixProblem.SourceSync), DeepLinks.parse("nightbaseline://fix/source-sync", null))
        assertNull(DeepLinks.parse("nightbaseline://night/2026-13-40", null))
        assertNull(DeepLinks.parse("nightbaseline://night/../settings", null))
        assertNull(DeepLinks.parse("https://night/2026-11-24", null))
        assertNull(DeepLinks.parse("nightbaseline://fix/everything", null))
        assertNull(DeepLinks.parse(null, "yesterday"))
        assertNull(DeepLinks.parse("nightbaseline://ni ght/x", null))
        assertNull(DeepLinks.parse(null, null))
    }

    @Test
    fun `a separating vertical fold is book posture with the rail, a horizontal one is tabletop`() {
        val density = 3.25f
        val book =
            layoutInputsOf(
                2249,
                2480,
                density,
                1f,
                listOf(FoldSpec(vertical = true, separating = true, occluding = false, 1124, 0, 1125, 2480)),
            )
        assertEquals(692f, book.widthDp, 0.5f)
        assertEquals(346f, book.verticalFold!!.positionDp, 0.5f)
        val spec = resolveLayout(book)
        assertEquals(LayoutMode.Book, spec.mode)
        assertEquals(NavigationKind.RailIcons, spec.navigation)
        assertTrue(spec.twoPanes)
        val table =
            layoutInputsOf(
                2480,
                2249,
                density,
                1f,
                listOf(FoldSpec(vertical = false, separating = true, occluding = false, 0, 1124, 2480, 1125)),
            )
        assertEquals(LayoutMode.Tabletop, resolveLayout(table).mode)
    }

    @Test
    fun `a flat crease is recorded as non-separating, an occluding hinge keeps its width`() {
        val flat =
            layoutInputsOf(2249, 2480, 3.25f, 1.35f, listOf(FoldSpec(true, separating = false, occluding = false, 1124, 0, 1125, 2480)))
        assertEquals(false, flat.verticalFold!!.separating)
        assertEquals(LayoutMode.Medium, resolveLayout(flat).mode)
        assertEquals(1.35f, flat.fontScale)
        val hinge = layoutInputsOf(2000, 1400, 2f, 1f, listOf(FoldSpec(true, separating = true, occluding = true, 980, 0, 1020, 1400)))
        assertEquals(20f, hinge.verticalFold!!.occludedDp, 0.01f)
        assertEquals(500f, hinge.verticalFold!!.positionDp, 0.01f)
        val none = layoutInputsOf(1080, 2400, 2.625f, 1f, emptyList())
        assertNull(none.verticalFold)
        assertEquals(LayoutMode.Compact, resolveLayout(none).mode)
    }
}
