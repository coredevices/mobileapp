package io.rebble.libpebblecommon.permissions

import kotlin.test.Test
import kotlin.test.assertEquals

class RequestBytesTest {
    @Test
    fun countsTheUrlHeadersAndBody() {
        val url = "https://api.example.com/w?lat=59.9&lon=10.7"
        assertEquals(3L + url.length + "X-Key".length + "abc".length + 5, requestBytes("GET", url, mapOf("X-Key" to "abc"), 5))
    }
}
