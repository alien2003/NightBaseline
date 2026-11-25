package io.github.alien2003.nightbaseline.ui.metric

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * `strings_metric.xml` follows the rules of `StringResourcesTest` (which reads strings.xml only): same keys in English
 * and Russian, positional string arguments, the same argument positions, no alarm words, "Vitals" or "!".
 */
class MetricStringsTest {
    private val en = load("src/main/res/values/strings_metric.xml")
    private val ru = load("src/main/res/values-ru/strings_metric.xml")

    @Test
    fun `English and Russian have the same keys and arguments`() {
        assertEquals(en.keys.sorted(), ru.keys.sorted())
        en.forEach { (key, text) -> assertEquals(args(text), args(ru.getValue(key)), key) }
    }

    @Test
    fun `arguments are positional strings`() {
        val bad = (en + ru).filter { (_, t) -> ARG.findAll(t).any { !POSITIONAL.matches(it.value) } }
        assertEquals(emptyMap<String, String>(), bad)
    }

    @Test
    fun `calm wording`() {
        val banned = Regex("""(?i)\b(vitals?|alert|warning|abnormal|danger)\b|!""")
        assertEquals(emptyMap<String, String>(), en.filterValues { banned.containsMatchIn(it) })
        assertEquals(emptyMap<String, String>(), ru.filterValues { it.contains('!') })
    }

    private fun args(text: String): Set<String> = POSITIONAL.findAll(text).map { it.value }.toSet()

    private fun load(path: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    private companion object {
        val POSITIONAL = Regex("%\\d+\\\$s")
        val ARG = Regex("%(\\d+\\\$)?[-#+0,(]*\\d*(\\.\\d+)?[a-zA-Z]")
    }
}
