package io.rebble.libpebblecommon.database.entity

import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import kotlin.test.Test
import kotlin.test.assertEquals

class ChargeLimitPrefTest {
    @Test
    fun prefIdMatchesTheFirmwareSettingsKey() {
        assertEquals("chargeLimitPct", EnumWatchPref.ChargeLimit.id)
    }

    @Test
    fun capabilityUsesBitIndex25() {
        assertEquals(25, ProtocolCapsFlag.SupportsChargeLimit.value)
    }

    @Test
    fun decodesExactCodes() {
        assertEquals(ChargeLimitLevel.Limit80, EnumWatchPref.ChargeLimit.decodeValue("80"))
        assertEquals(ChargeLimitLevel.Off, EnumWatchPref.ChargeLimit.decodeValue("0"))
    }

    @Test
    fun decodesTheFirmwareRangeBoundaries() {
        assertEquals(ChargeLimitLevel.Off, EnumWatchPref.ChargeLimit.decodeValue("49"))
        assertEquals(ChargeLimitLevel.Limit50, EnumWatchPref.ChargeLimit.decodeValue("50"))
        assertEquals(ChargeLimitLevel.Limit95, EnumWatchPref.ChargeLimit.decodeValue("95"))
        assertEquals(ChargeLimitLevel.Off, EnumWatchPref.ChargeLimit.decodeValue("96"))
    }

    @Test
    fun snapsOffStepValuesToTheStepBelow() {
        assertEquals(ChargeLimitLevel.Limit80, EnumWatchPref.ChargeLimit.decodeValue("82"))
        assertEquals(ChargeLimitLevel.Limit50, EnumWatchPref.ChargeLimit.decodeValue("54"))
        assertEquals(ChargeLimitLevel.Limit90, EnumWatchPref.ChargeLimit.decodeValue("94"))
    }

    @Test
    fun fallsBackToOffForValuesTheFirmwareRejects() {
        assertEquals(ChargeLimitLevel.Off, EnumWatchPref.ChargeLimit.decodeValue("30"))
        assertEquals(ChargeLimitLevel.Off, EnumWatchPref.ChargeLimit.decodeValue("96"))
        assertEquals(ChargeLimitLevel.Off, EnumWatchPref.ChargeLimit.decodeValue("garbage"))
    }

    @Test
    fun encodesTheRawPercentCode() {
        assertEquals("80", EnumWatchPref.ChargeLimit.encodeValue(ChargeLimitLevel.Limit80))
        assertEquals("0", EnumWatchPref.ChargeLimit.encodeValue(ChargeLimitLevel.Off))
    }

    @Test
    fun encodeDecodeRoundTripsEveryLevel() {
        ChargeLimitLevel.entries.forEach { level ->
            assertEquals(
                level,
                EnumWatchPref.ChargeLimit.decodeValue(EnumWatchPref.ChargeLimit.encodeValue(level))
            )
        }
    }
}
