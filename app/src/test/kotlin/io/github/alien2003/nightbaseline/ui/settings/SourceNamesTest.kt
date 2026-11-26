package io.github.alien2003.nightbaseline.ui.settings

import io.github.alien2003.nightbaseline.data.local.settings.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** The shown name of the source app ([SourceNames]). */
class SourceNamesTest {
    @Test
    fun `an installed app shows its label`() {
        assertEquals("Some Health", SourceNames.display("Some Health", "org.example.health"))
    }

    @Test
    fun `the default source without the app shows its name, never the package id`() {
        assertEquals("OHealth", SourceNames.display(null, AppSettings.DEFAULT_SOURCE_PACKAGE))
    }

    @Test
    fun `another missing app shows its package with a break opportunity after every dot`() {
        val shown = SourceNames.display(null, "org.example.health.app")
        assertEquals("org.example.health.app", shown.replace("​", ""))
        assertEquals(3, shown.count { it == '​' })
        assertFalse("a break after each dot, none inside a word", Regex("[a-z]​").containsMatchIn(shown))
    }
}
