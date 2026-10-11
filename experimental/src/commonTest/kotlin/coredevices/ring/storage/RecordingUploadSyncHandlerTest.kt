package coredevices.ring.storage

import kotlinx.coroutines.test.runTest
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.files.Path
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class FakeRecordingStorage : RecordingStorage {
    val uploaded = mutableListOf<StagedRecordingUpload>()
    val deletedStaged = mutableListOf<String>()

    override suspend fun uploadStagedRecording(upload: StagedRecordingUpload) {
        uploaded += upload
    }

    override fun deleteStagedRecording(id: String) {
        deletedStaged += id
    }

    override fun getCacheDirectory(): Path = TODO()
    override suspend fun exportRecording(id: String, useOriginalAudio: Boolean): Path = TODO()
    override suspend fun openRecordingSink(id: String, sampleRate: Int, mimeType: String): Sink = TODO()
    override suspend fun openOriginalRecordingSink(id: String, sampleRate: Int, mimeType: String): Sink = TODO()
    override suspend fun openRecordingSource(idNoSuffix: String, useOriginalAudio: Boolean): Pair<Source, RecordingStorage.RecordingSourceInfo> = TODO()
    override suspend fun openCachedRecordingSource(idNoSuffix: String, useOriginalAudio: Boolean): Pair<Source, RecordingStorage.RecordingSourceInfo>? = TODO()
    override suspend fun persistRecording(id: String) = TODO()
    override suspend fun uploadRecordingPcm(id: String, sampleRate: Int, pcmBytes: ByteArray, encryptionKey: String?) = TODO()
    override fun deleteRecording(id: String) = TODO()
    override fun deleteRecordingFromCache(id: String) = TODO()
    override fun recordingExists(id: String): Boolean = TODO()
    override suspend fun deleteAllCachedMetadata() = TODO()
    override fun clearCacheDirectory() = TODO()
    override suspend fun deleteFromFirebaseStorage(id: String) = TODO()
}

class RecordingUploadSyncHandlerTest {
    private val storage = FakeRecordingStorage()
    private val handler = RecordingUploadSyncHandler(storage)

    @Test
    fun payloadRoundTrips() {
        val staged = StagedRecordingUpload("rec-1", 16000, "abcd")
        assertEquals(staged, StagedRecordingUpload.decode(staged.encode()))
        assertEquals(
            StagedRecordingUpload("rec-2", 8000),
            StagedRecordingUpload.decode("""{"id":"rec-2","sampleRate":8000,"future":1}"""),
        )
    }

    @Test
    fun syncUploadsTheStagedRecording() = runTest {
        val staged = StagedRecordingUpload("rec-1", 16000, null)
        handler.sync(staged.encode())
        assertEquals(listOf(staged), storage.uploaded)
    }

    @Test
    fun failedTaskDiscardsTheStagedFile() = runTest {
        handler.onFailed(StagedRecordingUpload("rec-1", 16000).encode())
        assertEquals(listOf("rec-1"), storage.deletedStaged)
    }

    @Test
    fun malformedPayloadFailsPermanently() = runTest {
        assertFailsWith<SerializationException> { handler.sync("not json") }
    }
}
