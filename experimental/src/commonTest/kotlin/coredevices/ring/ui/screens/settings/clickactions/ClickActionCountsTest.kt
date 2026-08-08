package coredevices.ring.ui.screens.settings.clickactions

import coredevices.ring.data.entity.room.ClickAction
import coredevices.ring.data.entity.room.ClickActionBinding
import coredevices.ring.database.MusicControlMode
import coredevices.ring.database.reservedClickCounts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClickActionCountsTest {

    private fun binding(id: Long, clickCount: Int) = ClickActionBinding(
        id = id,
        clickCount = clickCount,
        action = ClickAction.AgentText("test"),
    )

    private fun options(
        existing: List<ClickActionBinding> = emptyList(),
        reserved: Set<Int> = emptySet(),
        editingId: Long = 0L,
    ) = clickCountOptions(existing, reserved, editingId)

    private fun List<ClickCountOption>.counts(availability: ClickCountAvailability) =
        filter { it.availability == availability }.map { it.count }

    @Test
    fun `media control reserves the counts its gestures use`() {
        assertEquals(emptySet(), MusicControlMode.Disabled.reservedClickCounts())
        assertEquals(setOf(1, 2), MusicControlMode.SingleClick.reservedClickCounts())
        assertEquals(setOf(2, 3), MusicControlMode.DoubleClick.reservedClickCounts())
    }

    @Test
    fun `every count in range is offered`() {
        assertEquals((1..8).toList(), options().map { it.count })
    }

    @Test
    fun `all counts are available when nothing is bound or reserved`() {
        assertEquals((1..8).toList(), options().counts(ClickCountAvailability.Available))
    }

    @Test
    fun `media-reserved counts are shown but not selectable`() {
        val result = options(reserved = MusicControlMode.DoubleClick.reservedClickCounts())

        assertEquals(listOf(2, 3), result.counts(ClickCountAvailability.UsedByMediaControl))
        assertEquals(listOf(1, 4, 5, 6, 7, 8), result.counts(ClickCountAvailability.Available))
    }

    @Test
    fun `counts owned by another binding are shown but not selectable`() {
        val result = options(existing = listOf(binding(id = 1, clickCount = 3)))

        assertEquals(listOf(3), result.counts(ClickCountAvailability.UsedByAnotherAction))
        assertEquals(listOf(1, 2, 4, 5, 6, 7, 8), result.counts(ClickCountAvailability.Available))
    }

    @Test
    fun `a binding being edited keeps its own count selectable`() {
        val result = options(
            existing = listOf(binding(id = 7, clickCount = 3), binding(id = 8, clickCount = 4)),
            editingId = 7L,
        )

        assertEquals(listOf(4), result.counts(ClickCountAvailability.UsedByAnotherAction))
        assertEquals(listOf(1, 2, 3, 5, 6, 7, 8), result.counts(ClickCountAvailability.Available))
    }

    @Test
    fun `media control takes precedence over an existing binding on the same count`() {
        // Media control dispatches first, so that is the reason worth showing the user.
        val result = options(existing = listOf(binding(id = 1, clickCount = 2)), reserved = setOf(2))

        assertEquals(listOf(2), result.counts(ClickCountAvailability.UsedByMediaControl))
        assertEquals(emptyList(), result.counts(ClickCountAvailability.UsedByAnotherAction))
    }

    @Test
    fun `firstSelectable skips locked counts`() {
        assertEquals(3, options(reserved = setOf(1, 2)).firstSelectable())
        assertNull(options(reserved = (1..8).toSet()).firstSelectable())
    }
}
