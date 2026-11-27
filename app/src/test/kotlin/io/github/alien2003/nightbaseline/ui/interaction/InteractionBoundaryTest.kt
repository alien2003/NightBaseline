package io.github.alien2003.nightbaseline.ui.interaction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The interaction boundary of ADR 0011 (I6): outside the framework package no source of the app uses an animation,
 * navigation, back, gesture, press, key, scroll, fling, overscroll or indication API, so every change of the app
 * moves on the framework's one registry, one clock and one release rule. The framework never imports a screen.
 * The forbidden list and the allowlist are the files `tools/check-interaction.sh` reads, so the two cannot drift.
 */
class InteractionBoundaryTest {
    private val framework = "src/main/kotlin/io/github/alien2003/nightbaseline/ui/interaction/"

    private val patterns: List<Regex> =
        File("../tools/interaction-forbidden.txt")
            .readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map(::Regex)

    private val allowed: Set<String> =
        File("../tools/interaction-allowlist.txt")
            .readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split("|").take(2).joinToString("|") }
            .toSet()

    private fun sources(): List<File> =
        listOf("src/main", "src/debug")
            .map(::File)
            .filter { it.isDirectory }
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }

    /** The code of [line] without a trailing comment; KDoc and comment lines are empty. */
    private fun code(line: String): String {
        val trimmed = line.trimStart()
        if (trimmed.startsWith("*") || trimmed.startsWith("//") || trimmed.startsWith("/*")) return ""
        return line.substringBefore("//")
    }

    @Test
    fun `no animation, navigation, gesture, press, key or scroll API outside ui-interaction`() {
        check(patterns.size > 20) { "run from the app module" }
        val hits =
            sources()
                .filterNot { it.invariantSeparatorsPath.startsWith(framework) }
                .flatMap { file ->
                    val path = "app/" + file.invariantSeparatorsPath
                    file.readLines().flatMapIndexed { i, line ->
                        val c = code(line)
                        patterns
                            .filter { it.containsMatchIn(c) && "$path|${it.pattern}" !in allowed }
                            .map { "$path:${i + 1}: '${it.pattern}': ${line.trim()}" }
                    }
                }
        assertEquals(emptyList<String>(), hits)
    }

    @Test
    fun `the allowlist is empty`() {
        // A ratchet: an exception may be added only with a reason, and the migration left none.
        assertEquals(emptySet<String>(), allowed)
    }

    @Test
    fun `the framework never imports a screen package`() {
        val screen =
            Regex("""import io\.github\.alien2003\.nightbaseline\.(nav|ui\.(home|history|night|metric|settings|onboarding|learning))\.""")
        val hits =
            sources()
                .filter { it.invariantSeparatorsPath.startsWith(framework) }
                .flatMap { f -> f.readLines().filter(screen::containsMatchIn).map { "${f.name}: $it" } }
        assertEquals(emptyList<String>(), hits)
    }

    @Test
    fun `every forbidden pattern catches what it is meant to`() {
        val samples =
            listOf(
                "import androidx.compose.animation.AnimatedContent",
                "AnimatedVisibility(visible)",
                "val a by animateFloatAsState(1f)",
                "Animatable(0f)",
                "spring(stiffness = 300f)",
                "tween(150)",
                "withFrameNanos { }",
                "import androidx.navigation.compose.NavHost",
                "PredictiveBackHandler { }",
                "Modifier.pointerInput(Unit) { }",
                "Modifier.clickable { }",
                "detectTapGestures { }",
                "Modifier.onPreviewKeyEvent { true }",
                "Modifier.verticalScroll(state)",
                "LazyColumn { }",
                "Modifier.selectable(selected) { }",
                "Modifier.nestedScroll(connection)",
                "LocalIndication provides x",
                "import androidx.compose.material3.Button",
            )
        samples.forEach { sample -> assertTrue(patterns.any { it.containsMatchIn(sample) }, "not caught: $sample") }
        // And the framework's own entry points are not.
        listOf("Modifier.nbVerticalScroll(state)", "NbLazyColumn { }", "Modifier.nbClickable(onClick, shape)", "selectableGroup()")
            .forEach { ok -> assertTrue(patterns.none { it.containsMatchIn(ok) }, "false positive: $ok") }
    }
}
