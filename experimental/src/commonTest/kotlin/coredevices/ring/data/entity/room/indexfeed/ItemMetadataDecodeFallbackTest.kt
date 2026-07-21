package coredevices.ring.data.entity.room.indexfeed

import coredevices.indexai.data.entity.ItemDocument
import coredevices.indexai.util.JsonSnake
import kotlin.test.Test
import kotlin.test.assertEquals

class ItemMetadataDecodeFallbackTest {

    @Test
    fun unknownMetadataTypeDecodesAsNote() {
        // Items written before the internal calendar_event kind was removed (or by a newer app
        // version with kinds this build doesn't know) must still decode instead of failing sync.
        val legacy = """
            {
                "title": "Lunch with Sam",
                "metadata": {
                    "type": "calendar_event",
                    "start_time": [1766000000, 0],
                    "end_time": [1766003600, 0],
                    "location": "Cafe"
                }
            }
        """.trimIndent()
        val doc = JsonSnake.decodeFromString<ItemDocument>(legacy)
        assertEquals(ItemDocument.ItemMetadata.Note, doc.metadata)
        assertEquals("Lunch with Sam", doc.title)
    }

    @Test
    fun knownMetadataTypesStillDecodeNormally() {
        val reminder = """
            {
                "title": "Call mum",
                "metadata": {"type": "reminder", "repeat": "one_time", "notification": "push"}
            }
        """.trimIndent()
        val doc = JsonSnake.decodeFromString<ItemDocument>(reminder)
        assertEquals(
            ItemDocument.ItemMetadata.Reminder(repeat = "one_time", notification = "push"),
            doc.metadata
        )
    }
}
