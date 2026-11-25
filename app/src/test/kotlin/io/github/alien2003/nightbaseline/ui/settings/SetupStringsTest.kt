package io.github.alien2003.nightbaseline.ui.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * `strings_setup.xml` follows the rules `StringResourcesTest` checks for `strings.xml`: the same keys in English and
 * Russian, only positional string arguments and the same ones in both languages, no alarm words, no "Vitals", no
 * exclamation marks, and no key that `strings.xml` already defines.
 */
class SetupStringsTest {
    private val en = load("src/main/res/values/strings_setup.xml")
    private val ru = load("src/main/res/values-ru/strings_setup.xml")

    @Test
    fun `English and Russian have the same keys`() {
        assertEquals(en.keys.sorted(), ru.keys.sorted())
    }

    @Test
    fun `arguments are positional strings and match between languages`() {
        val bad =
            (en + ru)
                .flatMap { (key, text) ->
                    ARG.findAll(text).map { "$key: ${it.value}" }
                }.filterNot { POSITIONAL.matches(it.substringAfter(": ")) }
        assertEquals(emptyList<String>(), bad)
        val mismatch = en.keys.filter { args(en.getValue(it)) != args(ru[it].orEmpty()) }
        assertEquals(emptyList<String>(), mismatch)
    }

    @Test
    fun `no alarm words, no Vitals, no exclamation marks`() {
        val banned = Regex("""(?i)\b(vitals?|alert|warning|abnormal|danger)\b|!""")
        assertEquals(emptyList<String>(), en.filter { banned.containsMatchIn(it.value) }.keys.toList())
        assertEquals(emptyList<String>(), ru.filter { it.value.contains('!') }.keys.toList())
    }

    @Test
    fun `keys are new`() {
        val shared = load("src/main/res/values/strings.xml").keys
        assertEquals(emptyList<String>(), en.keys.filter { it in shared })
    }

    private fun args(text: String): Set<String> = POSITIONAL.findAll(text).map { it.value }.toSet()

    private fun load(path: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val strings = doc.getElementsByTagName("string").let { n -> (0 until n.length).map { n.item(it) as Element } }
        return strings.associate { it.getAttribute("name") to it.textContent }
    }

    private companion object {
        val POSITIONAL = Regex("%\\d+\\\$s")
        val ARG = Regex("%(\\d+\\\$)?[-#+0,(]*\\d*(\\.\\d+)?[a-zA-Z]")
    }
}
