package io.rebble.libpebblecommon.music

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class MatchesTruncatedTest {
    @Test
    fun matchesWatchSideTruncation() {
        assertTrue(matchesTruncated("Mrs. Robinson", "Mrs. Rob"))
        assertTrue(matchesTruncated("Mrs. Robinson", "Mrs. Robinson"))
        assertFalse(matchesTruncated("Mrs. Robinson", "The Boxer"))
        assertFalse(matchesTruncated(null, "Mrs. Robinson"))
    }

    @Test
    fun ignoresCharacterSplitByTheByteTruncation() {
        // The firmware cuts at a byte count, so the last character can come back as U+FFFD.
        assertTrue(matchesTruncated("君の名は。", "君の名\uFFFD"))
        assertFalse(matchesTruncated("君の名は。", "その名\uFFFD"))
    }
}
