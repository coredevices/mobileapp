package coredevices.ring.agent.integrations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderListFuzzyFilterTest {

    private fun entries(vararg titles: String) = titles.mapIndexed { i, t -> ReminderListEntry("$i", t) }

    @Test
    fun ordersExactThenPrefixThenWordStart() {
        val result = entries("My todo backlog", "Todos", "Todo").fuzzyFilter("todo")
        assertEquals(listOf("Todo", "Todos", "My todo backlog"), result.map { it.title })
    }

    @Test
    fun shorterTitleWinsWhenScoresTie() {
        val result = entries("Work stuff and things", "Work stuff").fuzzyFilter("work")
        assertEquals(listOf("Work stuff", "Work stuff and things"), result.map { it.title })
    }

    @Test
    fun midWordMatchesAreDiscarded() {
        assertTrue(entries("Shopping").fuzzyFilter("hopping").isEmpty())
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertEquals("GROCERIES", entries("GROCERIES").fuzzyFilter("groceries").single().title)
    }

    @Test
    fun theWordListIsIgnoredOnBothSides() {
        assertEquals("Shopping list", entries("Shopping list").fuzzyFilter("list shopping").single().title)
    }

    @Test
    fun unrelatedQueryMatchesNothing() {
        assertTrue(entries("Notes to self", "Shopping").fuzzyFilter("groceries").isEmpty())
    }

    @Test
    fun queryLongerThanTheTitleStillMatchesIt() {
        val lists = entries("Grocery list", "Clients")
        assertEquals("Grocery list", lists.fuzzyFilter("my grocery list").single().title)
        assertEquals("Clients", lists.fuzzyFilter("potential clients").single().title)
    }

    @Test
    fun directMatchBeatsTitleContainedInTheQuery() {
        val result = entries("Clients", "Potential clients").fuzzyFilter("potential clients")
        assertEquals(listOf("Potential clients", "Clients"), result.map { it.title })
    }

    @Test
    fun longestContainedTitleWins() {
        val result = entries("Grocery", "Grocery and shopping").fuzzyFilter("my grocery and shopping list")
        assertEquals(listOf("Grocery and shopping", "Grocery"), result.map { it.title })
    }

    @Test
    fun containedTitleMustBeAWholePhrase() {
        // "Client" only appears inside "clients", so it isn't a match.
        assertTrue(entries("Client").fuzzyFilter("potential clients").isEmpty())
    }
}
