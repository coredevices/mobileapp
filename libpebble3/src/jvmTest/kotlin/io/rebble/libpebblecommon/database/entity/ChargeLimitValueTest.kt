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

private fun firmware(major: Int, minor: Int, patch: Int, suffix: String? = null) = FirmwareVersion(
    stringVersion = "v$major.$minor.$patch" + (suffix?.let { "-$it" } ?: ""),
    timestamp = Instant.fromEpochSeconds(1_757_000_000),
    major = major,
    minor = minor,
    patch = patch,
    suffix = suffix,
    gitHash = "abc1234",
    isRecovery = false,
    isDualSlot = true,
    isSlot0 = false,
)

class ChargeLimitValueTest {
    private val item = WatchPrefItem(
        id = EnumWatchPref.ChargeLimit.id,
        value = "80",
        timestamp = Instant.DISTANT_PAST.asMillisecond(),
    )

    private fun params(firmwareVersion: FirmwareVersion) = ValueParams(
        WatchType.APLITE,
        emptySet(),
        firmwareVersion,
        libPebbleConfigFlow = LibPebbleConfigFlow(MutableStateFlow(LibPebbleConfig())),
    )

    @Test
    fun encodesOnTheFirstFirmwareThatShipsThePref() {
        assertContentEquals(ubyteArrayOf(80u), item.value(params(firmware(4, 37, 0))))
    }

    @Test
    fun encodesOnDevelopmentBuildsPastThatTag() {
        assertContentEquals(ubyteArrayOf(80u), item.value(params(firmware(4, 37, 0, "9-gabc1234"))))
    }

    @Test
    fun encodesOnLaterFirmware() {
        assertContentEquals(ubyteArrayOf(80u), item.value(params(firmware(4, 38, 0))))
    }

    @Test
    fun skippedOnFirmwareThatPredatesThePref() {
        assertNull(item.value(params(firmware(4, 36, 2))))
    }

    @Test
    fun prefsWithoutAMinimumVersionAreUnaffected() {
        val clock24h = WatchPrefItem(
            id = BoolWatchPref.Clock24h.id,
            value = "1",
            timestamp = Instant.DISTANT_PAST.asMillisecond(),
        )
        assertContentEquals(ubyteArrayOf(1u), clock24h.value(params(firmware(4, 0, 0))))
    }
}
