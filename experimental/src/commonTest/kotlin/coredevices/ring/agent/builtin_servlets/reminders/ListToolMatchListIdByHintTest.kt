@file:OptIn(ExperimentalTime::class)

package coredevices.ring.agent.builtin_servlets.reminders

import coredevices.ring.agent.builtin_servlets.reminders.ListTool.Companion.matchListIdByHint
import coredevices.ring.data.entity.room.indexfeed.CachedList
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_NOTES_SELF_ID
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_SHOPPING_ID
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_TODOS_ID
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.SEED_NOTES_SELF
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.SEED_SHOPPING
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.SEED_TODOS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime

class ListToolMatchListIdByHintTest {

    private val seededLists = listOf(
        CachedList(firestoreId = LIST_NOTES_SELF_ID, title = "Notes to self", seed = SEED_NOTES_SELF),
        // The Todos list is renamed to "Reminders" but keeps its "todos" seed.
        CachedList(firestoreId = LIST_TODOS_ID, title = "Reminders", seed = SEED_TODOS),
        CachedList(firestoreId = LIST_SHOPPING_ID, title = "Shopping list", seed = SEED_SHOPPING),
    )

    @Test
    fun todoHintResolvesToRenamedRemindersListViaSeed() {
        // The model is still prompted to use 'todo'; the renamed title no longer
        // contains it, so resolution must fall back to the unchanged seed.
        assertEquals(LIST_TODOS_ID, matchListIdByHint(seededLists, "todo"))
    }

    @Test
    fun reminderHintResolvesToRenamedRemindersListViaTitle() {
        assertEquals(LIST_TODOS_ID, matchListIdByHint(seededLists, "reminder"))
    }

    @Test
    fun shoppingHintResolvesViaTitle() {
        assertEquals(LIST_SHOPPING_ID, matchListIdByHint(seededLists, "shopping"))
    }

    @Test
    fun titleMatchIsPreferredOverSeedMatch() {
        // A user-created list whose title contains the hint should win over a
        // built-in list that only matches on seed.
        val lists = seededLists + CachedList(firestoreId = "custom", title = "My todo backlog", seed = null)
        assertEquals("custom", matchListIdByHint(lists, "todo"))
    }

    @Test
    fun unknownHintResolvesToNull() {
        assertNull(matchListIdByHint(seededLists, "groceries for the week"))
    }

    @Test
    fun blankHintResolvesToNull() {
        assertNull(matchListIdByHint(seededLists, "   "))
    }
}
