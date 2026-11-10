package io.rebble.libpebblecommon.js

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class XMLHTTPRequestManagerTest {

    @Test
    fun noHostHeaderMeansNothingExtraToCheck() {
        assertNull(hostHeaderUrl("https://api.example.com/v1", mapOf("Accept" to "application/json")))
        assertNull(hostHeaderUrl("https://api.example.com/v1", mapOf("Host" to " ")))
    }

    @Test
    fun hostHeaderIsCheckedOnTheRequestScheme() {
        assertEquals(
            "https://other.example.org/",
            hostHeaderUrl("https://api.example.com/v1?x=1", mapOf("Host" to "other.example.org")),
        )
        assertEquals(
            "http://192.168.1.10:8080/",
            hostHeaderUrl("http://cdn.example.com/", mapOf("host" to "192.168.1.10:8080")),
        )
    }

    @Test
    fun hostHeaderNameIsCaseInsensitive() {
        assertEquals(
            "https://other.example.org/",
            hostHeaderUrl("https://api.example.com/", mapOf("HOST" to "other.example.org")),
        )
    }
}
