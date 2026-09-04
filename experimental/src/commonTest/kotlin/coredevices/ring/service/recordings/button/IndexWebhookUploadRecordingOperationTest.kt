package coredevices.ring.service.recordings.button

import com.russhwolf.settings.MapSettings
import coredevices.indexai.data.entity.LocalRecording
import coredevices.indexai.database.dao.LocalRecordingDao
import coredevices.ring.data.entity.room.CachedRecordingMetadata
import coredevices.ring.external.indexwebhook.IndexWebhookConfig
import coredevices.ring.external.indexwebhook.IndexWebhookDelivery
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
import kotlin.test.assertNotNull
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
        val deliveries = mutableListOf<IndexWebhookDelivery>()
        val inner = FakeTranscribingOp(transcript = "spoken")
        buildDecorator(deliveries, IndexWebhookPayloadMode.RecordingOnly, inner, fileId = "rec-1", recordingId = 1)
            .run(null)
        advanceUntilIdle()

        // Decorator sent without attaching the transcription hook: it did not wait.
        assertFalse(inner.hookPresentAtRun)
        assertEquals(1, deliveries.size)
        assertNotNull(deliveries.single().audioData)
        assertNull(deliveries.single().transcription)
    }

    @Test
    fun transcriptModeSendsTheCapturedTranscript() = runTest {
        val deliveries = mutableListOf<IndexWebhookDelivery>()
        val inner = FakeTranscribingOp(transcript = "the captured transcript")
        buildDecorator(deliveries, IndexWebhookPayloadMode.Both, inner, fileId = "rec-2", recordingId = 2)
            .run(null)
        advanceUntilIdle()

        assertTrue(inner.hookPresentAtRun)
        assertEquals(1, deliveries.size)
        assertEquals("the captured transcript", deliveries.single().transcription)
    }

    @Test
    fun deferredHookSendStillWinsOverTheFallback() = runTest {
        val deliveries = mutableListOf<IndexWebhookDelivery>()
        // A real dispatcher queues the hook's send instead of running it eagerly, so run()
        // returns before it claims the key. The fallback must not fire a null-transcript
        // send and beat it. Delivered exactly once, with the real transcript.
        val inner = FakeTranscribingOp(transcript = "must survive")
        buildDecorator(
            deliveries, IndexWebhookPayloadMode.Both, inner, fileId = "rec-3", recordingId = 3,
            dispatcher = StandardTestDispatcher(testScheduler),
        ).run(null)
        advanceUntilIdle()

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
        advanceUntilIdle()

        assertEquals(1, deliveries.size)
        assertEquals("delivered", deliveries.single().transcription)
    }

    @Test
    fun transcriptionOnlyRecordingSendsTranscriptWithoutAudio() = runTest {
        val deliveries = mutableListOf<IndexWebhookDelivery>()
        val inner = FakeTranscribingOp(transcript = "just the words")
        buildDecorator(deliveries, IndexWebhookPayloadMode.TranscriptionOnly, inner, fileId = "rec-6", recordingId = 6)
            .run(null)
        advanceUntilIdle()

        assertEquals(1, deliveries.size)
        assertNull(deliveries.single().audioData)
        assertEquals("just the words", deliveries.single().transcription)
    }

    @Test
    fun typedInputSendsTheTextAsTranscript() = runTest {
        val deliveries = mutableListOf<IndexWebhookDelivery>()
        val inner = FakeTranscribingOp(transcript = "typed note")
        buildDecorator(deliveries, IndexWebhookPayloadMode.TranscriptionOnly, inner, fileId = null, recordingId = 5)
            .run(null)
        advanceUntilIdle()

        assertEquals(1, deliveries.size)
        assertNull(deliveries.single().audioData)
        assertEquals("typed note", deliveries.single().transcription)
    }

    private fun TestScope.buildDecorator(
        deliveries: MutableList<IndexWebhookDelivery>,
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
            enqueue = { deliveries += it },
            webhookPreferences = prefs,
            encodeM4a = { _, _ -> byteArrayOf(1) },
            recordingStorage = FakeRecordingStorage,
            decorated = decorated,
            fileId = fileId,
            recordingId = recordingId,
            gesture = RingGesture.Hold,
        )
    }
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
