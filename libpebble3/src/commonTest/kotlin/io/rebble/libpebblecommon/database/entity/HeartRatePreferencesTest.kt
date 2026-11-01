package io.rebble.libpebblecommon.database.entity

import io.rebble.libpebblecommon.LibPebbleConfig
import io.rebble.libpebblecommon.LibPebbleConfigFlow
import io.rebble.libpebblecommon.database.dao.ValueParams
import io.rebble.libpebblecommon.database.entity.HeartRatePreferencesValue.Companion.encodeToString
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.services.FirmwareVersion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

private val PARAMS = ValueParams(
    platform = WatchType.EMERY,
    capabilities = setOf(ProtocolCapsFlag.SupportsHealthInsights),
    firmwareVersion = FirmwareVersion(
        stringVersion = "v4.9.150",
        timestamp = Instant.DISTANT_PAST,
        major = 4,
        minor = 9,
        patch = 150,
        suffix = null,
        gitHash = "",
        isRecovery = false,
        isDualSlot = false,
        isSlot0 = false,
    ),
    libPebbleConfigFlow = LibPebbleConfigFlow(MutableStateFlow(LibPebbleConfig())),
)

class HeartRatePreferencesTest {
    @Test
    fun age30MatchesTheFirmwareDefaults() {
        val expected = HeartRatePreferencesValue(
            restingHr = 70,
            elevatedHr = 100,
            maxHr = 190,
            zone1Threshold = 130,
            zone2Threshold = 154,
            zone3Threshold = 172,
        )
        assertEquals(expected, HeartRatePreferencesValue.forAge(30))
        assertEquals(HeartRatePreferencesValue(), HeartRatePreferencesValue.forAge(30))
    }

    @Test
    fun zonesFollowAge() {
        assertEquals(
            HeartRatePreferencesValue(70, 100, 160, 115, 133, 147),
            HeartRatePreferencesValue.forAge(60),
        )
        assertEquals(
            HeartRatePreferencesValue(70, 100, 185, 128, 151, 168),
            HeartRatePreferencesValue.forAge(35),
        )
        assertEquals(
            HeartRatePreferencesValue(55, 100, 190, 123, 150, 170),
            HeartRatePreferencesValue.forAge(30, restingHr = 55),
        )
    }

    @Test
    fun everyAgePassesTheFirmwareValidation() {
        for (age in -5..200) {
            val prefs = HeartRatePreferencesValue.forAge(age)
            assertTrue(prefs.restingHr <= prefs.elevatedHr, "age $age")
            assertTrue(prefs.elevatedHr <= prefs.maxHr, "age $age")
            assertTrue(prefs.zone1Threshold <= prefs.zone2Threshold, "age $age")
            assertTrue(prefs.zone2Threshold <= prefs.zone3Threshold, "age $age")
            assertTrue(prefs.maxHr <= 255, "age $age")
        }
    }

    @Test
    fun sendsTheWatchsSixByteStruct() {
        val entry = HealthSettingsEntry(
            id = "heartRatePreferences",
            value = HeartRatePreferencesValue.forAge(60).encodeToString(),
        )
        assertContentEquals(ubyteArrayOf(70u, 100u, 160u, 115u, 133u, 147u), entry.value(PARAMS))
    }
}
