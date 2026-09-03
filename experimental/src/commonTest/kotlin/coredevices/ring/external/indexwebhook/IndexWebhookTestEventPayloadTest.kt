package coredevices.ring.external.indexwebhook

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndexWebhookTestEventPayloadTest {

    private fun testEventBody(): String = buildWebhookMultipartBody(
        boundary = "BOUNDARY",
        audioData = null,
        filename = "recording.m4a",
        recordedAt = 1_700_000_000_000L,
        transcription = WEBHOOK_TEST_TRANSCRIPTION,
        isTest = true,
    ).decodeToString()

    @Test
    fun testEventPayloadIsMarkedAsATestAndCarriesNoAudio() {
        val body = testEventBody()

        assertEquals(
            "--BOUNDARY\r\n" +
                "Content-Disposition: form-data; name=\"transcription\"\r\n\r\n" +
                "Index webhook test event\r\n" +
                "--BOUNDARY\r\n" +
                "Content-Disposition: form-data; name=\"test\"\r\n\r\n" +
                "true\r\n" +
                "--BOUNDARY\r\n" +
                "Content-Disposition: form-data; name=\"recordedAt\"\r\n\r\n" +
                "1700000000000\r\n" +
                "--BOUNDARY\r\n" +
                "Content-Disposition: form-data; name=\"client\"\r\n\r\n" +
                "ring\r\n" +
                "--BOUNDARY--\r\n",
            body,
        )
        assertFalse(body.contains("name=\"audio\""))
    }

    @Test
    fun realRecordingPayloadIsNotMarkedAsATest() {
        val body = buildWebhookMultipartBody(
            boundary = "BOUNDARY",
            audioData = byteArrayOf(1, 2, 3),
            filename = "abc.m4a",
            recordedAt = 1L,
            transcription = "hello",
            isTest = false,
        ).decodeToString()

        assertFalse(body.contains("name=\"test\""))
        assertTrue(body.contains("Content-Disposition: form-data; name=\"audio\"; filename=\"abc.m4a\""))
        assertTrue(body.contains("Content-Type: audio/mp4"))
    }

    @Test
    fun locationFieldsAreAllIncludedWhenAvailable() {
        val body = buildWebhookMultipartBody(
            boundary = "BOUNDARY",
            audioData = null,
            filename = "recording.m4a",
            recordedAt = 1L,
            transcription = "hello",
            isTest = false,
            location = IndexWebhookLocation(
                latitude = 37.775,
                longitude = -122.419,
                timestamp = 1_700_000_000_000L,
            ),
        ).decodeToString()

        assertTrue(body.contains("name=\"locationLatitude\"\r\n\r\n37.775\r\n"))
        assertTrue(body.contains("name=\"locationLongitude\"\r\n\r\n-122.419\r\n"))
        assertTrue(body.contains("name=\"locationTimestamp\"\r\n\r\n1700000000000\r\n"))
    }

    @Test
    fun locationFieldsAreAllOmittedWhenUnavailable() {
        val body = testEventBody()

        assertFalse(body.contains("name=\"locationLatitude\""))
        assertFalse(body.contains("name=\"locationLongitude\""))
        assertFalse(body.contains("name=\"locationTimestamp\""))
    }

    @Test
    fun testEventTriggerValueIsDistinctFromEveryGestureValue() {
        assertEquals("test-event", WEBHOOK_TEST_TRIGGER)
        assertTrue(
            IndexWebhookPreferences.gestures.none { it.webhookTriggerValue == WEBHOOK_TEST_TRIGGER }
        )
    }
}
