package coredevices.pebble.firmware

import CoreAppVersion
import co.touchlab.kermit.Logger
import com.eygraber.uri.Uri
import coredevices.pebble.Platform
import coredevices.pebble.account.BootConfigProvider
import coredevices.pebble.services.PebbleHttpClient
import coredevices.pebble.services.PebbleHttpClient.Companion.get
import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.services.FirmwareVersion
import io.rebble.libpebblecommon.services.WatchInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

class Cohorts(
    private val httpClient: PebbleHttpClient,
    private val bootConfig: BootConfigProvider,
    private val platform: Platform,
    private val appVersion: CoreAppVersion,
) {
    private val logger = Logger.withTag("Cohorts")

    suspend fun getLatestFirmware(watch: WatchInfo): FirmwareUpdateCheckResult {
        logger.v { "getLatestFirmware" }
        val baseUrl = bootConfig.getBootConfig()?.config?.cohorts?.endpoint
        if (baseUrl == null) {
            return FirmwareUpdateCheckResult.UpdateCheckFailed("No cohorts endpoint")
        }
        val platformString = when (platform) {
            Platform.IOS -> "ios"
            Platform.Android -> "android"
        }
        val uri = Uri.parse(baseUrl).buildUpon().apply {
            appendQueryParameter(PARAM_HARDWARE, watch.platform.revision)
            appendQueryParameter(PARAM_MOBILE_PLATFORM_VERSION, "1.0.0") // FIXME
            appendQueryParameter(PARAM_MOBILE_HARDWARE, "samsung") // FIXME
            appendQueryParameter(PARAM_MOBILE_PLATFORM, platformString)
            appendQueryParameter(PARAM_PEBBLE_APP_VERSION, appVersion.version)
            appendQueryParameter(PARAM_SELECT, "fw")
        }.build()
        val response: CohortsResponse? = httpClient.get(uri.toString(), auth = true)
        if (response == null) {
            logger.i { "No response from cohorts" }
            return FirmwareUpdateCheckResult.UpdateCheckFailed("Failed to check for PebbleOS update")
        }
        logger.v { "response: $response" }
        // (differently from memfault) cohorts always returns the latest (we didn't pass the current
        // version to it), so we need to check whether it needs an update
        val latestFwVersion = FirmwareVersion.from(
            tag = response.fw.normal.friendlyVersion,
            isRecovery = false,
            gitHash = "", // TODO
            timestamp = Instant.DISTANT_PAST, // TODO
            isDualSlot = false, // not used from here
            isSlot0 = false, // not used from here
        )
        if (latestFwVersion == null) {
            logger.e { "Couldn't parse firmware version from response" }
            return FirmwareUpdateCheckResult.UpdateCheckFailed("Failed to check for PebbleOS update")
        }
        if (watch.runningFwVersion.isRecovery || latestFwVersion > watch.runningFwVersion) {
            return FirmwareUpdateCheckResult.FoundUpdate(
                version = latestFwVersion,
                url = response.fw.normal.url,
                notes = response.fw.normal.notes,
            )
        } else {
            return FirmwareUpdateCheckResult.FoundNoUpdate
        }
    }

    companion object {
        private const val PARAM_HARDWARE: String = "hardware"
        private const val PARAM_MOBILE_PLATFORM: String = "mobilePlatform"
        private const val PARAM_MOBILE_PLATFORM_VERSION: String = "mobileVersion"
        private const val PARAM_MOBILE_HARDWARE: String = "mobileHardware"
        private const val PARAM_PEBBLE_APP_VERSION: String = "pebbleAppVersion"
        private const val PARAM_SELECT: String = "select"
    }
}

@Serializable
data class CohortsResponse(
    val fw: CohortsFirmwares,
)

@Serializable
data class CohortsFirmwares(
    val normal: CohortsFirmware,
)

@Serializable
data class CohortsFirmware(
    val friendlyVersion: String,
    val notes: String,
    @SerialName("sha-256")
    val sha256: String,
    val timestamp: Long,
    val url: String,
)