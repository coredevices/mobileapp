package io.rebble.libpebblecommon.database.entity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChargeLimitPrefTest {
    @Test
    fun idMatchesTheFirmwareSettingsKey() {
        assertEquals("chargeLimitPct", EnumWatchPref.ChargeLimit.id)
    }

    @Test
    fun everyLevelIsAValueTheWatchAccepts() {
        ChargeLimitLevel.entries.forEach { level ->
            val code = level.code.toInt()
            assertTrue(code == 0 || code in 50..95, "$level is out of range")
        }
    }

    @Test
    fun encodesThePercentAndRoundTrips() {
        assertEquals("80", EnumWatchPref.ChargeLimit.encodeValue(ChargeLimitLevel.Limit80))
        assertEquals("0", EnumWatchPref.ChargeLimit.encodeValue(ChargeLimitLevel.Off))
        ChargeLimitLevel.entries.forEach { level ->
            assertEquals(
                level,
                EnumWatchPref.ChargeLimit.decodeValue(EnumWatchPref.ChargeLimit.encodeValue(level)),
            )
        }
    }

    @Test
    fun unknownValueFallsBackToOff() {
        assertEquals(ChargeLimitLevel.Off, EnumWatchPref.ChargeLimit.decodeValue("82"))
        assertEquals(ChargeLimitLevel.Off, EnumWatchPref.ChargeLimit.decodeValue("garbage"))
    }
}
