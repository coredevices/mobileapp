package coredevices.util.transcription

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MemInfoParserTest {

    private val sample = """
        MemTotal:       11692324 kB
        MemFree:          312456 kB
        MemAvailable:    3145728 kB
        Buffers:           98304 kB
    """.trimIndent()

    @Test
    fun readsMemAvailableInMegabytes() {
        assertEquals(3072L, parseMemAvailableMB(sample))
    }

    @Test
    fun ignoresOtherFieldsWithSimilarPrefixes() {
        val text = "MemAvailableFake: 1 kB\nMemAvailable: 2048 kB\n"
        assertEquals(2L, parseMemAvailableMB(text))
    }

    @Test
    fun returnsNullWhenMissing() {
        assertNull(parseMemAvailableMB("MemTotal: 100 kB\nMemFree: 50 kB\n"))
        assertNull(parseMemAvailableMB(""))
    }
}
