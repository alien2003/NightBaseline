package io.github.alien2003.nightbaseline.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** How the bottom bar shares its width out when the labels wrap: every item keeps at least its longest word. */
class NavShareTest {
    @Test
    fun equalSharesWhenEveryWordFits() {
        assertEquals(listOf(100, 100, 100), share(listOf(60, 80, 90), total = 300, count = 3))
    }

    @Test
    fun aLongWordKeepsItsWidthAndTheOthersShareTheRest() {
        val widths = share(listOf(60, 60, 180), total = 300, count = 3)
        assertEquals(180, widths[2])
        assertEquals(listOf(60, 60), widths.take(2))
        assertEquals(300, widths.sum())
    }

    @Test
    fun neverBelowTheLongestWordEvenWhenTooNarrow() {
        val minimum = listOf(120, 130, 140)
        val widths = share(minimum, total = 300, count = 3)
        widths.zip(minimum).forEach { (w, m) -> assertTrue(w >= m, "$w >= $m") }
    }
}
