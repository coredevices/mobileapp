package io.rebble.libpebblecommon.pebblekit.two

import io.rebble.pebblekit2.common.model.TimelineLayout
import io.rebble.pebblekit2.common.model.TimelineLayoutType
import io.rebble.pebblekit2.common.model.TimelinePin
import io.rebble.pebblekit2.common.model.TimelineReminder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class TimelinePinConversionTest {
    private val due = Instant.parse("2026-11-01T12:00:00Z")
    private val pin = TimelinePin(
        id = "task-42",
        startTime = due,
        layout = TimelineLayout(
            type = TimelineLayoutType.GENERIC_PIN,
            title = "Buy milk",
        ),
    )

    @Test
    fun preservesAdvanceAndDueReminders() {
        val source = pin.copy(
            reminders = listOf(
                reminder(due - 30.minutes, "In 30 minutes"),
                reminder(due, "Due now"),
            ),
        )

        val converted = source.toPinJson()
        val reminders = converted.reminders.orEmpty()
        assertEquals(2, reminders.size)
        assertEquals(listOf(due - 30.minutes, due), reminders.map { it.time })
        assertEquals(listOf("In 30 minutes", "Due now"), reminders.map { it.layout.title })
        reminders.forEach {
            assertEquals("genericReminder", it.layout.type)
            assertEquals("Buy milk", it.layout.body)
            assertEquals("system://images/NOTIFICATION_REMINDER", it.layout.tinyIcon)
        }
        assertEquals(pin.id, converted.id)
        assertEquals(due, converted.time)
        assertEquals("genericPin", converted.layout.type)
        assertEquals("Buy milk", converted.layout.title)
    }

    @Test
    fun clearingRemindersKeepsThePin() {
        val source = pin.copy(reminders = listOf(reminder(due, "Due now")))
        assertEquals(1, source.toPinJson().reminders.orEmpty().size)

        val converted = source.copy(reminders = emptyList()).toPinJson()
        assertTrue(converted.reminders.orEmpty().isEmpty())
        assertEquals(pin.id, converted.id)
        assertEquals(pin.startTime, converted.time)
        assertEquals(pin.layout.title, converted.layout.title)
    }

    private fun reminder(time: Instant, title: String) = TimelineReminder(
        time = time,
        layout = TimelineLayout(
            type = TimelineLayoutType.GENERIC_REMINDER,
            title = title,
            body = "Buy milk",
            tinyIcon = "system://images/NOTIFICATION_REMINDER",
        ),
    )
}
