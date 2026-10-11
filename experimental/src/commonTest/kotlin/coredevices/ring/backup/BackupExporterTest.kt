package coredevices.ring.backup

import coredevices.indexai.data.entity.RecordingDocument
import coredevices.indexai.data.entity.RecordingEntry
import coredevices.ring.data.entity.room.CachedRecordingMetadata
import coredevices.ring.storage.RecordingStorage
import coredevices.ring.storage.StagedRecordingUpload
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@Ignore // CI JDK is not compatible with our kmpio
class BackupExporterTest {
    private val dir = Path(SystemTemporaryDirectory, "backup-exporter-test-${Random.nextLong()}")
    private val zipPath = Path(dir, "backup.zip")

    private class FakeAudio(val bytes: ByteArray, val sampleRate: Int)

    /** Mirrors RealRecordingStorage's fallback to the other variant when the requested one is missing. */
    private class FakeStorage(private val files: Map<String, FakeAudio>) : RecordingStorage {
        override suspend fun openRecordingSource(idNoSuffix: String, useOriginalAudio: Boolean): Pair<Source, RecordingStorage.RecordingSourceInfo> {
            val requested = if (useOriginalAudio) "$idNoSuffix-original" else idNoSuffix
            val fallback = if (useOriginalAudio) idNoSuffix else "$idNoSuffix-original"
            val id = listOf(requested, fallback).firstOrNull { it in files } ?: error("No such recording $requested")
            val audio = files.getValue(id)
            val source = Buffer().apply { write(audio.bytes) }
            return source to RecordingStorage.RecordingSourceInfo(
                id = id,
                cachedMetadata = CachedRecordingMetadata(id, audio.sampleRate, "audio/raw"),
                size = audio.bytes.size.toLong(),
            )
        }

        override fun getCacheDirectory(): Path = TODO()
        override suspend fun exportRecording(id: String, useOriginalAudio: Boolean): Path = TODO()
        override suspend fun openRecordingSink(id: String, sampleRate: Int, mimeType: String): Sink = TODO()
        override suspend fun openOriginalRecordingSink(id: String, sampleRate: Int, mimeType: String): Sink = TODO()
        override suspend fun openCachedRecordingSource(idNoSuffix: String, useOriginalAudio: Boolean): Pair<Source, RecordingStorage.RecordingSourceInfo>? = TODO()
        override suspend fun persistRecording(id: String) = TODO()
        override suspend fun uploadRecordingPcm(id: String, sampleRate: Int, pcmBytes: ByteArray, encryptionKey: String?) = TODO()
        override fun deleteRecording(id: String) = TODO()
        override fun deleteRecordingFromCache(id: String) = TODO()
        override fun recordingExists(id: String): Boolean = TODO()
        override suspend fun deleteAllCachedMetadata() = TODO()
        override fun clearCacheDirectory() = TODO()
        override suspend fun deleteFromFirebaseStorage(id: String) = TODO()
        override suspend fun uploadStagedRecording(upload: StagedRecordingUpload) = TODO()
        override fun deleteStagedRecording(id: String) = TODO()
    }

    private val audioA = FakeAudio(Random(1).nextBytes(200 * 1024), 16000)
    private val audioAOriginal = FakeAudio(Random(2).nextBytes(1000), 48000)
    private val audioBOriginal = FakeAudio(Random(3).nextBytes(500), 48000)
    private val storage = FakeStorage(
        mapOf(
            "a" to audioA,
            "a-original" to audioAOriginal,
            "b-original" to audioBOriginal,
        )
    )

    private fun doc(vararg fileNames: String?) = RecordingDocument(
        timestamp = Instant.fromEpochMilliseconds(1_700_000_000_000),
        updated = 1_700_000_000_000,
        entries = fileNames.map { RecordingEntry(timestamp = Instant.fromEpochMilliseconds(1_700_000_000_000), fileName = it) },
    )

    @BeforeTest
    fun setUp() {
        SystemFileSystem.createDirectories(dir)
    }

    @AfterTest
    fun tearDown() {
        SystemFileSystem.list(dir).forEach { SystemFileSystem.delete(it, false) }
        SystemFileSystem.delete(dir, false)
    }

    @Test
    fun exportsDocumentsAudioAndManifest() = runTest {
        val docA = doc("a")
        val docB = doc("b", null)
        val docC = doc()
        val progress = mutableListOf<Int>()

        val result = BackupExporter(storage).export(
            zipPath = zipPath,
            userId = "uid",
            email = "user@example.com",
            recordings = flowOf(BackupRecording("A", docA), BackupRecording("B", docB), BackupRecording("C", docC)),
            onProgress = { progress += it },
        )

        assertEquals(BackupExporter.Result(recordings = 3, audioFiles = 3), result)
        assertEquals(listOf(1, 2, 3), progress)

        val reader = BackupZipReader(zipPath)
        reader.open()
        try {
            val names = reader.entryNames
            val manifest = BackupFormat.json.decodeFromString(BackupManifest.serializer(), reader.readBytes(BackupFormat.MANIFEST_ENTRY).decodeToString())
            assertEquals(3, manifest.recordingCount)
            assertEquals("uid", manifest.userId)

            assertEquals(docA, BackupFormat.json.decodeFromString(RecordingDocument.serializer(), reader.readBytes(BackupFormat.documentEntry("A")).decodeToString()))
            assertEquals(docC, BackupFormat.json.decodeFromString(RecordingDocument.serializer(), reader.readBytes(BackupFormat.documentEntry("C")).decodeToString()))

            assertContentEquals(audioA.bytes, reader.readBytes(BackupFormat.audioEntry("A", "a")))
            assertContentEquals(audioAOriginal.bytes, reader.readBytes(BackupFormat.audioEntry("A", "a-original")))
            assertEquals(BackupAudioMeta(48000, "audio/raw"), BackupFormat.decodeAudioMeta(reader.readBytes(BackupFormat.audioMetaEntry("A", "a-original"))))

            // Processed "b" is missing in storage: the original must not be duplicated under its name
            assertFalse(BackupFormat.audioEntry("B", "b") in names)
            assertContentEquals(audioBOriginal.bytes, reader.readBytes(BackupFormat.audioEntry("B", "b-original")))
        } finally {
            reader.close()
        }
    }

    @Test
    fun replacesExistingZip() = runTest {
        SystemFileSystem.sink(zipPath).buffered().use { it.write(ByteArray(10)) }

        BackupExporter(storage).export(zipPath, "uid", "e", flowOf(BackupRecording("A", doc("a"))))

        val reader = BackupZipReader(zipPath)
        reader.open()
        try {
            assertTrue(BackupFormat.documentEntry("A") in reader.entryNames)
        } finally {
            reader.close()
        }
    }
}
