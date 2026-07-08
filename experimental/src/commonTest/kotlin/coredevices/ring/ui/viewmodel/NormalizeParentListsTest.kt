package coredevices.ring.ui.viewmodel

import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_NOTES_SELF_ID
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_TODOS_ID
import kotlin.test.Test
import kotlin.test.assertEquals

class NormalizeParentListsTest {

    @Test
    fun textOnlyEditKeepsCalendarEventInTodos() {
        // requestedParents = null (text-only edit) must preserve membership,
        // not strip Todos and relocate to Notes-to-Self.
        assertEquals(
            listOf(LIST_TODOS_ID),
            normalizeParentLists(
                kind = "calendar_event",
                requestedParents = null,
                currentParents = listOf(LIST_TODOS_ID),
            ),
        )
    }

    @Test
    fun textOnlyEditPreservesEmptyMembership() {
        // message / action_log / answer items are intentionally list-less.
        assertEquals(
            emptyList(),
            normalizeParentLists(
                kind = "answer",
                requestedParents = null,
                currentParents = emptyList(),
            ),
        )
    }

    @Test
    fun textOnlyEditPreservesCustomListMembership() {
        assertEquals(
            listOf("list_custom"),
            normalizeParentLists(
                kind = "note",
                requestedParents = null,
                currentParents = listOf("list_custom"),
            ),
        )
    }

    @Test
    fun reminderAlwaysForcedToTodos() {
        assertEquals(
            listOf(LIST_TODOS_ID),
            normalizeParentLists(
                kind = "reminder",
                requestedParents = null,
                currentParents = listOf("list_custom"),
            ),
        )
    }

    @Test
    fun scheduledEditKeepsItemOutOfReminders() {
        // Timer/alarm items are owned by the system clock app; an edit must not
        // re-parent them into the Reminders list.
        assertEquals(
            emptyList(),
            normalizeParentLists(
                kind = "scheduled",
                requestedParents = null,
                currentParents = emptyList(),
            ),
        )
    }

    @Test
    fun explicitDeselectAllFallsBackToNotes() {
        assertEquals(
            listOf(LIST_NOTES_SELF_ID),
            normalizeParentLists(
                kind = "note",
                requestedParents = emptyList(),
                currentParents = listOf(LIST_NOTES_SELF_ID),
            ),
        )
    }

    @Test
    fun explicitRequestStripsTodosAndDedupes() {
        assertEquals(
            listOf("list_custom"),
            normalizeParentLists(
                kind = "note",
                requestedParents = listOf(LIST_TODOS_ID, "list_custom", "list_custom"),
                currentParents = listOf(LIST_TODOS_ID),
            ),
        )
    }
}