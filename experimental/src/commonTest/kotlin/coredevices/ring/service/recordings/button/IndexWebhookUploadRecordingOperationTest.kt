package coredevices.ring.service.recordings.button

import com.russhwolf.settings.MapSettings
import coredevices.indexai.data.entity.LocalRecording
import coredevices.indexai.database.dao.LocalRecordingDao
import coredevices.ring.data.entity.room.CachedRecordingMetadata
import coredevices.ring.external.indexwebhook.IndexWebhookApi
import coredevices.ring.external.indexwebhook.IndexWebhookConfig
import coredevices.ring.external.indexwebhook.IndexWebhookPayloadMode
import coredevices.ring.external.indexwebhook.IndexWebhookPreferences
import coredevices.ring.service.RecordingBackgroundScope
import coredevices.ring.service.button.RingGesture
import coredevices.ring.service.recordings.RecordingProcessingQueue
import coredevices.ring.storage.RecordingStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlin.time.Instant
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
        val api = FakeWebhookApi()
        val inner = FakeTranscribingOp(transcript = "spoken")
        buildDecorator(api, IndexWebhookPayloadMode.RecordingOnly, inner, fileId = "rec-1", recordingId = 1)
            .run(null)
        advanceUntilIdle()

        // Decorator sent without attaching the transcription hook: it did not wait.
        assertFalse(inner.hookPresentAtRun)
        assertEquals(1, api.calls.size)
        assertFalse(api.calls.single().samplesNull)
        assertNull(api.calls.single().transcription)
    }

    @Test
    fun transcriptModeSendsTheCapturedTranscript() = runTest {
        val api = FakeWebhookApi()
        val inner = FakeTranscribingOp(transcript = "the captured transcript")
        buildDecorator(api, IndexWebhookPayloadMode.Both, inner, fileId = "rec-2", recordingId = 2)
            .run(null)
        advanceUntilIdle()

        assertTrue(inner.hookPresentAtRun)
        assertEquals(1, api.calls.size)
        assertEquals("the captured transcript", api.calls.single().transcription)
    }

    @Test
    fun deferredHookSendStillWinsOverTheFallback() = runTest {
        val api = FakeWebhookApi()
        // A real dispatcher queues the hook's send instead of running it eagerly, so run()
        // returns before it claims the key. The fallback must not fire a null-transcript
        // send and beat it. Delivered exactly once, with the real transcript.
        val inner = FakeTranscribingOp(transcript = "must survive")
        buildDecorator(
            api, IndexWebhookPayloadMode.Both, inner, fileId = "rec-3", recordingId = 3,
            dispatcher = StandardTestDispatcher(testScheduler),
        ).run(null)
        advanceUntilIdle()

        assertEquals(1, api.calls.size)
        assertEquals("must survive", api.calls.single().transcription)
    }

    @Test
    fun webhookStillFiresWhenAgentFailsAfterTranscription() = runTest {
        val api = FakeWebhookApi()
        val inner = FakeTranscribingOp(transcript = "delivered", throwAfterHook = true)
        val decorator =
            buildDecorator(api, IndexWebhookPayloadMode.Both, inner, fileId = "rec-4", recordingId = 4)

        assertFailsWith<IllegalStateException> { decorator.run(null) }
        advanceUntilIdle()

        assertEquals(1, api.calls.size)
        assertEquals("delivered", api.calls.single().transcription)
    }

    @Test
    fun transcriptionOnlyRecordingSendsTranscriptWithoutAudio() = runTest {
        val api = FakeWebhookApi()
        val inner = FakeTranscribingOp(transcript = "just the words")
        buildDecorator(api, IndexWebhookPayloadMode.TranscriptionOnly, inner, fileId = "rec-6", recordingId = 6)
            .run(null)
        advanceUntilIdle()

        assertEquals(1, api.calls.size)
        assertTrue(api.calls.single().samplesNull)
        assertEquals("just the words", api.calls.single().transcription)
    }

    @Test
    fun typedInputSendsTheTextAsTranscript() = runTest {
        val api = FakeWebhookApi()
        val inner = FakeTranscribingOp(transcript = "typed note")
        buildDecorator(api, IndexWebhookPayloadMode.TranscriptionOnly, inner, fileId = null, recordingId = 5)
            .run(null)
        advanceUntilIdle()

        assertEquals(1, api.calls.size)
        assertTrue(api.calls.single().samplesNull)
        assertEquals("typed note", api.calls.single().transcription)
    }

    private fun TestScope.buildDecorator(
        api: IndexWebhookApi,
        mode: IndexWebhookPayloadMode,
        decorated: RecordingOperation,
        fileId: String?,
        recordingId: Long,
        dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(testScheduler),
    ): IndexWebhookUploadRecordingOperation {
        startKoin {
            modules(module {
                single<LocalRecordingDao> { FakeLocalRecordingDao }
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
            webhookApi = api,
            webhookPreferences = prefs,
            recordingStorage = FakeRecordingStorage,
            decorated = decorated,
            fileId = fileId,
            recordingId = recordingId,
            gesture = RingGesture.Hold,
        )
    }
}

private class FakeWebhookApi : IndexWebhookApi {
    data class Call(val samplesNull: Boolean, val transcription: String?)

    val calls = mutableListOf<Call>()

    override fun uploadIfEnabled(
        samples: ShortArray?,
        sampleRate: Int,
        recordingId: String,
        transcription: String?,
        recordedAt: Instant,
        gesture: RingGesture,
    ) {
        calls += Call(samplesNull = samples == null, transcription = transcription)
    }

    override suspend fun sendTestEvent(
        gesture: RingGesture,
        url: String,
        headers: Map<String, String>,
        includeLocation: Boolean,
    ) = throw NotImplementedError("unused")
}

private class FakeTranscribingOp(
    private val transcript: String,
    private val throwAfterHook: Boolean = false,
) : TranscribingRecordingOperation {
    override var onTranscriptionPersisted: (suspend (transcription: String) -> Unit)? = null
    var hookPresentAtRun = false

    override suspend fun run(handle: RecordingProcessingQueue.TaskHandle?) {
        hookPresentAtRun = onTranscriptionPersisted != null
        onTranscriptionPersisted?.invoke(transcript)
        if (throwAfterHook) throw IllegalStateException("agent failed")
    }
}

private object FakeLocalRecordingDao : LocalRecordingDao {
    override suspend fun getRecording(id: Long): LocalRecording? = null
    override suspend fun insertRecording(recording: LocalRecording): Long = TODO()
    override suspend fun updateRecording(recording: LocalRecording) = TODO()
    override suspend fun updateRecordingFirestoreId(id: Long, firestoreId: String) = TODO()
    override suspend fun deleteRecording(recording: LocalRecording) = TODO()
    override suspend fun getByFirestoreId(firestoreId: String): LocalRecording? = TODO()
    override fun getRecordingFlow(id: Long) = TODO()
    override fun getAllRecordings() = TODO()
    override fun getAllRecordingsAfter(timestamp: Instant) = TODO()
    override fun getRecordingsCount() = TODO()
    override fun getPaginatedFeedItems() = TODO()
    override fun getFeedItemByIdFlow(recordingId: Long) = TODO()
    override suspend fun getMostRecentTimestamp(): LocalRecording? = TODO()
    override suspend fun getRecentRecordings(limit: Int): List<LocalRecording> = TODO()
    override suspend fun getAllFirestoreIds(): List<String> = TODO()
    override suspend fun deleteAll() = TODO()
    override suspend fun setUpdated(id: Long, updated: Instant) = TODO()
    override suspend fun setLastPushedUpdated(id: Long, updated: Long) = TODO()
}

private object FakeRecordingStorage : RecordingStorage {
    override suspend fun openRecordingSource(
        idNoSuffix: String,
        useOriginalAudio: Boolean,
    ) = Buffer() to RecordingStorage.RecordingSourceInfo(
        id = idNoSuffix,
        cachedMetadata = CachedRecordingMetadata(id = idNoSuffix, sampleRate = 16000, mimeType = "audio/raw"),
        size = 0,
    )

    override fun getCacheDirectory() = TODO()
    override suspend fun exportRecording(id: String, useOriginalAudio: Boolean) = TODO()
    override suspend fun openRecordingSink(id: String, sampleRate: Int, mimeType: String) = TODO()
    override suspend fun openOriginalRecordingSink(id: String, sampleRate: Int, mimeType: String) = TODO()
    override suspend fun openCachedRecordingSource(idNoSuffix: String, useOriginalAudio: Boolean) = TODO()
    override suspend fun persistRecording(id: String) = TODO()
    override suspend fun uploadRecordingPcm(id: String, sampleRate: Int, pcmBytes: ByteArray, encryptionKey: String?) = TODO()
    override fun deleteRecording(id: String) = TODO()
    override fun deleteRecordingFromCache(id: String) = TODO()
    override fun recordingExists(id: String) = TODO()
    override suspend fun deleteAllCachedMetadata() = TODO()
    override fun clearCacheDirectory() = TODO()
    override suspend fun deleteFromFirebaseStorage(id: String) = TODO()
}
