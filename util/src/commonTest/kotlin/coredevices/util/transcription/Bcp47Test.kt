package coredevices.util.transcription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Bcp47Test {
    @Test
    fun qualifiesBareCodeWithRegion() {
        assertEquals("en-US", toBcp47("en", "US"))
        // Region is normalized to uppercase per BCP-47.
        assertEquals("es-ES", toBcp47("es", "es"))
    }

    @Test
    fun fallsBackToCanonicalRegionWhenNoDeviceRegion() {
        assertEquals("es-ES", toBcp47("es", null))
        assertEquals("pt-BR", toBcp47("pt", ""))
        assertEquals("zh-CN", toBcp47("zh", "   "))
    }

    @Test
    fun fallsBackToBareCodeWhenRegionUnresolvable() {
        // Region absent and language not in the canonical table.
        assertEquals("xx", toBcp47("xx", null))
    }

    @Test
    fun leavesAlreadyQualifiedCodeUntouched() {
        assertEquals("zh-Hans", toBcp47("zh-Hans", "US"))
        assertEquals("pt_BR", toBcp47("pt_BR", "US"))
    }

    @Test
    fun coversLanguageMatchesOnPrimarySubtagOnly() {
        val tags = listOf("en-GB", "es-ES", "zh-Hans-CN")
        assertTrue(tags.coversLanguage("en-US"))
        assertTrue(tags.coversLanguage("en"))
        assertTrue(tags.coversLanguage("zh-Hant-TW"))
        assertFalse(tags.coversLanguage("fr-FR"))
    }

    @Test
    fun coversLanguageIgnoresCaseAndEmptyTagList() {
        assertTrue(listOf("EN-gb").coversLanguage("en-US"))
        assertFalse(emptyList<String>().coversLanguage("en-US"))
    }
}
