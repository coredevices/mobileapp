@file:OptIn(ExperimentalTime::class)

package coredevices.ring.data.entity.room.indexfeed

import coredevices.indexai.data.entity.ItemDocument.ItemMetadata
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_TODOS_ID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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
    fun subtitleIsNoteForPlainNote() {
        assertEquals("Note", widgetRowSubtitle(item("a")))
    }

    @Test
    fun subtitleIsTodoForTodoMemberWithoutDueDate() {
        assertEquals("To-do", widgetRowSubtitle(item("a", lists = LIST_TODOS_ID)))
    }

    @Test
    fun subtitleStartsWithDueForTodoMemberWithDueDate() {
        val subtitle = widgetRowSubtitle(item("a", lists = LIST_TODOS_ID, dueAt = Instant.fromEpochMilliseconds(0)))
        assertTrue(subtitle.startsWith("Due "), subtitle)
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

    @Test
    fun fingerprintIgnoresFieldsThatAreNotRendered() {
        val a = item("a", createdAtMs = 1)
        val b = item("a", createdAtMs = 2)
        assertEquals(widgetRenderFingerprint(listOf(a)), widgetRenderFingerprint(listOf(b)))
    }
}
