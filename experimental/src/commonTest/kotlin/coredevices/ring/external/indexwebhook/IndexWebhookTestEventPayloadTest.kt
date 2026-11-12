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
    fun jsonPayloadMirrorsMultipartFieldsWithBase64Audio() {
        val body = buildWebhookJsonBody(
            audioData = byteArrayOf(1, 2, 3),
            filename = "abc.m4a",
            recordedAt = 1L,
            transcription = "hello \"world\"",
            isTest = false,
        ).decodeToString()

        assertEquals(
            """{"audio":"AQID","filename":"abc.m4a","audioContentType":"audio/mp4",""" +
                """"transcription":"hello \"world\"","recordedAt":1,"client":"ring"}""",
            body,
        )
    }

    @Test
    fun jsonTestEventCarriesTranscriptionOnly() {
        val body = buildWebhookJsonBody(
            audioData = null,
            filename = "recording.m4a",
            recordedAt = 1L,
            transcription = WEBHOOK_TEST_TRANSCRIPTION,
            isTest = true,
        ).decodeToString()

        assertEquals(
            """{"transcription":"Index webhook test event","test":true,"recordedAt":1,"client":"ring"}""",
            body,
        )
    }

    @Test
    fun testEventTriggerValueIsDistinctFromEveryGestureValue() {
        assertEquals("test-event", WEBHOOK_TEST_TRIGGER)
        assertTrue(
            IndexWebhookPreferences.gestures.none { it.webhookTriggerValue == WEBHOOK_TEST_TRIGGER }
        )
    }
}
