package com.healthapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Vietnamese (default) and English must offer the same strings with the same placeholders. */
class StringResourcesTest {
    private fun load(dir: String): Map<String, String> {
        val file = File("src/main/res/$dir/strings.xml")
        assertTrue("missing ${file.path}", file.exists())
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val n = nodes.item(i)
            n.attributes.getNamedItem("name").nodeValue to n.textContent
        }
    }

    private val placeholder = Regex("""%(\d+)\$[sd]""")
    private fun args(s: String) = placeholder.findAll(s).map { it.value }.toSortedSet()

    @Test fun sameKeysInBothLanguages() {
        val vi = load("values")
        val en = load("values-en")
        assertEquals("only in Vietnamese", emptySet<String>(), vi.keys - en.keys)
        assertEquals("only in English", emptySet<String>(), en.keys - vi.keys)
        assertTrue("expected the whole UI in resources", vi.size > 500)
    }

    @Test fun samePlaceholdersInBothLanguages() {
        val vi = load("values")
        val en = load("values-en")
        val mismatched = vi.keys.filter { args(vi.getValue(it)) != args(en[it].orEmpty()) }
        assertEquals("placeholders differ", emptyList<String>(), mismatched)
    }

    private fun plurals(dir: String): Map<String, Set<String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/$dir/strings.xml"))
        val nodes = doc.getElementsByTagName("plurals")
        return (0 until nodes.length).associate { i ->
            val n = nodes.item(i) as org.w3c.dom.Element
            val items = n.getElementsByTagName("item")
            n.getAttribute("name") to (0 until items.length).map { (items.item(it) as org.w3c.dom.Element).getAttribute("quantity") }.toSet()
        }
    }

    @Test fun pluralsMatch_andEnglishHasSingularAndPlural() {
        val vi = plurals("values")
        val en = plurals("values-en")
        assertEquals(vi.keys, en.keys)
        assertTrue("expected count strings as plurals", en.size >= 10)
        en.forEach { (k, q) -> assertEquals("English $k", setOf("one", "other"), q) }
        vi.forEach { (k, q) -> assertTrue("Vietnamese $k needs 'other'", "other" in q) }
    }
}
