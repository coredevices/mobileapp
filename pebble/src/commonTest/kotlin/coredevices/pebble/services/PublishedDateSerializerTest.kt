package coredevices.pebble.services

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class PublishedDateSerializerTest {
    private val json = Json

    private fun deserialize(value: String?): Instant? {
        val element = if (value == null) JsonNull else JsonPrimitive(value)
        return json.decodeFromJsonElement(PublishedDateSerializer, element)
    }

    @Test
    fun rfc1123Format() {
        val result = deserialize("Sat, 30 Nov 2013 13:36:50 GMT")
        assertEquals(Instant.parse("2013-11-30T13:36:50Z"), result)
    }

    @Test
    fun iso8601WithoutTimezone() {
        val result = deserialize("2013-11-30T13:38:31.987")
        assertEquals(Instant.parse("2013-11-30T13:38:31.987Z"), result)
    }

    @Test
    fun iso8601WithTimezone() {
        val result = deserialize("2013-11-30T13:38:31Z")
        assertEquals(Instant.parse("2013-11-30T13:38:31Z"), result)
    }

    @Test
    fun nullValue() {
        assertNull(deserialize(null))
    }

    @Test
    fun emptyString() {
        assertNull(deserialize(""))
    }

    @Test
    fun serializeRoundTrip() {
        val instant = Instant.parse("2013-11-30T13:36:50Z")
        val encoded = json.encodeToJsonElement(PublishedDateSerializer, instant)
        val decoded = json.decodeFromJsonElement(PublishedDateSerializer, encoded)
        assertEquals(instant, decoded)
    }
}
