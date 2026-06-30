package coredevices.ring.bugreport

import coredevices.indexai.data.entity.ConversationMessageDocument
import coredevices.indexai.data.entity.ConversationMessageEntity
import coredevices.indexai.data.entity.LocalRecording
import coredevices.indexai.data.entity.MessageRole
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.data.entity.RecordingEntryStatus
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class RecentRecordingsExportTest {

    @Test
    fun serializesRecordingEntriesAndMessagesRoundTrip() {
        val export = RecentRecordingExport(
            recording = LocalRecording(
                id = 7,
                localTimestamp = Instant.fromEpochMilliseconds(1_000),
                firestoreId = "fs-7",
                updated = Instant.fromEpochMilliseconds(2_000),
                assistantTitle = "Title",
            ),
            entries = listOf(
                RecordingEntryEntity(
                    id = 1,
                    recordingId = 7,
                    timestamp = Instant.fromEpochMilliseconds(1_500),
                    fileName = "audio-a",
                    status = RecordingEntryStatus.completed,
                    transcription = "hello",
                    transcribedUsingModel = "parakeet",
                    userMessageId = 42,
                )
            ),
            messages = listOf(
                ConversationMessageEntity(
                    id = 1,
                    recordingId = 7,
                    timestamp = Instant.fromEpochMilliseconds(1_600),
                    document = ConversationMessageDocument(role = MessageRole.user, content = "hello"),
                )
            ),
        )

        // Round-trips cleanly through the same encoder the export uses.
        val json = Json.encodeToString(listOf(export))
        val decoded = Json.decodeFromString<List<RecentRecordingExport>>(json).single()

        assertEquals(7, decoded.recording.id)
        assertEquals(Instant.fromEpochMilliseconds(1_000), decoded.recording.localTimestamp)
        assertEquals("fs-7", decoded.recording.firestoreId)

        val entry = decoded.entries.single()
        assertEquals("audio-a", entry.fileName)
        assertEquals(RecordingEntryStatus.completed, entry.status)
        assertEquals("hello", entry.transcription)
        assertEquals(42, entry.userMessageId)

        val message = decoded.messages.single()
        assertEquals(MessageRole.user, message.document.role)
        assertEquals("hello", message.document.content)
    }
}
