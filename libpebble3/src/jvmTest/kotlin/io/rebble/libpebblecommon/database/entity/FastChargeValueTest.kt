package io.rebble.libpebblecommon.database.entity

import io.rebble.libpebblecommon.LibPebbleConfig
import io.rebble.libpebblecommon.LibPebbleConfigFlow
import io.rebble.libpebblecommon.database.asMillisecond
import io.rebble.libpebblecommon.database.dao.ValueParams
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.services.FirmwareVersion
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull
import kotlin.time.Instant

private fun firmware(major: Int, minor: Int, patch: Int) = FirmwareVersion(
    stringVersion = "v$major.$minor.$patch",
    timestamp = Instant.fromEpochSeconds(1_757_000_000),
    major = major,
    minor = minor,
    patch = patch,
    suffix = null,
    gitHash = "abc1234",
    isRecovery = false,
    isDualSlot = true,
    isSlot0 = false,
)

class FastChargeValueTest {
    private val item = WatchPrefItem(
        id = BoolWatchPref.FastCharge.id,
        value = "0",
        timestamp = Instant.DISTANT_PAST.asMillisecond(),
    )

    private fun params(firmwareVersion: FirmwareVersion) = ValueParams(
        WatchType.APLITE,
        emptySet(),
        firmwareVersion,
        libPebbleConfigFlow = LibPebbleConfigFlow(MutableStateFlow(LibPebbleConfig())),
    )

    @Test
    fun encodesOnFirmwareThatShipsThePref() {
        assertContentEquals(ubyteArrayOf(0u), item.value(params(firmware(4, 37, 0))))
    }

    @Test
    fun skippedOnFirmwareThatPredatesThePref() {
        assertNull(item.value(params(firmware(4, 36, 2))))
    }
}
