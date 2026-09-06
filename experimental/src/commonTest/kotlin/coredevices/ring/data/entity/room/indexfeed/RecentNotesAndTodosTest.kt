@file:OptIn(ExperimentalTime::class)

package coredevices.ring.data.entity.room.indexfeed

import coredevices.indexai.data.entity.ItemDocument.ItemMetadata
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_TODOS_ID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

class RecentNotesAndTodosTest {

    private fun item(
        id: String,
        createdAtMs: Long = 1_000,
        metadata: ItemMetadata = ItemMetadata.Note,
        lists: String = "",
        title: String = "t-$id",
        done: Boolean = false,
        deleted: Boolean = false,
        locked: Boolean = false,
        dueAt: Instant? = null,
    ) = CachedItem(
        firestoreId = id,
        createdAt = Instant.fromEpochMilliseconds(createdAtMs),
        updatedAt = Instant.fromEpochMilliseconds(createdAtMs),
        title = title,
        metadata = metadata,
        parentListIdsCsv = lists,
        done = done,
        deleted = deleted,
        locked = locked,
        dueAt = dueAt,
    )

    private val reminder = ItemMetadata.Reminder(repeat = "one_time", notification = "push")

    @Test
    fun excludesDeletedItems() {
        val rows = recentNotesAndTodos(listOf(item("a", deleted = true), item("b")))
        assertEquals(listOf("b"), rows.map { it.firestoreId })
    }

    @Test
    fun excludesLockedItems() {
        val rows = recentNotesAndTodos(listOf(item("a", locked = true), item("b")))
        assertEquals(listOf("b"), rows.map { it.firestoreId })
    }

    @Test
    fun excludesDoneItems() {
        val rows = recentNotesAndTodos(listOf(item("a", done = true), item("b")))
        assertEquals(listOf("b"), rows.map { it.firestoreId })
    }

    @Test
    fun includesNoteFiledUnderUserList() {
        val rows = recentNotesAndTodos(listOf(item("a", lists = "some_user_list")))
        assertEquals(listOf("a"), rows.map { it.firestoreId })
    }

    @Test
    fun includesTodoMemberRegardlessOfKind() {
        val rows = recentNotesAndTodos(listOf(item("a", metadata = reminder, lists = LIST_TODOS_ID)))
        assertEquals(listOf("a"), rows.map { it.firestoreId })
    }

    @Test
    fun excludesNonNoteKindsOutsideTodosList() {
        val answer = item("a", metadata = ItemMetadata.Answer(question = "q"))
        val reminderNotInTodos = item("b", metadata = reminder)
        val rows = recentNotesAndTodos(listOf(answer, reminderNotInTodos, item("c")))
        assertEquals(listOf("c"), rows.map { it.firestoreId })
    }

    @Test
    fun sortsNewestFirstThenByIdForTies() {
        val rows = recentNotesAndTodos(
            listOf(item("b", createdAtMs = 5), item("a", createdAtMs = 5), item("z", createdAtMs = 9)),
        )
        assertEquals(listOf("z", "a", "b"), rows.map { it.firestoreId })
    }

    @Test
    fun truncatesToLimit() {
        val rows = recentNotesAndTodos((1..7).map { item("i$it", createdAtMs = it.toLong()) }, limit = 5)
        assertEquals(5, rows.size)
    }

    @Test
    fun rejectsNonPositiveLimit() {
        assertFailsWith<IllegalArgumentException> { recentNotesAndTodos(listOf(item("a")), limit = 0) }
    }

    @Test
    fun fingerprintChangesWhenMembershipFlipsSubtitle() {
        val asNote = item("a")
        val asTodo = item("a", lists = LIST_TODOS_ID)
        assertNotEquals(widgetRenderFingerprint(listOf(asNote)), widgetRenderFingerprint(listOf(asTodo)))
    }

    @Test
    fun emptyTitleItemIsIncludedAndFingerprinted() {
        val untitled = item("a", title = "")
        assertEquals(listOf("a"), recentNotesAndTodos(listOf(untitled)).map { it.firestoreId })
        assertEquals(listOf(Triple("a", "", "Note")), widgetRenderFingerprint(listOf(untitled)))
    }

    // --- Banner widget: counts, row labels, relative time (all in UTC for determinism) ---

    private val utc = TimeZone.UTC
    private val now = Instant.parse("2026-09-06T10:00:00Z")

    @Test
    fun countsSplitTodosAndNotesAndSkipExcludedItems() {
        val items = listOf(
            item("t1", metadata = reminder, lists = LIST_TODOS_ID),
            item("n1"), item("n2", lists = "yu"),
            item("gone", deleted = true), item("done", done = true, lists = LIST_TODOS_ID),
        )
        assertEquals(WidgetCounts(todos = 1, notes = 2), widgetCounts(items))
    }

    @Test
    fun relativeTimeBuckets() {
        assertEquals("now", relativeTime(now - 30.seconds, now, utc))
        assertEquals("5 min", relativeTime(now - 5.minutes, now, utc))
        assertEquals("3 h", relativeTime(now - 3.hours, now, utc))
        assertEquals("Yesterday", relativeTime(Instant.parse("2026-09-05T23:00:00Z"), now, utc))
        assertEquals("1 Sep", relativeTime(Instant.parse("2026-09-01T09:00:00Z"), now, utc))
    }

    @Test
    fun todoLabelReflectsDueDate() {
        val lists = emptyMap<String, String>()
        assertEquals("To-do", widgetRowLabel(item("a", lists = LIST_TODOS_ID), lists, now, utc))
        assertEquals("Due today", widgetRowLabel(item("a", lists = LIST_TODOS_ID, dueAt = now + 2.hours), lists, now, utc))
        assertEquals("Due tomorrow", widgetRowLabel(item("a", lists = LIST_TODOS_ID, dueAt = now + 1.days), lists, now, utc))
        assertEquals("Due 15 Sep", widgetRowLabel(item("a", lists = LIST_TODOS_ID, dueAt = Instant.parse("2026-09-15T12:00:00Z")), lists, now, utc))
        assertEquals("Overdue", widgetRowLabel(item("a", lists = LIST_TODOS_ID, dueAt = now - 2.days), lists, now, utc))
    }

    @Test
    fun noteLabelIsParentListNameOrNote() {
        val lists = mapOf("yu" to "yu", "notes_self" to "Notes to self")
        assertEquals("yu", widgetRowLabel(item("a", lists = "yu"), lists, now, utc))
        assertEquals("Notes to self", widgetRowLabel(item("a", lists = "notes_self"), lists, now, utc))
        assertEquals("Note", widgetRowLabel(item("a"), lists, now, utc))
        assertEquals("Note", widgetRowLabel(item("a", lists = "unknown_list"), lists, now, utc))
    }

    @Test
    fun fingerprintIgnoresFieldsThatAreNotRendered() {
        val a = item("a", createdAtMs = 1)
        val b = item("a", createdAtMs = 2)
        assertEquals(widgetRenderFingerprint(listOf(a)), widgetRenderFingerprint(listOf(b)))
    }
}
