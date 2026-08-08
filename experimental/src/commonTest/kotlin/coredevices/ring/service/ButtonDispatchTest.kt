package coredevices.ring.service

import coredevices.ring.database.MusicControlMode
import coredevices.ring.database.reservedClickCounts
import kotlin.test.Test
import kotlin.test.assertEquals

class ButtonDispatchTest {

    private fun shorts(n: Int) = List(n) { ButtonPress.Short }

    @Test
    fun `a sequence containing a long press belongs to the recording pipeline`() {
        // Press-and-hold, and click-then-hold — both start recordings.
        assertEquals(
            ButtonDispatch.Ignore,
            resolveButtonDispatch(listOf(ButtonPress.Long), emptySet()),
        )
        assertEquals(
            ButtonDispatch.Ignore,
            resolveButtonDispatch(listOf(ButtonPress.Short, ButtonPress.Long), emptySet()),
        )
    }

    @Test
    fun `an empty sequence is ignored`() {
        assertEquals(ButtonDispatch.Ignore, resolveButtonDispatch(emptyList(), emptySet()))
    }

    @Test
    fun `media control wins over a custom binding on the same count`() {
        val reserved = MusicControlMode.DoubleClick.reservedClickCounts()

        assertEquals(ButtonDispatch.Media(shorts(2)), resolveButtonDispatch(shorts(2), reserved))
        assertEquals(ButtonDispatch.Media(shorts(3)), resolveButtonDispatch(shorts(3), reserved))
    }

    @Test
    fun `counts media control does not claim go to the custom binding`() {
        val reserved = MusicControlMode.DoubleClick.reservedClickCounts()

        assertEquals(ButtonDispatch.Custom(1), resolveButtonDispatch(shorts(1), reserved))
        assertEquals(ButtonDispatch.Custom(4), resolveButtonDispatch(shorts(4), reserved))
    }

    @Test
    fun `with media control disabled every count is custom`() {
        val reserved = MusicControlMode.Disabled.reservedClickCounts()

        (1..8).forEach { count ->
            assertEquals(ButtonDispatch.Custom(count), resolveButtonDispatch(shorts(count), reserved))
        }
    }

    @Test
    fun `single click mode claims one and two clicks`() {
        val reserved = MusicControlMode.SingleClick.reservedClickCounts()

        assertEquals(ButtonDispatch.Media(shorts(1)), resolveButtonDispatch(shorts(1), reserved))
        assertEquals(ButtonDispatch.Media(shorts(2)), resolveButtonDispatch(shorts(2), reserved))
        assertEquals(ButtonDispatch.Custom(3), resolveButtonDispatch(shorts(3), reserved))
    }

    @Test
    fun `counts beyond the bindable range are ignored`() {
        assertEquals(ButtonDispatch.Ignore, resolveButtonDispatch(shorts(9), emptySet()))
        assertEquals(ButtonDispatch.Ignore, resolveButtonDispatch(shorts(20), emptySet()))
    }
}
