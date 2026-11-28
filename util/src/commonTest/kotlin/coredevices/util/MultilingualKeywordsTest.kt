package coredevices.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultilingualKeywordsTest {

    private val keywords = MultilingualKeywords(
        listOf(
            LocalizedKeywords(listOf("calendar", "event"), KeywordMatch.WholeWord),
            LocalizedKeywords(listOf("カレンダー"), KeywordMatch.Substring),
        )
    )

    @Test
    fun wholeWordMatchesCaseInsensitively() {
        assertTrue(keywords.matches("Add it to my CALENDAR"))
        assertTrue(keywords.matches("create an event"))
    }

    @Test
    fun wholeWordIgnoresWordsContainingKeyword() {
        assertFalse(keywords.matches("I'll eventually get to it"))
    }

    @Test
    fun substringMatchesWithoutWordBoundaries() {
        assertTrue(keywords.matches("金曜の15時にカレンダーに歯医者を入れて"))
    }

    @Test
    fun matchesAcrossLanguages() {
        assertTrue(keywords.matches("カレンダーにmeetingを入れて"))
        assertTrue(keywords.matches("明日の event を登録"))
    }

    @Test
    fun noMatchWhenNoKeywordPresent() {
        assertFalse(keywords.matches("schedule a meeting with Sam"))
        assertFalse(keywords.matches("明日の15時に会議をスケジュールして"))
    }

    @Test
    fun nullOrBlankNeverMatches() {
        assertFalse(keywords.matches(null))
        assertFalse(keywords.matches(""))
        assertFalse(keywords.matches("   "))
    }

    @Test
    fun keywordsAreMatchedLiterally() {
        val dotted = MultilingualKeywords(listOf(LocalizedKeywords(listOf("a.b"), KeywordMatch.Substring)))
        assertTrue(dotted.matches("x a.b y"))
        assertFalse(dotted.matches("x aXb y"))
    }

    @Test
    fun promptListQuotesEveryKeywordInOrder() {
        assertEquals("'calendar', 'event', 'カレンダー'", keywords.promptList())
    }
}
