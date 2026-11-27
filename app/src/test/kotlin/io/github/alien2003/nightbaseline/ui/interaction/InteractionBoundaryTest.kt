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

    /** The ids of [lines]' scroll containers, or "MISSING:<line>" for a call with no literal id within its lookahead. */
    private fun containerIds(lines: List<String>): List<String> {
        val call = Regex("""(nbVerticalScroll|nbHorizontalScroll|NbLazyColumn)\(""")
        val id = Regex("""id = "([^"]+)"""")
        return lines.indices.filter { call.containsMatchIn(code(lines[it])) }.map { i ->
            (i until minOf(i + ID_LOOKAHEAD, lines.size)).firstNotNullOfOrNull { id.find(lines[it])?.groupValues?.get(1) }
                ?: "MISSING:${i + 1}"
        }
    }

    @Test
    fun `registration - every scroll container passes a literal id, unique in the app (review V10)`() {
        // The rule's own check: a call without an id is caught, a call with one gives it.
        assertEquals(listOf("MISSING:1"), containerIds(listOf("Modifier.nbVerticalScroll(state)", "", "", "", "", "", "")))
        assertEquals(listOf("x-1"), containerIds(listOf("NbLazyColumn(", "    state = s,", "    id = \"x-1\",", ")")))
        val ids =
            sources()
                .filterNot { it.invariantSeparatorsPath.startsWith(framework) }
                .flatMap { file -> containerIds(file.readLines()).map { if (it.startsWith("MISSING:")) "${file.path}:$it" else it } }
        assertEquals(emptyList<String>(), ids.filter { "MISSING:" in it }, "containers without a literal id")
        assertEquals(
            emptyList<String>(),
            ids
                .groupBy { it }
                .filter { it.value.size > 1 }
                .keys
                .toList(),
            "ids used twice",
        )
        assertTrue(ids.size > 10, "the app has scroll containers")
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
    fun `a selection changes in one step - no crossfade anywhere, the framework included (R1-8)`() {
        // The bar's selected icon crossfaded over 2-4 frames, so the old item stayed lit after the change had started.
        val crossfade = Regex("\\bCrossfade\\b")
        val hits =
            sources().flatMap { f ->
                f.readLines().mapIndexedNotNull { i, line -> if (crossfade.containsMatchIn(code(line))) "${f.name}:${i + 1}" else null }
            }
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
                // Review 9: the gaps of the first round.
                "import androidx.compose.ui.input.key.Key",
                "awaitPointerEvent(PointerEventPass.Final)",
                "val t = VelocityTracker1D(false)",
                "state.animateScrollToItem(0)",
                "state.scrollTo(0)",
                "Modifier.indication(source, ripple)",
                "BackEventCompat(0f, 0f, 0f, 0)",
                "NbSpring(1f, 300f)",
                // Review round 2: fully qualified calls bypassed the import ban.
                "val t = androidx.compose.animation.core.rememberInfiniteTransition()",
                "androidx.compose.animation.core.animate(0f, 1f) { v, _ -> }",
                "t.animateFloat(0f, 1f, spec)",
                // Review round 3 (V11): pagers, grids, marquees, view animations and raw motion events.
                "HorizontalPager(state) { }",
                "val s = rememberPagerState { 3 }",
                "import androidx.compose.foundation.pager.PagerDefaults",
                "LazyHorizontalGrid(GridCells.Fixed(2)) { }",
                "LazyVerticalStaggeredGrid(columns) { }",
                "Modifier.basicMarquee()",
                "import android.view.animation.DecelerateInterpolator",
                "val i = android.view.animation.LinearInterpolator()",
                "Modifier.pointerInteropFilter { true }",
                "Modifier.motionEventSpy { }",
            )
        samples.forEach { sample -> assertTrue(patterns.any { it.containsMatchIn(sample) }, "not caught: $sample") }
        // And the framework's own entry points are not.
        listOf("Modifier.nbVerticalScroll(state)", "NbLazyColumn { }", "Modifier.nbClickable(onClick, shape)", "selectableGroup()")
            .forEach { ok -> assertTrue(patterns.none { it.containsMatchIn(ok) }, "false positive: $ok") }
    }
}

/** Lines after a scroll container call in which its id is looked for. */
private const val ID_LOOKAHEAD = 6
