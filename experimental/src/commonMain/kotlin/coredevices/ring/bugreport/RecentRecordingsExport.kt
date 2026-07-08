package coredevices.ring.bugreport

import coredevices.indexai.data.entity.ConversationMessageEntity
import coredevices.indexai.data.entity.LocalRecording
import coredevices.indexai.data.entity.RecordingEntryEntity
import kotlinx.serialization.Serializable

/**
 * A recording plus its associated data (entries + conversation messages) — the
 * data shown as screen context in `RecordingDetails` — bundled into a bug
 * report. References the Room entities directly (they are `@Serializable`) so
 * the export can't drift out of sync with the schema.
 */
@Serializable
data class RecentRecordingExport(
    val recording: LocalRecording,
    val entries: List<RecordingEntryEntity>,
    val messages: List<ConversationMessageEntity>,
)
