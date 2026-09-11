package io.rebble.libpebblecommon.database.entity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FastChargePrefTest {
    @Test
    fun prefIdMatchesTheFirmwareSettingsKey() {
        assertEquals("fastCharge", BoolWatchPref.FastCharge.id)
    }

    @Test
    fun defaultsToFastCharging() {
        assertTrue(BoolWatchPref.FastCharge.defaultValue)
    }

    @Test
    fun encodesAsAFirmwareBoolean() {
        assertEquals("1", BoolWatchPref.FastCharge.encodeValue(true))
        assertEquals("0", BoolWatchPref.FastCharge.encodeValue(false))
        assertEquals(false, BoolWatchPref.FastCharge.decodeValue("0"))
    }
}
