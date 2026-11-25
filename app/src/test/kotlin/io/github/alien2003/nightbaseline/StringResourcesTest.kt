package io.github.alien2003.nightbaseline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The string resources follow `docs/design/states.md` 0: English and Russian have the same keys (strings and
 * plurals), Russian uses only argument positions English defines, plurals have the quantities each language needs,
 * and no text uses the banned alarm words, "Vitals" or an exclamation mark (`principles.md` 1).
 */
class StringResourcesTest {
    private val en = load("src/main/res/values/strings.xml")
    private val ru = load("src/main/res/values-ru/strings.xml")

    @Test
    fun `English and Russian have the same keys`() {
        assertEquals(en.keys.sorted(), ru.keys.sorted())
    }

    @Test
    fun `Russian uses only the argument positions of English`() {
        val problems =
            ru.mapNotNull { (key, texts) ->
                val enArgs = en[key].orEmpty().flatMap(::args).toSet() + EXTRA_ARGS[key].orEmpty()
                val ruArgs = texts.flatMap(::args).toSet()
                "$key uses $ruArgs, English has $enArgs".takeIf { !enArgs.containsAll(ruArgs) }
            }
        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun `every argument is a positional string`() {
        val bad =
            (en + ru)
                .flatMap { (key, texts) -> texts.flatMap { t -> ARG.findAll(t).map { "$key: ${it.value}" }.toList() } }
                .filterNot { POSITIONAL.matches(it.substringAfter(": ")) }
        assertEquals(emptyList<String>(), bad)
    }

    @Test
    fun `plurals have one and other in English and one, few, many, other in Russian`() {
        assertTrue(enPlurals.values.all { it.containsAll(setOf("one", "other")) }, "en: $enPlurals")
        assertTrue(ruPlurals.values.all { it.containsAll(setOf("one", "few", "many", "other")) }, "ru: $ruPlurals")
        assertEquals(enPlurals.keys, ruPlurals.keys)
    }

    @Test
    fun `no alarm words, no Vitals, no exclamation marks`() {
        val banned = Regex("""(?i)\b(vitals?|alert|warning|abnormal|danger)\b|!""")
        val hits = en.flatMap { (key, texts) -> texts.filter { banned.containsMatchIn(it) }.map { "$key: $it" } }
        assertEquals(emptyList<String>(), hits)
        val ruHits = ru.flatMap { (key, texts) -> texts.filter { it.contains('!') }.map { "$key: $it" } }
        assertEquals(emptyList<String>(), ruHits)
    }

    private val enPlurals by lazy { plurals("src/main/res/values/strings.xml") }
    private val ruPlurals by lazy { plurals("src/main/res/values-ru/strings.xml") }

    private fun args(text: String): List<String> = POSITIONAL.findAll(text).map { it.value }.toList()

    private fun load(path: String): Map<String, List<String>> {
        val doc = parse(path)
        val strings = doc.getElementsByTagName("string").let { n -> (0 until n.length).map { n.item(it) as Element } }
        val plurals = doc.getElementsByTagName("plurals").let { n -> (0 until n.length).map { n.item(it) as Element } }
        return strings.associate { it.getAttribute("name") to listOf(it.textContent) } +
            plurals.associate { p ->
                val items = p.getElementsByTagName("item")
                p.getAttribute("name") to (0 until items.length).map { items.item(it).textContent }
            }
    }

    private fun plurals(path: String): Map<String, Set<String>> {
        val doc = parse(path)
        val plurals = doc.getElementsByTagName("plurals").let { n -> (0 until n.length).map { n.item(it) as Element } }
        return plurals.associate { p ->
            val items = p.getElementsByTagName("item")
            p.getAttribute("name") to (0 until items.length).map { (items.item(it) as Element).getAttribute("quantity") }.toSet()
        }
    }

    private fun parse(path: String) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))

    private companion object {
        /** Arguments a key passes although English does not show them: night_label gets the range and the wake date. */
        val EXTRA_ARGS = mapOf("night_label" to setOf("%2${'$'}s"))
        val POSITIONAL = Regex("%\\d+\\\$s")
        val ARG = Regex("%(\\d+\\\$)?[-#+0,(]*\\d*(\\.\\d+)?[a-zA-Z]")
    }
}
