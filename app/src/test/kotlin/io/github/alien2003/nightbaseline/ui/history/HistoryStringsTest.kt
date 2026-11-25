package io.github.alien2003.nightbaseline.ui.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * `strings_history.xml` follows the rules `StringResourcesTest` checks for `strings.xml` (that test reads only
 * `strings.xml`): same keys in English and Russian, positional arguments only, no alarm words, no "Vitals", no "!".
 */
class HistoryStringsTest {
    private val en = load("src/main/res/values/strings_history.xml")
    private val ru = load("src/main/res/values-ru/strings_history.xml")

    @Test
    fun `English and Russian have the same keys`() {
        assertEquals(en.keys.sorted(), ru.keys.sorted())
    }

    @Test
    fun `arguments are positional and match`() {
        val arg = Regex("%(\\d+\\$)?[-#+0,(]*\\d*(\\.\\d+)?[a-zA-Z]")
        val positional = Regex("%\\d+\\\$s")
        val bad =
            (en + ru)
                .values
                .flatten()
                .flatMap { arg.findAll(it).map { m -> m.value } }
                .filterNot(positional::matches)
        assertEquals(emptyList<String>(), bad)
        val mismatch = en.keys.filter { key -> en[key].orEmpty().argsOf(positional) != ru[key].orEmpty().argsOf(positional) }
        assertEquals(emptyList<String>(), mismatch)
    }

    @Test
    fun `no alarm words, no Vitals, no exclamation marks`() {
        val banned = Regex("""(?i)\b(vitals?|alert|warning|abnormal|danger)\b|!""")
        val hits = en.filterValues { texts -> texts.any(banned::containsMatchIn) }.keys + ru.filterValues { it.any { t -> '!' in t } }.keys
        assertEquals(emptySet<String>(), hits)
    }

    private fun List<String>.argsOf(regex: Regex): Set<String> = flatMap { regex.findAll(it).map { m -> m.value } }.toSet()

    private fun load(path: String): Map<String, List<String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val strings = doc.getElementsByTagName("string").let { n -> (0 until n.length).map { n.item(it) as Element } }
        val plurals = doc.getElementsByTagName("plurals").let { n -> (0 until n.length).map { n.item(it) as Element } }
        return strings.associate { it.getAttribute("name") to listOf(it.textContent) } +
            plurals.associate { p ->
                val items = p.getElementsByTagName("item")
                p.getAttribute("name") to (0 until items.length).map { items.item(it).textContent }
            }
    }
}
