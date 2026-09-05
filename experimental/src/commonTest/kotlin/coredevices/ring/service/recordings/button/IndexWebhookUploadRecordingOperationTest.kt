package coredevices.ring.service.recordings.button

import com.russhwolf.settings.MapSettings
import coredevices.ring.external.indexwebhook.IndexWebhookConfig
import coredevices.ring.external.indexwebhook.IndexWebhookDelivery
import coredevices.ring.external.indexwebhook.IndexWebhookPayloadMode
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.service.RecordingBackgroundScope
import coredevices.ring.service.button.RingGesture
import coredevices.ring.service.recordings.RecordingProcessingQueue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IndexWebhookUploadRecordingOperationTest {

    @Test
    fun recordingOnlyPreparesWithoutBlockingTranscription() = runTest {
        val deliveries = mutableListOf<IndexWebhookDelivery>()
        val releasePreparation = CompletableDeferred<Unit>()
        var innerRan = false
        val inner = FakeTranscribingOp(transcript = "spoken") { innerRan = true }
        val operation = buildDecorator(
            deliveries,
            IndexWebhookPayloadMode.RecordingOnly,
            inner,
            fileId = "rec-1",
            recordingId = 1,
            enqueue = {
                releasePreparation.await()
                deliveries += it
            },
        )

        val job = launch { operation.run(null) }
        testScheduler.runCurrent()
        assertTrue(innerRan)
        assertFalse(job.isCompleted)
        releasePreparation.complete(Unit)
        job.join()

        assertFalse(inner.hookPresentAtRun)
        assertEquals(1, deliveries.size)
        assertEquals("rec-1", deliveries.single().fileId)
        assertNull(deliveries.single().transcription)
    }

    @Test
    fun transcriptModeSendsTheCapturedTranscript() = runTest {
        val deliveries = mutableListOf<IndexWebhookDelivery>()
        val inner = FakeTranscribingOp(transcript = "the captured transcript")
        buildDecorator(deliveries, IndexWebhookPayloadMode.Both, inner, fileId = "rec-2", recordingId = 2)
            .run(null)
        assertTrue(inner.hookPresentAtRun)
        assertEquals(1, deliveries.size)
        assertEquals("the captured transcript", deliveries.single().transcription)
    }

    @Test
    fun transcriptHookDoesNotAlsoSendTheFallback() = runTest {
        val deliveries = mutableListOf<IndexWebhookDelivery>()
        val inner = FakeTranscribingOp(transcript = "must survive")
        buildDecorator(
            deliveries, IndexWebhookPayloadMode.Both, inner, fileId = "rec-3", recordingId = 3,
        ).run(null)
        assertEquals(1, deliveries.size)
        assertEquals("must survive", deliveries.single().transcription)
    }

    @Test
    fun webhookStillFiresWhenAgentFailsAfterTranscription() = runTest {
        val deliveries = mutableListOf<IndexWebhookDelivery>()
        val inner = FakeTranscribingOp(transcript = "delivered", throwAfterHook = true)
        val decorator =
            buildDecorator(deliveries, IndexWebhookPayloadMode.Both, inner, fileId = "rec-4", recordingId = 4)

        assertFailsWith<IllegalStateException> { decorator.run(null) }
        assertEquals(1, deliveries.size)
        assertEquals("delivered", deliveries.single().transcription)
    }

    @Test
    fun enqueueFailureDoesNotSkipRecordingProcessing() = runTest {
        var innerRan = false
        val inner = FakeTranscribingOp(transcript = "spoken") { innerRan = true }
        buildDecorator(
            mutableListOf(),
            IndexWebhookPayloadMode.RecordingOnly,
            inner,
            fileId = "rec-7",
            recordingId = 7,
            enqueue = { error("database failed") },
        ).run(null)

        assertTrue(innerRan)
    }

    @Test
    fun transcriptionOnlyRecordingSendsTranscriptWithoutAudio() = runTest {
        val deliveries = mutableListOf<IndexWebhookDelivery>()
        val inner = FakeTranscribingOp(transcript = "just the words")
        buildDecorator(deliveries, IndexWebhookPayloadMode.TranscriptionOnly, inner, fileId = "rec-6", recordingId = 6)
            .run(null)
        assertEquals(1, deliveries.size)
        assertNull(deliveries.single().fileId)
        assertEquals("just the words", deliveries.single().transcription)
    }

    @Test
    fun typedInputSendsTheTextAsTranscript() = runTest {
        val deliveries = mutableListOf<IndexWebhookDelivery>()
        val inner = FakeTranscribingOp(transcript = "typed note")
        buildDecorator(deliveries, IndexWebhookPayloadMode.TranscriptionOnly, inner, fileId = null, recordingId = 5)
            .run(null)
        assertEquals(1, deliveries.size)
        assertNull(deliveries.single().fileId)
        assertEquals("typed note", deliveries.single().transcription)
    }

    private fun TestScope.buildDecorator(
        deliveries: MutableList<IndexWebhookDelivery>,
        mode: IndexWebhookPayloadMode,
        decorated: RecordingOperation,
        fileId: String?,
        recordingId: Long,
        enqueue: suspend (IndexWebhookDelivery) -> Unit = { deliveries += it },
    ): IndexWebhookUploadRecordingOperation {
        val prefs = IndexWebhookPreferences(MapSettings()).apply {
            setConfig(
                RingGesture.Hold,
                IndexWebhookConfig(url = "https://example.com/hook", payloadMode = mode, saved = true),
            )
        }
        return IndexWebhookUploadRecordingOperation(
            enqueue = enqueue,
            webhookPreferences = prefs,
            decorated = decorated,
            fileId = fileId,
            recordingId = recordingId,
            gesture = RingGesture.Hold,
            backgroundScope = RecordingBackgroundScope(backgroundScope),
        )
    }
}

private class FakeTranscribingOp(
    private val transcript: String,
    private val throwAfterHook: Boolean = false,
    private val onRun: () -> Unit = {},
) : TranscribingRecordingOperation {
    override var onTranscriptionPersisted: (suspend (transcription: String) -> Unit)? = null
    var hookPresentAtRun = false

    override suspend fun run(handle: RecordingProcessingQueue.TaskHandle?) {
        onRun()
        hookPresentAtRun = onTranscriptionPersisted != null
        onTranscriptionPersisted?.invoke(transcript)
        if (throwAfterHook) throw IllegalStateException("agent failed")
    }
}
