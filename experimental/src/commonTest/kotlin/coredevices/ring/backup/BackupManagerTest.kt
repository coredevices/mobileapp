package coredevices.ring.backup

import coredevices.indexai.data.entity.RecordingDocument
import coredevices.indexai.data.entity.RecordingEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class BackupManagerTest {
    @Test
    fun audioFileIdsCoverBothVariantsAndSkipEntriesWithoutFile() {
        val ts = Instant.fromEpochMilliseconds(0)
        val document = RecordingDocument(
            timestamp = ts,
            updated = 0,
            entries = listOf(
                RecordingEntry(timestamp = ts, fileName = "a"),
                RecordingEntry(timestamp = ts, fileName = null),
                RecordingEntry(timestamp = ts, fileName = "b"),
            ),
        )

        assertEquals(listOf("a", "a-original", "b", "b-original"), BackupManager.audioFileIds(document))
    }
}
