package coredevices.util

import kotlin.test.Test
import kotlin.test.assertEquals

class TextTest {
    @Test
    fun capitalizesLowercaseFirstWord() {
        assertEquals("Put the speaker on the trike", "put the speaker on the trike".capitalizeFirstWord())
    }

    @Test
    fun preservesMixedCaseFirstWord() {
        assertEquals("iPhone charger", "iPhone charger".capitalizeFirstWord())
        assertEquals("eBay auction", "eBay auction".capitalizeFirstWord())
    }

    @Test
    fun leavesCapitalizedAndEmptyUnchanged() {
        assertEquals("Buy milk", "Buy milk".capitalizeFirstWord())
        assertEquals("", "".capitalizeFirstWord())
    }
}
