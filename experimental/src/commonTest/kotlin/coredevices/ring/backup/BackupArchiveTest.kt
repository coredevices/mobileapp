package coredevices.ring.backup

import coredevices.indexai.data.entity.RecordingDocument
import coredevices.indexai.data.entity.RecordingEntry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class BackupArchiveTest {
    private class MapSource(private val entries: Map<String, ByteArray>) : BackupEntrySource {
        override val entryNames: Set<String> get() = entries.keys
        override suspend fun readBytes(name: String): ByteArray = entries.getValue(name)
    }

    private val ts = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun doc(vararg fileNames: String?) = RecordingDocument(
        timestamp = ts,
        updated = 1_700_000_000_000,
        entries = fileNames.map { RecordingEntry(timestamp = ts, fileName = it) },
    )

    private fun docBytes(doc: RecordingDocument) =
        BackupFormat.json.encodeToString(RecordingDocument.serializer(), doc).encodeToByteArray()

    private fun metaBytes(meta: BackupAudioMeta) =
        BackupFormat.json.encodeToString(BackupAudioMeta.serializer(), meta).encodeToByteArray()

    @Test
    fun readsDocumentsAndPresentAudioVariants() = runTest {
        val docA = doc("a")
        val source = MapSource(
            mapOf(
                BackupFormat.MANIFEST_ENTRY to ByteArray(0),
                BackupFormat.documentEntry("A") to docBytes(docA),
                BackupFormat.audioEntry("A", "a") to ByteArray(4),
                BackupFormat.audioEntry("A", "a-original") to ByteArray(4),
                BackupFormat.audioMetaEntry("A", "a-original") to metaBytes(BackupAudioMeta(48000, "audio/raw")),
            )
        )

        val recordings = BackupArchive.readRecordings(source)

        assertEquals(1, recordings.size)
        val recording = recordings.single()
        assertEquals("A", recording.firestoreId)
        assertEquals(docA, recording.document)
        assertEquals(listOf("a", "a-original"), recording.audioFiles.map { it.variant })
        assertEquals(listOf(BackupFormat.audioEntry("A", "a"), BackupFormat.audioEntry("A", "a-original")), recording.audioFiles.map { it.entryName })
        // Missing meta falls back to the default 16kHz raw
        assertEquals(16000, recording.audioFiles[0].sampleRate)
        assertEquals(48000, recording.audioFiles[1].sampleRate)
    }

    @Test
    fun skipsAudioNotInArchiveAndEntriesWithoutFileName() = runTest {
        val source = MapSource(
            mapOf(
                BackupFormat.documentEntry("B") to docBytes(doc("b", null)),
                BackupFormat.audioEntry("B", "b-original") to ByteArray(4),
            )
        )

        val recording = BackupArchive.readRecordings(source).single()

        assertEquals(listOf("b-original"), recording.audioFiles.map { it.variant })
    }

    @Test
    fun ignoresRecordingsWithoutDocumentOrWithMalformedDocument() = runTest {
        val source = MapSource(
            mapOf(
                BackupFormat.audioEntry("orphan", "x") to ByteArray(4),
                BackupFormat.documentEntry("bad") to "not json".encodeToByteArray(),
                BackupFormat.documentEntry("good") to docBytes(doc()),
            )
        )

        val recordings = BackupArchive.readRecordings(source)

        assertEquals(listOf("good"), recordings.map { it.firestoreId })
        assertTrue(recordings.single().audioFiles.isEmpty())
    }
}
