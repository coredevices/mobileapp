package coredevices.ring.service.recordings.button

import com.russhwolf.settings.MapSettings
import coredevices.ring.data.entity.room.IndexWebhookDeliveryEntity
import coredevices.ring.external.indexwebhook.IndexWebhookConfig
import coredevices.ring.external.indexwebhook.IndexWebhookPayloadMode
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.service.RecordingBackgroundScope
import coredevices.ring.service.button.RingGesture
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.util.queue.RecoverableTaskException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IndexWebhookUploadRecordingOperationTest {

    @AfterTest
    fun tearDown() {
        runCatching { stopKoin() }
    }

    @Test
    fun recordingOnlySendsWithoutWaitingForTranscription() = runTest {
        val queue = FakeQueue()
        val inner = FakeTranscribingOp(transcript = "spoken")
        buildDecorator(queue, IndexWebhookPayloadMode.RecordingOnly, inner, fileId = "rec-1", recordingId = 1)
            .run(null)
        advanceUntilIdle()

        // Decorator sent without attaching the transcription hook: it did not wait.
        assertFalse(inner.hookPresentAtRun)
        assertEquals(1, queue.calls.size)
        assertFalse(queue.calls.single().noAudio)
        assertNull(queue.calls.single().transcription)
    }

    @Test
    fun transcriptModeSendsTheCapturedTranscript() = runTest {
        val queue = FakeQueue()
        val inner = FakeTranscribingOp(transcript = "the captured transcript")
        buildDecorator(queue, IndexWebhookPayloadMode.Both, inner, fileId = "rec-2", recordingId = 2)
            .run(null)
        advanceUntilIdle()

        assertTrue(inner.hookPresentAtRun)
        assertEquals(1, queue.calls.size)
        assertEquals("the captured transcript", queue.calls.single().transcription)
    }

    @Test
    fun deferredHookSendStillWinsOverTheFallback() = runTest {
        val queue = FakeQueue()
        // A real dispatcher queues the hook's send instead of running it eagerly, so run()
        // returns before it claims the key. The fallback must not fire a null-transcript
        // send and beat it. Delivered exactly once, with the real transcript.
        val inner = FakeTranscribingOp(transcript = "must survive")
        buildDecorator(
            queue, IndexWebhookPayloadMode.Both, inner, fileId = "rec-3", recordingId = 3,
            dispatcher = StandardTestDispatcher(testScheduler),
        ).run(null)
        advanceUntilIdle()

        assertEquals(1, queue.calls.size)
        assertEquals("must survive", queue.calls.single().transcription)
    }

    @Test
    fun webhookStillFiresWhenAgentFailsAfterTranscription() = runTest {
        val queue = FakeQueue()
        val inner = FakeTranscribingOp(transcript = "delivered", throwAfterHook = true)
        val decorator = buildDecorator(
            queue, IndexWebhookPayloadMode.Both, inner, fileId = "rec-4", recordingId = 4,
            dispatcher = StandardTestDispatcher(testScheduler),
        )

        assertFailsWith<IllegalStateException> { decorator.run(null) }
        advanceUntilIdle()

        assertEquals(1, queue.calls.size)
        assertEquals("delivered", queue.calls.single().transcription)
    }

    @Test
    fun audioStillSendsWhenTranscriptionFails() = runTest {
        val queue = FakeQueue()
        val inner = FakeTranscribingOp(transcript = "unused", failBeforeHook = IllegalStateException("no speech"))
        val decorator =
            buildDecorator(queue, IndexWebhookPayloadMode.Both, inner, fileId = "rec-7", recordingId = 7)

        assertFailsWith<IllegalStateException> { decorator.run(null) }
        advanceUntilIdle()

        assertEquals(1, queue.calls.size)
        assertFalse(queue.calls.single().noAudio)
        assertNull(queue.calls.single().transcription)
    }

    @Test
    fun recoverableTranscriptionFailureWaitsForRetry() = runTest {
        val queue = FakeQueue()
        val inner = FakeTranscribingOp(transcript = "unused", failBeforeHook = RecoverableTaskException("offline"))
        val decorator =
            buildDecorator(queue, IndexWebhookPayloadMode.Both, inner, fileId = "rec-8", recordingId = 8)

        assertFailsWith<RecoverableTaskException> { decorator.run(null) }
        advanceUntilIdle()

        assertTrue(queue.calls.isEmpty())
    }

    @Test
    fun transcriptionOnlySendsNothingWhenTranscriptionFails() = runTest {
        val queue = FakeQueue()
        val inner = FakeTranscribingOp(transcript = "unused", failBeforeHook = IllegalStateException("no speech"))
        val decorator =
            buildDecorator(queue, IndexWebhookPayloadMode.TranscriptionOnly, inner, fileId = "rec-9", recordingId = 9)

        assertFailsWith<IllegalStateException> { decorator.run(null) }
        advanceUntilIdle()

        assertTrue(queue.calls.isEmpty())
    }

    @Test
    fun transcriptionOnlyRecordingSendsTranscriptWithoutAudio() = runTest {
        val queue = FakeQueue()
        val inner = FakeTranscribingOp(transcript = "just the words")
        buildDecorator(queue, IndexWebhookPayloadMode.TranscriptionOnly, inner, fileId = "rec-6", recordingId = 6)
            .run(null)
        advanceUntilIdle()

        assertEquals(1, queue.calls.size)
        assertTrue(queue.calls.single().noAudio)
        assertEquals("just the words", queue.calls.single().transcription)
    }

    @Test
    fun typedInputSendsTheTextAsTranscript() = runTest {
        val queue = FakeQueue()
        val inner = FakeTranscribingOp(transcript = "typed note")
        buildDecorator(queue, IndexWebhookPayloadMode.TranscriptionOnly, inner, fileId = null, recordingId = 5)
            .run(null)
        advanceUntilIdle()

        assertEquals(1, queue.calls.size)
        assertTrue(queue.calls.single().noAudio)
        assertEquals("typed note", queue.calls.single().transcription)
    }

    private fun TestScope.buildDecorator(
        queue: FakeQueue,
        mode: IndexWebhookPayloadMode,
        decorated: RecordingOperation,
        fileId: String?,
        recordingId: Long,
        dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(testScheduler),
    ): IndexWebhookUploadRecordingOperation {
        startKoin {
            modules(module {
                single { RecordingBackgroundScope(CoroutineScope(dispatcher)) }
            })
        }
        val prefs = IndexWebhookPreferences(MapSettings()).apply {
            setConfig(
                RingGesture.Hold,
                IndexWebhookConfig(url = "https://example.com/hook", payloadMode = mode, saved = true),
            )
        }
        return IndexWebhookUploadRecordingOperation(
            enqueue = queue::enqueue,
            webhookPreferences = prefs,
            decorated = decorated,
            fileId = fileId,
            recordingId = recordingId,
            gesture = RingGesture.Hold,
        )
    }
}

private class FakeQueue {
    data class Call(val noAudio: Boolean, val transcription: String?)

    val calls = mutableListOf<Call>()

    fun enqueue(delivery: IndexWebhookDeliveryEntity) {
        calls += Call(noAudio = delivery.fileId == null, transcription = delivery.transcription)
    }
}

private class FakeTranscribingOp(
    private val transcript: String,
    private val throwAfterHook: Boolean = false,
    private val failBeforeHook: Throwable? = null,
) : TranscribingRecordingOperation {
    override var onTranscriptionPersisted: (suspend (transcription: String) -> Unit)? = null
    var hookPresentAtRun = false

    override suspend fun run(handle: RecordingProcessingQueue.TaskHandle?) {
        hookPresentAtRun = onTranscriptionPersisted != null
        failBeforeHook?.let { throw it }
        onTranscriptionPersisted?.invoke(transcript)
        if (throwAfterHook) throw IllegalStateException("agent failed")
    }
}
