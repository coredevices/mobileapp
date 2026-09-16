package coredevices.ring.service.recordings.button

import coredevices.ring.external.indexwebhook.IndexWebhookPayloadMode
import coredevices.ring.service.recordings.RecordingProcessingQueue
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebhookNeedsOwnTranscriptionTest {

    private val nonTranscribing = object : RecordingOperation {
        override suspend fun run(handle: RecordingProcessingQueue.TaskHandle?) = Unit
    }

    private val transcribing = object : TranscribingRecordingOperation {
        override var onTranscriptionPersisted: (suspend (transcription: String) -> Unit)? = null
        override suspend fun run(handle: RecordingProcessingQueue.TaskHandle?) = Unit
    }

    @Test
    fun transcriptBearingPayloadWithNonTranscribingInnerNeedsTranscription() {
        assertTrue(webhookNeedsOwnTranscription(IndexWebhookPayloadMode.TranscriptionOnly, nonTranscribing, "file-1"))
        assertTrue(webhookNeedsOwnTranscription(IndexWebhookPayloadMode.Both, nonTranscribing, "file-1"))
    }

    @Test
    fun recordingOnlyPayloadDoesNot() {
        assertFalse(webhookNeedsOwnTranscription(IndexWebhookPayloadMode.RecordingOnly, nonTranscribing, "file-1"))
    }

    @Test
    fun transcribingInnerAlreadyProvidesTheTranscript() {
        assertFalse(webhookNeedsOwnTranscription(IndexWebhookPayloadMode.TranscriptionOnly, transcribing, "file-1"))
        assertFalse(webhookNeedsOwnTranscription(IndexWebhookPayloadMode.Both, transcribing, "file-1"))
    }

    @Test
    fun typedInputHasNoAudioToTranscribe() {
        assertFalse(webhookNeedsOwnTranscription(IndexWebhookPayloadMode.TranscriptionOnly, nonTranscribing, null))
    }
}
