package coredevices.ring.reminders

import coredevices.indexai.data.entity.ItemDocument.ItemMetadata
import coredevices.ring.data.entity.room.indexfeed.CachedItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class UpcomingRemindersTest {

    private val reminder = ItemMetadata.Reminder(repeat = "", notification = "")
    private val now = Instant.fromEpochMilliseconds(1_500)

    private fun item(
        id: String,
        dueAt: Long? = null,
        metadata: ItemMetadata = reminder,
        done: Boolean = false,
        deleted: Boolean = false,
    ) = CachedItem(
        firestoreId = id,
        dueAt = dueAt?.let { Instant.fromEpochMilliseconds(it) },
        metadata = metadata,
        done = done,
        deleted = deleted,
    )

    @Test
    fun keepsOpenRemindersInTodosOrder() {
        val day = 24 * 60 * 60 * 1000L
        val items = listOf(
            item("undated"),
            item("later", dueAt = 1_500 + 2 * day),
            item("overdue-old", dueAt = 500),
            item("overdue", dueAt = 1_000),
            item("note", dueAt = 2_000, metadata = ItemMetadata.Note),
            item("done", dueAt = 2_000, done = true),
            item("deleted", dueAt = 2_000, deleted = true),
            item("soon-2", dueAt = 3_000),
            item("soon", dueAt = 2_000),
        )
        assertEquals(
            listOf("soon", "soon-2", "overdue", "overdue-old", "undated", "later"),
            upcomingReminders(items, now = now).map { it.firestoreId },
        )
    }

    @Test
    fun keepsLingeringDoneReminders() {
        val items = listOf(item("a", dueAt = 2_000, done = true), item("b", dueAt = 2_000, done = true))
        assertEquals(listOf("a"), upcomingReminders(items, lingeringIds = setOf("a"), now = now).map { it.firestoreId })
    }

    @Test
    fun capsAtLimit() {
        val items = (1..5).map { item("r$it", dueAt = 2_000L + it) }
        assertEquals(listOf("r1", "r2"), upcomingReminders(items, now = now, limit = 2).map { it.firestoreId })
    }
}
