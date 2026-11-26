package io.github.alien2003.nightbaseline.nav

import io.github.alien2003.nightbaseline.ui.shell.BackPreview
import org.junit.Assert.assertEquals
import org.junit.Test

/** A gesture that resumes a cancel picks the page up where it is and still ends at the full preview. */
class BackResumeTest {
    @Test
    fun `resumed progress starts at the page and ends at 1`() {
        assertEquals(0.4f, BackPreview.resumed(0.4f, 0f), EPS)
        assertEquals(0.7f, BackPreview.resumed(0.4f, 0.5f), EPS)
        assertEquals(1f, BackPreview.resumed(0.4f, 1f), EPS)
        assertEquals("a fresh gesture follows the finger", 0.3f, BackPreview.resumed(0f, 0.3f), EPS)
    }

    private companion object {
        const val EPS = 1e-5f
    }
}
