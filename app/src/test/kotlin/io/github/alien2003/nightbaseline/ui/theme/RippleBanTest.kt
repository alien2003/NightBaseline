package io.github.alien2003.nightbaseline.ui.theme

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/**
 * ColorOS has no ripple (`tokens.md` 2.8): no Kotlin source of the app may create one. The theme removes the default
 * (`LocalIndication`, `LocalRippleConfiguration`); this keeps call sites from bringing it back. Mirrors
 * `tools/check-ripple.sh`.
 */
class RippleBanTest {
    private val pattern = Regex("""(\bripple\(|rememberRipple\(|RippleConfiguration\(|LocalRippleTheme)""")

    @Test
    fun `no source uses the Material ripple`() {
        val roots = listOf("src/main", "src/debug").map(::File).filter { it.isDirectory }
        check(roots.isNotEmpty()) { "run from the app module" }
        val hits =
            roots.flatMap { root ->
                root
                    .walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .flatMap { file ->
                        file.readLines().mapIndexedNotNull { i, line ->
                            "${file.path}:${i + 1}: ${line.trim()}".takeIf {
                                pattern.containsMatchIn(line) &&
                                    !line.trimStart().startsWith("*")
                            }
                        }
                    }.toList()
            }
        assertEquals(emptyList<String>(), hits)
    }
}
