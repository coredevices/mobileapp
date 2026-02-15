package coredevices.pebble.firmware

import coredevices.pebble.services.Memfault
import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform
import io.rebble.libpebblecommon.metadata.WatchHardwarePlatform.*
import io.rebble.libpebblecommon.services.WatchInfo

class FirmwareUpdateCheck(
    private val memfault: Memfault,
    private val cohorts: Cohorts,
) {
    suspend fun checkForUpdates(watch: WatchInfo): FirmwareUpdateCheckResult = when {
        watch.platform == UNKNOWN -> FirmwareUpdateCheckResult.UpdateCheckFailed("Unknown platform")
        watch.platform.isCoreDevice() -> memfault.getLatestFirmware(watch)
        else -> cohorts.getLatestFirmware(watch)
    }
}

fun WatchHardwarePlatform.isCoreDevice(): Boolean = when (this) {
    UNKNOWN, PEBBLE_ONE_EV_1, PEBBLE_ONE_EV_2, PEBBLE_ONE_EV_2_3, PEBBLE_ONE_EV_2_4,
    PEBBLE_ONE_POINT_FIVE, PEBBLE_TWO_POINT_ZERO, PEBBLE_SNOWY_EVT_2, PEBBLE_SNOWY_DVT,
    PEBBLE_BOBBY_SMILES, PEBBLE_ONE_BIGBOARD_2, PEBBLE_ONE_BIGBOARD, PEBBLE_SNOWY_BIGBOARD,
    PEBBLE_SNOWY_BIGBOARD_2, PEBBLE_SPALDING_EVT, PEBBLE_SPALDING_PVT, PEBBLE_SPALDING_BIGBOARD,
    PEBBLE_SILK_EVT, PEBBLE_SILK, PEBBLE_SILK_BIGBOARD, PEBBLE_SILK_BIGBOARD_2_PLUS,
    PEBBLE_ROBERT_EVT, PEBBLE_ROBERT_BIGBOARD, PEBBLE_ROBERT_BIGBOARD_2 -> false
    else -> true
}