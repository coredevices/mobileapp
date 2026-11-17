package coredevices.pebble.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class HighlightJsTest {
    @Test
    fun coloursKeywordsStringsAndComments() {
        val source = "var a = 'if'; // return x"
        val spans = highlightJs(source, dark = false).spanStyles.map { source.substring(it.start, it.end) }
        assertTrue("var" in spans, "$spans")
        assertTrue(spans.any { "'if'" in it }, "$spans")
        assertTrue(spans.any { "// return x" in it }, "$spans")
    }
}
