package coredevices.pebble.firmware

import co.touchlab.kermit.Logger
import coredevices.pebble.services.HttpClientAuthType
import coredevices.pebble.services.PebbleHttpClient
import coredevices.pebble.services.PebbleHttpClient.Companion.get
import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.services.FirmwareVersion
import io.rebble.libpebblecommon.services.WatchInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

class Cohorts(
    private val httpClient: PebbleHttpClient,
) {
    private val logger = Logger.withTag("Cohorts")

    suspend fun getLatestFirmware(watch: WatchInfo): FirmwareUpdateCheckResult {
        logger.v { "getLatestFirmware" }
        // GitHub raw returns text/plain, so fetch as String and deserialize manually
        val responseText: String? = httpClient.get(LOGGED_OUT_URL, auth = HttpClientAuthType.None)
        if (responseText == null) {
            logger.i { "No response from cohorts" }
            return FirmwareUpdateCheckResult.UpdateCheckFailed("Failed to check for PebbleOS update")
        }
        val response = try {
            json.decodeFromString<CohortsResponse>(responseText)
        } catch (e: Exception) {
            logger.e(e) { "Failed to parse cohorts response" }
            return FirmwareUpdateCheckResult.UpdateCheckFailed("Failed to check for PebbleOS update")
        }
//        logger.v { "response: $response" } // TODO remove
        // (differently from memfault) cohorts always returns the latest (we didn't pass the current
        // version to it), so we need to check whether it needs an update
        val normalFw = response.hardware[watch.platform.revision]?.normal
        if (normalFw == null) {
            logger.i { "No firmware found for ${watch.platform.revision}" }
            return FirmwareUpdateCheckResult.UpdateCheckFailed("Failed to check for PebbleOS update")
        }
        val latestFwVersion = FirmwareVersion.from(
            tag = normalFw.version,
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
                url = "https://binaries.rebble.io/fw/${watch.platform.revision}/Pebble-${normalFw.version}-${watch.platform.revision}.pbz",
                notes = response.notes[normalFw.version] ?: "",
            )
        } else {
            return FirmwareUpdateCheckResult.FoundNoUpdate
        }
    }

    companion object {
        private const val LOGGED_OUT_URL = "https://raw.githubusercontent.com/pebble-dev/rebble-cohorts/refs/heads/master/config.json"
        private val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
data class CohortsResponse(
    val notes: Map<String, String>,
    val timestamps: Map<String, Long>,
    val hardware: Map<String, CohortsFirmwareType>
)

@Serializable
data class CohortsFirmwareType(
    val normal: CohortsFirmware,
)

@Serializable
data class CohortsFirmware(
    val version: String,
    @SerialName("sha-256")
    val sha256: String,
)
