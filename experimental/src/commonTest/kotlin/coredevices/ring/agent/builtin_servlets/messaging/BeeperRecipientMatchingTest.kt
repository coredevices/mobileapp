package coredevices.ring.agent.builtin_servlets.messaging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class BeeperRecipientMatchingTest {

    @Test
    fun matchLevels() {
        val cases = listOf(
            Triple("taz family", "🌴 Taz Family!", NameMatch.Exact),
            Triple("Liz", "Liz", NameMatch.Exact),
            Triple("alvin and bob", "Alvin & Bob", NameMatch.Exact),
            Triple("alvin and bob", "Alvin Lacson, Bob Smith", NameMatch.WholeWords),
            Triple("alvin and bob", "Alvin Lacson", NameMatch.None),
            Triple("liz", "Elizabeth 'Liz' Migicovsky", NameMatch.WholeWords),
            Triple("liz", "~Liz (work)", NameMatch.WholeWords),
            Triple("family taz", "Taz Family", NameMatch.WholeWords),
            Triple("meg", "Megan", NameMatch.WordPrefix),
            Triple("liz mig", "Liz Migicovsky", NameMatch.WordPrefix),
            Triple("al alex", "Alex Albert", NameMatch.WordPrefix),
            Triple("al al", "Alex", NameMatch.None),
            Triple("eliz", "Elizabeth 'Liz' Migicovsky", NameMatch.WordPrefix),
            Triple("mcdonald", "Mc Donald", NameMatch.JoinedPrefix),
            Triple("lizm", "liz.m", NameMatch.JoinedPrefix),
            Triple("an", "Megan", NameMatch.None),
            Triple("gan", "Megan", NameMatch.None),
            Triple("taz", "Book Club", NameMatch.None),
            Triple("", "Megan", NameMatch.None),
            Triple("liz", "🌴", NameMatch.None),
        )
        for ((query, name, expected) in cases) {
            assertEquals(expected, nameMatch(query, name), "'$query' vs '$name'")
        }
    }

    @Test
    fun bestOfSeveralNames() {
        assertEquals(NameMatch.Exact, nameMatch("liz", listOf("Elizabeth M", null, "Liz")))
    }

    @Test
    fun strongerMatchBeatsMoreRecentChat() {
        val exact = BeeperChatCandidate("!al", "Al", NameMatch.Exact, lastActivity = 100)
        val prefix = BeeperChatCandidate("!alice", "Alice", NameMatch.WordPrefix, lastActivity = 900)
        assertEquals(exact, listOf(exact, prefix).bestMatches().pick())
    }

    @Test
    fun mostRecentWinsWithinLevel() {
        val whatsapp = BeeperChatCandidate("!wa", "Liz", NameMatch.Exact, lastActivity = 100)
        val signal = BeeperChatCandidate("!sig", "Liz", NameMatch.Exact, lastActivity = 200)
        assertEquals(signal, listOf(whatsapp, signal).bestMatches().pick())
    }

    @Test
    fun directChatBeatsMoreRecentGroupChat() {
        val dm = BeeperChatCandidate("!dm", "Alvin Lacson", NameMatch.WholeWords, lastActivity = 100)
        val group = BeeperChatCandidate("!grp", "Alvin Lacson, +14153200891", NameMatch.WholeWords, lastActivity = 900, group = true)
        assertEquals(dm, listOf(group, dm).bestMatches().pick())
    }

    @Test
    fun groupChatWinsWhenNamedMoreClosely() {
        val dm = BeeperChatCandidate("!dm", "Alvin Lacson", nameMatch("alvin and bob", "Alvin Lacson"))
        val group = BeeperChatCandidate("!grp", "Alvin & Bob", nameMatch("alvin and bob", "Alvin & Bob"), group = true)
        assertEquals(group, listOf(dm, group).bestMatches().pick())
        assertEquals(group, listOf(group).bestMatches().pick())
    }

    @Test
    fun approvedSendsOnlyOnSingleWholeWordMatch() {
        val exact = BeeperChatCandidate("!a", "Liz", NameMatch.Exact, approved = true)
        assertEquals(exact, listOf(exact).pick())

        val prefix = exact.copy(match = NameMatch.WordPrefix)
        assertFalse(listOf(prefix).pick()!!.approved)

        val tie = listOf(exact, exact.copy(roomId = "!c", lastActivity = 5)).pick()!!
        assertEquals("!c", tie.roomId)
        assertFalse(tie.approved)
    }

    @Test
    fun noMatchPicksNothing() {
        assertNull(listOf(BeeperChatCandidate("!x", "Bob", NameMatch.None)).bestMatches().pick())
    }
}
