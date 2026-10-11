package coredevices.ring.ui.screens.settings

import io.rebble.libpebblecommon.database.entity.CalendarEntity
import kotlin.test.Test
import kotlin.test.assertEquals

class TargetCalendarDialogTest {
    private fun calendar(id: Int, name: String, writable: Boolean) = CalendarEntity(
        id = id,
        platformId = id.toString(),
        name = name,
        ownerName = "owner",
        ownerId = "owner",
        color = 0,
        enabled = true,
        writable = writable,
    )

    @Test
    fun writableCalendarsComeFirstThenAlphabetical() {
        val ordered = orderCalendarsForPicker(
            listOf(
                calendar(1, "Holidays", writable = false),
                calendar(2, "work", writable = true),
                calendar(3, "Birthdays", writable = false),
                calendar(4, "Family", writable = true),
            )
        )
        assertEquals(listOf(4, 2, 3, 1), ordered.map { it.id })
    }
}
