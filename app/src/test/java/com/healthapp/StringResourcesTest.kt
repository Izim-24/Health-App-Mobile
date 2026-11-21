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
}
