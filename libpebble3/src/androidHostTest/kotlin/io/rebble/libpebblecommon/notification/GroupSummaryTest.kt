package io.rebble.libpebblecommon.notification

import io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification.ActiveNotificationInfo
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification.summaryStillNeeded
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupSummaryTest {

    private val summaryKey = "0|com.example.app|1|null|10"
    private val groupKey = "0|com.example.app|g:messages"

    private val summary = ActiveNotificationInfo(summaryKey, groupKey, isGroupSummary = true)
    private val child = ActiveNotificationInfo("0|com.example.app|2|null|10", groupKey, isGroupSummary = false)
    private val otherGroupChild =
        ActiveNotificationInfo("0|com.other.app|3|null|10", "0|com.other.app|g:other", isGroupSummary = false)

    private fun check(active: List<ActiveNotificationInfo>?) =
        summaryStillNeeded(summaryKey, groupKey, active)

    @Test
    fun sentWhenSummaryIsAloneInGroup() {
        assertTrue(check(listOf(summary, otherGroupChild)))
    }

    @Test
    fun notSentWhenGroupHasChild() {
        assertFalse(check(listOf(summary, child)))
    }

    @Test
    fun notSentWhenSummaryNoLongerActive() {
        assertFalse(check(listOf(otherGroupChild)))
    }

    @Test
    fun notSentWhenActiveNotificationsUnavailable() {
        assertFalse(check(null))
    }
}
