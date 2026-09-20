package coredevices.ring.service.indexfeed

import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_SHOPPING_ID
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultChildKindTest {

    @Test
    fun noteListKeepsNotes() {
        assertEquals("note", defaultChildKind("list-1", "note", "note"))
    }

    @Test
    fun checklistListUpgradesNotesToChecklist() {
        assertEquals("checklist", defaultChildKind("list-1", "checklist", "note"))
    }

    @Test
    fun shoppingListIsAlwaysChecklist() {
        assertEquals("checklist", defaultChildKind(LIST_SHOPPING_ID, "note", "note"))
    }

    @Test
    fun explicitNonNoteKindIsLeftAlone() {
        assertEquals("reminder", defaultChildKind("list-1", "checklist", "reminder"))
    }
}
