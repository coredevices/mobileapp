package coredevices.mcp.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolSearchTest {

    @Test
    fun tokenizesSnakeCamelAndPunctuation() {
        assertEquals(listOf("kidsnet", "status"), ToolSearch.tokenize("kidsnet_status"))
        assertEquals(listOf("switch", "devices"), ToolSearch.tokenize("switchDevices"))
        assertEquals(listOf("get", "list", "items", "v2"), ToolSearch.tokenize("Get list-items (v2)."))
    }

    @Test
    fun exactBeatsPrefixBeatsPartialBeatsFuzzy() {
        val exact = ToolSearch.score(listOf("status"), "kidsnet_status")
        val prefix = ToolSearch.score(listOf("stat"), "kidsnet_status")
        val partial = ToolSearch.score(listOf("tatu"), "kidsnet_status")
        val fuzzy = ToolSearch.score(listOf("statis"), "kidsnet_status")
        assertTrue(exact > prefix && prefix > partial && partial > fuzzy && fuzzy > 0)
    }

    @Test
    fun shortTokensOnlyMatchExactly() {
        assertEquals(0, ToolSearch.score(listOf("st"), "kidsnet_status"))
        assertEquals(0, ToolSearch.score(listOf("statu"), "set"))
        assertTrue(ToolSearch.score(listOf("tv"), "Turn the tv on") > 0)
    }

    @Test
    fun typosAreToleratedOnLongerWordsOnly() {
        assertTrue(ToolSearch.score(listOf("kidnet"), "kidsnet_status") > 0)
        assertTrue(ToolSearch.score(listOf("annoucnement"), "Play an announcement") > 0)
        assertEquals(0, ToolSearch.score(listOf("note"), "Play an announcement"))
        assertEquals(0, ToolSearch.score(listOf("list"), "lost"))
    }

    @Test
    fun multiWordKeywordsAndMultipleTextsAccumulate() {
        val both = ToolSearch.score(listOf("kids net status"), "kidsnet_status", "Current KidsNet mode")
        val nameOnly = ToolSearch.score(listOf("kids net status"), "kidsnet_status", null)
        assertTrue(both > nameOnly && nameOnly > 0)
        assertEquals(0, ToolSearch.score(listOf("anything"), null))
    }
}
