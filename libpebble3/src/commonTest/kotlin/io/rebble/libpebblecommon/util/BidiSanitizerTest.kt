package io.rebble.libpebblecommon.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BidiSanitizerTest {
    @Test
    fun stripBidiIsolates_nullInput() {
        assertNull(stripBidiIsolates(null))
    }

    @Test
    fun stripBidiIsolates_removesIsolateMarkers() {
        val input = "\u2068Юлия\u2069 and \u2066abc\u2069 and \u2067xyz\u2069"
        val expected = "Юлия and abc and xyz"
        assertEquals(expected, stripBidiIsolates(input))
    }

    @Test
    fun stripBidiIsolates_noopWhenNonePresent() {
        val input = "Sender Name"
        assertEquals(input, stripBidiIsolates(input))
    }
}

