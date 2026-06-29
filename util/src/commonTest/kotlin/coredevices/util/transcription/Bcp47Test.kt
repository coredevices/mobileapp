package coredevices.util.transcription

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
