@file:OptIn(ExperimentalTime::class)

package coredevices.ring.agent.builtin_servlets.reminders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class TaskerReminderExtrasTest {
    private val deadline = Instant.parse("2026-09-02T14:00:00Z")

    @Test
    fun `a plain reminder carries no extras`() {
        assertEquals(
            emptyMap<String, String>(),
            taskerReminderExtras(deadline = null, listId = null, notifyBefore = null),
        )
    }

    @Test
    fun `a due date travels as a UTC ISO-8601 instant`() {
        assertEquals(
            mapOf("deadline" to "2026-09-02T14:00:00Z"),
            taskerReminderExtras(deadline, listId = null, notifyBefore = null),
        )
    }

    @Test
    fun `lead time travels as whole seconds alongside the due date`() {
        assertEquals(
            mapOf("deadline" to "2026-09-02T14:00:00Z", "notify_before_seconds" to "3600"),
            taskerReminderExtras(deadline, listId = null, notifyBefore = 1.hours),
        )
    }

    @Test
    fun `lead time without a due date is dropped`() {
        assertEquals(
            emptyMap<String, String>(),
            taskerReminderExtras(deadline = null, listId = null, notifyBefore = 1.hours),
        )
    }

    @Test
    fun `a list name travels independently of the due date`() {
        assertEquals(
            mapOf("list" to "Groceries"),
            taskerReminderExtras(deadline = null, listId = "Groceries", notifyBefore = null),
        )
    }
}
