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
    private val en = loadAll(EN_DIR)
    private val ru = loadAll(RU_DIR)

    @Test
    fun `English and Russian have the same keys`() {
        assertEquals(en.keys.sorted(), ru.keys.sorted())
    }

    @Test
    fun `every strings file exists in both languages with the same keys`() {
        assertEquals(files(EN_DIR).map { it.name }, files(RU_DIR).map { it.name })
        files(EN_DIR).forEach { file ->
            val enKeys = load(file.path).keys.sorted()
            val ruKeys = load(File(RU_DIR, file.name).path).keys.sorted()
            assertEquals(enKeys, ruKeys, "keys of ${file.name}")
        }
    }

    @Test
    fun `no key is defined in two files`() {
        val dup =
            files(EN_DIR)
                .flatMap { f -> load(f.path).keys.map { it to f.name } }
                .groupBy({ it.first }, { it.second })
                .filterValues { it.size > 1 }
        assertEquals(emptyMap<String, List<String>>(), dup)
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

    private val enPlurals by lazy { files(EN_DIR).map { plurals(it.path) }.reduce { a, b -> a + b } }
    private val ruPlurals by lazy { files(RU_DIR).map { plurals(it.path) }.reduce { a, b -> a + b } }

    /** Every translatable `strings*.xml` of [dir] (`strings_untranslatable.xml` holds proper nouns only). */
    private fun files(dir: String): List<File> =
        File(dir)
            .listFiles { f -> f.name.startsWith("strings") && f.name.endsWith(".xml") && f.name != UNTRANSLATABLE }
            .orEmpty()
            .sortedBy { it.name }

    private fun loadAll(dir: String): Map<String, List<String>> = files(dir).map { load(it.path) }.reduce { a, b -> a + b }

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
        const val EN_DIR = "src/main/res/values"
        const val RU_DIR = "src/main/res/values-ru"
        const val UNTRANSLATABLE = "strings_untranslatable.xml"

        /** Arguments a key passes although English does not show them: night_label gets the range, wake date and evening. */
        val EXTRA_ARGS = mapOf("night_label" to setOf("%2${'$'}s", "%3${'$'}s"))
        val POSITIONAL = Regex("%\\d+\\\$s")
        val ARG = Regex("%(\\d+\\\$)?[-#+0,(]*\\d*(\\.\\d+)?[a-zA-Z]")
    }
}
