package io.github.alien2003.nightbaseline.seeder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The line tools/emu/seed.sh matches with `grep -o "SEED_RESULT .* run=<id>$"`. */
class SeedResultTest {
    @Test
    fun `run id ends the line`() {
        assertEquals("SEED_RESULT ok wipe run=1764180000-123-4567", SeedResult.line("ok wipe", "1764180000-123-4567"))
    }

    @Test
    fun `no run id keeps the plain line`() {
        assertEquals("SEED_RESULT error busy", SeedResult.line("error busy", null))
        assertEquals("SEED_RESULT error busy", SeedResult.line("error busy", ""))
    }

    @Test
    fun `a multi-line error stays on the matched line`() {
        assertEquals(
            "SEED_RESULT error IllegalStateException: first second run=7",
            SeedResult.line("error IllegalStateException: first\n  second\r\n", "7"),
        )
    }

    @Test
    fun `run id is reduced to characters safe in a grep pattern`() {
        assertEquals("SEED_RESULT ok wipe run=ab-12", SeedResult.line("ok wipe", "a.b* -1$2"))
    }
}
