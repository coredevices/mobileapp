package coredevices.ring.external.indexwebhook

import coredevices.ring.service.button.RingGesture
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class IndexWebhookButtonEventPayloadTest {

    private fun body(gesture: RingGesture, payload: String = "", isTest: Boolean = false): JsonObject =
        Json.parseToJsonElement(
            buildButtonEventBody(gesture, payload, Instant.fromEpochMilliseconds(1_700_000_000_000L), isTest)
                .decodeToString()
        ).jsonObject

    @Test
    fun carriesTheClickCountAndGesture() {
        val body = body(RingGesture.TripleClick)

        assertEquals(3, body.getValue("clicks").jsonPrimitive.int)
        assertEquals("triple-click", body.getValue("gesture").jsonPrimitive.content)
        assertEquals("button", body.getValue("event").jsonPrimitive.content)
        assertEquals(1_700_000_000_000L, body.getValue("pressedAt").jsonPrimitive.content.toLong())
        assertFalse("payload" in body)
        assertFalse("test" in body)
        assertEquals(1, body(RingGesture.Click).getValue("clicks").jsonPrimitive.int)
        assertEquals(2, body(RingGesture.DoubleClick).getValue("clicks").jsonPrimitive.int)
    }

    @Test
    fun jsonPayloadIsEmbeddedAsJson() {
        val payload = body(RingGesture.Click, """ {"entity_id": "light.desk"} """).getValue("payload")

        assertEquals("light.desk", payload.jsonObject.getValue("entity_id").jsonPrimitive.content)
    }

    @Test
    fun plainTextPayloadIsEmbeddedAsAString() {
        assertEquals(
            JsonPrimitive("toggle desk lamp"),
            body(RingGesture.Click, "toggle desk lamp").getValue("payload"),
        )
    }

    @Test
    fun testEventsAreMarked() {
        assertTrue(body(RingGesture.Click, isTest = true).getValue("test").jsonPrimitive.content.toBoolean())
    }

    @Test
    fun clickTriggerValuesAreDistinct() {
        val values = RingGesture.entries.map { it.webhookTriggerValue } + WEBHOOK_TEST_TRIGGER
        assertEquals(values.size, values.toSet().size)
    }
}
