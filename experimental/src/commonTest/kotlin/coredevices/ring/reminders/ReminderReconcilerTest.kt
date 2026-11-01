@file:OptIn(ExperimentalTime::class)

package coredevices.ring.reminders

import coredevices.ring.data.entity.room.reminders.LocalReminderData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Decision-table for [computeActions]: which reminder items get an alarm armed, rescheduled,
 * disarmed, or left alone, given this device's current alarm ledger. Covers the cross-device cases
 * the reconciler exists for (new phone / web app / a second device).
 */
class ReminderReconcilerTest {

    private val now = Instant.fromEpochMilliseconds(1_000_000)
    private val future = Instant.fromEpochMilliseconds(2_000_000)
    private val past = Instant.fromEpochMilliseconds(500_000)

    private fun view(
        itemId: String,
        dueAt: Instant? = future,
        notifyBeforeMillis: Long? = null,
        done: Boolean = false,
        deleted: Boolean = false,
        message: String = "msg",
    ) = ReminderView(itemId, message, dueAt, notifyBeforeMillis, done, deleted)

    private fun row(itemId: String, time: Instant? = future, notifyBeforeMillis: Long? = null, message: String = "msg") =
        LocalReminderData(itemId, time, message, notifyBeforeMillis)

    /** Active-item ids default to every non-deleted reminder view. */
    private fun actions(
        reminders: List<ReminderView>,
        rows: List<LocalReminderData>,
        activeItemIds: Set<String> = reminders.filterNot { it.deleted }.map { it.itemId }.toSet(),
    ) = computeActions(reminders, activeItemIds, rows, now)

    @Test
    fun armsActiveFutureReminderWithNoRow() {
        assertEquals(
            listOf(ReminderAction.Arm("a", "msg", future, null)),
            actions(listOf(view("a")), rows = emptyList()),
        )
    }

    @Test
    fun leavesActiveReminderWhoseRowMatches() {
        assertEquals(emptyList(), actions(listOf(view("a")), rows = listOf(row("a"))))
    }

    @Test
    fun reArmsWhenRowTimeIsStale() {
        assertEquals(
            listOf(ReminderAction.Arm("a", "msg", future, null)),
            actions(listOf(view("a", dueAt = future)), rows = listOf(row("a", time = past))),
        )
    }

    @Test
    fun reArmsWhenLeadTimeChanged() {
        assertEquals(
            listOf(ReminderAction.Arm("a", "msg", future, 60_000)),
            actions(listOf(view("a", notifyBeforeMillis = 60_000)), rows = listOf(row("a", notifyBeforeMillis = null))),
        )
    }

    @Test
    fun reArmsWhenMessageChanged() {
        assertEquals(
            listOf(ReminderAction.Arm("a", "new", future, null)),
            actions(listOf(view("a", message = "new")), rows = listOf(row("a", message = "old"))),
        )
    }

    @Test
    fun leavesPastDueActiveReminderWithMatchingRow() {
        // Its notification may already be showing; never disarm and never re-fire.
        assertEquals(
            emptyList(),
            actions(listOf(view("a", dueAt = past)), rows = listOf(row("a", time = past))),
        )
    }

    @Test
    fun ignoresPastDueReminderWithNoRow() {
        assertEquals(emptyList(), actions(listOf(view("a", dueAt = past)), rows = emptyList()))
    }

    @Test
    fun cancelsStaleFutureAlarmWhenDueCleared() {
        // Due date cleared on another device (synced via pull). The future alarm must be canceled.
        assertEquals(
            listOf(ReminderAction.Arm("a", "msg", null, null)),
            actions(listOf(view("a", dueAt = null)), rows = listOf(row("a", time = future))),
        )
    }

    @Test
    fun reschedulesWhenDueMovedToPast() {
        assertEquals(
            listOf(ReminderAction.Arm("a", "msg", past, null)),
            actions(listOf(view("a", dueAt = past)), rows = listOf(row("a", time = future))),
        )
    }

    @Test
    fun disarmsCompletedReminder() {
        assertEquals(
            listOf(ReminderAction.Disarm("a")),
            actions(listOf(view("a", done = true)), rows = listOf(row("a")), activeItemIds = setOf("a")),
        )
    }

    @Test
    fun disarmsDeletedReminder() {
        assertEquals(
            listOf(ReminderAction.Disarm("a")),
            actions(listOf(view("a", deleted = true)), rows = listOf(row("a")), activeItemIds = emptySet()),
        )
    }

    @Test
    fun disarmsRowWhoseItemIsGone() {
        assertEquals(
            listOf(ReminderAction.Disarm("a")),
            actions(reminders = emptyList(), rows = listOf(row("a")), activeItemIds = emptySet()),
        )
    }

    @Test
    fun leavesListNoteAlarmWhoseItemStillExists() {
        // A list note ("add milk, remind me at 5pm") has a local alarm but no reminder metadata, so
        // it isn't a reminder view. Its item still exists, so the row must not be disarmed.
        assertEquals(
            emptyList(),
            actions(reminders = emptyList(), rows = listOf(row("a")), activeItemIds = setOf("a")),
        )
    }

    @Test
    fun disarmsListNoteAlarmWhenNoteDeleted() {
        assertEquals(
            listOf(ReminderAction.Disarm("a")),
            actions(reminders = emptyList(), rows = listOf(row("a")), activeItemIds = emptySet()),
        )
    }
}
