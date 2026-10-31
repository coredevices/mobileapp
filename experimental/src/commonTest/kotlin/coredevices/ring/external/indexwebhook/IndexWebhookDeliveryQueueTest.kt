package coredevices.ring.external.indexwebhook

import com.russhwolf.settings.MapSettings
import coredevices.indexai.data.entity.LocalRecording
import coredevices.indexai.database.dao.LocalRecordingDao
import coredevices.ring.data.entity.room.CachedRecordingMetadata
import coredevices.ring.data.entity.room.IndexWebhookDeliveryEntity
import coredevices.ring.database.room.dao.IndexWebhookDeliveryDao
import coredevices.ring.database.room.repository.IndexWebhookDeliveryRepository
import coredevices.ring.service.RecordingBackgroundScope
import coredevices.ring.service.button.RingGesture
import coredevices.ring.storage.RecordingStorage
import coredevices.ring.storage.StagedRecordingUpload
import coredevices.util.queue.RecoverableTaskException
import coredevices.util.queue.TaskStatus
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class IndexWebhookDeliveryQueueTest {

    private val created = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun delivery(fileId: String? = null, transcription: String? = "hello") = IndexWebhookDeliveryEntity(
        id = 1,
        created = created,
        deliveryId = fileId ?: "text-7",
        gesture = RingGesture.Hold,
        recordingId = 7,
        fileId = fileId,
        transcription = transcription,
    )

    private fun TestScope.queue(api: FakeWebhookApi) = IndexWebhookDeliveryQueue(
        repository = IndexWebhookDeliveryRepository(FakeDeliveryDao()),
        webhookApi = api,
        recordingStorage = FakeRecordingStorage,
        localRecordingDao = FakeLocalRecordingDao,
        scope = RecordingBackgroundScope(backgroundScope),
        settings = MapSettings(),
    )

    @Test
    fun sendsTheQueuedPayload() = runTest {
        val api = FakeWebhookApi(result(ok = true))
        queue(api).processTask(delivery())

        val call = api.calls.single()
        assertEquals("text-7", call.recordingId)
        assertEquals("hello", call.transcription)
        assertNull(call.samples)
        assertEquals(RingGesture.Hold, call.gesture)
        // The recording is gone, so its capture time falls back to when the delivery was queued.
        assertEquals(created, call.recordedAt)
    }

    @Test
    fun readsAudioFromTheRecordingWhenSending() = runTest {
        val api = FakeWebhookApi(result(ok = true))
        queue(api).processTask(delivery(fileId = "rec-1", transcription = null))

        assertEquals(listOf<Short>(1, -2), api.calls.single().samples?.toList())
    }

    @Test
    fun retryableFailureIsRecoverable() = runTest {
        val api = FakeWebhookApi(result(ok = false, retryable = true))
        assertFailsWith<RecoverableTaskException> { queue(api).processTask(delivery()) }
    }

    @Test
    fun permanentFailureIsNotRecoverable() = runTest {
        val api = FakeWebhookApi(result(ok = false, retryable = false))
        assertFailsWith<IllegalStateException> { queue(api).processTask(delivery()) }
    }

    @Test
    fun disabledWebhookIsDone() = runTest {
        val api = FakeWebhookApi(result = null)
        queue(api).processTask(delivery())
        assertEquals(1, api.calls.size)
    }

    @Test
    fun onlyTransientStatusesAreRetryable() {
        listOf(408, 429, 500, 502, 503, 504).forEach {
            assertTrue(HttpStatusCode.fromValue(it).isRetryableWebhookStatus(), "$it")
        }
        listOf(400, 401, 403, 404, 409, 413, 422).forEach {
            assertFalse(HttpStatusCode.fromValue(it).isRetryableWebhookStatus(), "$it")
        }
    }

    private fun result(ok: Boolean, retryable: Boolean = false) = IndexWebhookRunResult(
        ok = ok,
        status = if (ok) "200 OK" else "503 ERROR",
        detail = "",
        byteSize = 0,
        durationMs = 0,
        retryable = retryable,
    )
}

private class FakeWebhookApi(private val result: IndexWebhookRunResult?) : IndexWebhookApi {
    data class Call(
        val samples: ShortArray?,
        val recordingId: String,
        val transcription: String?,
        val recordedAt: Instant,
        val gesture: RingGesture,
    )

    val calls = mutableListOf<Call>()

    override suspend fun upload(
        samples: ShortArray?,
        sampleRate: Int,
        recordingId: String,
        transcription: String?,
        recordedAt: Instant,
        gesture: RingGesture,
    ): IndexWebhookRunResult? {
        calls += Call(samples, recordingId, transcription, recordedAt, gesture)
        return result
    }

    override fun sendButtonEventIfEnabled(gesture: RingGesture, pressedAt: Instant) = Unit

    override suspend fun sendTestEvent(
        gesture: RingGesture,
        url: String,
        headers: Map<String, String>,
        signRequests: Boolean,
        signingSecret: String?,
        clickPayload: String,
    ) = throw NotImplementedError("unused")
}

private class FakeDeliveryDao : IndexWebhookDeliveryDao {
    override suspend fun insertTask(task: IndexWebhookDeliveryEntity): Long = TODO()
    override suspend fun getPendingTasks(): List<IndexWebhookDeliveryEntity> = emptyList()
    override suspend fun updateTaskStatus(taskId: Long, status: TaskStatus) = TODO()
    override suspend fun deleteTask(taskId: Long) = TODO()
    override suspend fun deleteCompletedTasksAttemptedBefore(before: Instant) = TODO()
    override suspend fun getTaskById(taskId: Long): IndexWebhookDeliveryEntity? = TODO()
    override suspend fun incrementAttempts(taskId: Long, currentTime: Instant) = TODO()
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
    ) = Buffer().apply { write(byteArrayOf(1, 0, -2, -1)) } to RecordingStorage.RecordingSourceInfo(
        id = idNoSuffix,
        cachedMetadata = CachedRecordingMetadata(id = idNoSuffix, sampleRate = 16000, mimeType = "audio/raw"),
        size = 4,
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
    override suspend fun uploadStagedRecording(upload: StagedRecordingUpload) = TODO()
    override fun deleteStagedRecording(id: String) = TODO()
}
