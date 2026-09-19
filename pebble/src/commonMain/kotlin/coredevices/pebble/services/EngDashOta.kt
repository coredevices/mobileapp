package coredevices.pebble.services

import co.touchlab.kermit.Logger
import coredevices.api.EngDashOtaApi
import coredevices.api.ensureVersionPrefix
import coredevices.pebble.services.Memfault.Companion.serialForMemfault
import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.services.FirmwareVersion
import io.rebble.libpebblecommon.services.WatchInfo
import kotlin.time.Instant

/**
 * OTA update check against eng-dash (`/ota/latest`), an alternative to Memfault OTA for
 * Core devices. The release comes from the signed-in account's track rather than the serial.
 */
class EngDashOta(
    private val otaApi: EngDashOtaApi
) {
    private val logger = Logger.withTag("EngDashOta")

    suspend fun getLatestFirmware(watch: WatchInfo): FirmwareUpdateCheckResult {
        val result = try {
            otaApi.getLatestFirmware(
                deviceSerial = watch.serialForMemfault(),
                hardwareVersion = watch.platform.revision,
                currentVersion = if (!watch.runningFwVersion.isRecovery) {
                    ensureVersionPrefix(watch.runningFwVersion.stringVersion)
                } else null
            )
        } catch (e: IllegalStateException) {
            return FirmwareUpdateCheckResult.UpdateCheckFailed(e.message ?: "Failed to check for PebbleOS update")
        } catch (e: Exception) {
            logger.w(e) { "Error checking for updates from eng-dash: ${e.message}" }
            return FirmwareUpdateCheckResult.UpdateCheckFailed("Failed to check for PebbleOS update")
        }
        if (result == null) {
            logger.i("No new firmware available")
            return FirmwareUpdateCheckResult.FoundNoUpdate
        }
        logger.d { "ota: $result" }
        val fwVersion = FirmwareVersion.from(
            tag = result.version,
            isRecovery = false,
            gitHash = "", // TODO
            timestamp = Instant.DISTANT_PAST, // TODO
            isDualSlot = false, // not used from here
            isSlot0 = false, // not used from here
        )
        return if (fwVersion == null) {
            FirmwareUpdateCheckResult.UpdateCheckFailed("Failed to check for PebbleOS update")
        } else {
            FirmwareUpdateCheckResult.FoundUpdate(
                version = fwVersion,
                notes = result.notes.orEmpty(),
                url = result.artifacts.first().url,
                canDowngrade = result.isDowngrade,
            )
        }
    }
}
