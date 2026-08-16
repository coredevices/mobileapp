package coredevices.pebble.services

import co.touchlab.kermit.Logger
import coredevices.pebble.services.Memfault.Companion.serialForMemfault
import coredevices.pebble.services.PebbleHttpClient.Companion.authFor
import coredevices.util.CommonBuildKonfig
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.ContentConvertException
import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.services.FirmwareVersion
import io.rebble.libpebblecommon.services.WatchInfo
import kotlinx.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * OTA update check against eng-dash (`$BUG_URL/ota/latest`), an alternative to Memfault OTA for
 * Core devices. The release comes from the signed-in account's track rather than the serial.
 */
class EngDashOta(
    private val httpClient: HttpClient,
    private val pebbleHttpClient: PebbleHttpClient,
) {
    private val logger = Logger.withTag("EngDashOta")

    suspend fun getLatestFirmware(watch: WatchInfo): FirmwareUpdateCheckResult {
        val baseUrl = CommonBuildKonfig.BUG_URL
            ?: return FirmwareUpdateCheckResult.UpdateCheckFailed("No eng-dash URL configured")
        val token = pebbleHttpClient.authFor(HttpClientAuthType.Core)
        val response = try {
            httpClient.get("$baseUrl/ota/latest") {
                token?.let { bearerAuth(token) }
                parameter("device_serial", watch.serialForMemfault())
                parameter("hardware_version", watch.platform.revision)
                if (!watch.runningFwVersion.isRecovery) {
                    parameter(
                        "current_version",
                        ensureVersionPrefix(watch.runningFwVersion.stringVersion),
                    )
                }
            }
        } catch (e: IOException) {
            logger.w(e) { "Error checking for updates from eng-dash: ${e.message}" }
            return FirmwareUpdateCheckResult.UpdateCheckFailed("Failed to check for PebbleOS update")
        }
        return when (response.status) {
            HttpStatusCode.OK -> try {
                val result = response.body<EngDashLatestResult>()
                logger.d { "ota: $result" }
                val fwVersion = FirmwareVersion.from(
                    tag = result.version,
                    isRecovery = false,
                    gitHash = "", // TODO
                    timestamp = Instant.DISTANT_PAST, // TODO
                    isDualSlot = false, // not used from here
                    isSlot0 = false, // not used from here
                )
                if (fwVersion == null) {
                    FirmwareUpdateCheckResult.UpdateCheckFailed("Failed to check for PebbleOS update")
                } else {
                    FirmwareUpdateCheckResult.FoundUpdate(
                        version = fwVersion,
                        notes = result.notes.orEmpty(),
                        url = result.artifacts.first().url,
                        canDowngrade = result.isDowngrade,
                    )
                }
            } catch (e: NoTransformationFoundException) {
                logger.e("error: ${e.message}", e)
                FirmwareUpdateCheckResult.UpdateCheckFailed("Failed to check for PebbleOS update")
            } catch (e: ContentConvertException) {
                logger.e("error: ${e.message}", e)
                FirmwareUpdateCheckResult.UpdateCheckFailed("Failed to check for PebbleOS update")
            } catch (e: NoSuchElementException) {
                logger.e("error: no artifacts in response", e)
                FirmwareUpdateCheckResult.UpdateCheckFailed("Failed to check for PebbleOS update")
            }

            HttpStatusCode.NoContent -> {
                logger.i("No new firmware available")
                FirmwareUpdateCheckResult.FoundNoUpdate
            }

            else -> {
                logger.e { "Error fetching latest FW: ${response.status}" }
                FirmwareUpdateCheckResult.UpdateCheckFailed("Failed to check for PebbleOS update")
            }
        }
    }
}

@Serializable
data class EngDashLatestResult(
    val version: String,
    val notes: String? = null,
    @SerialName("is_downgrade")
    val isDowngrade: Boolean = false,
    val artifacts: List<EngDashArtifact>,
)

@Serializable
data class EngDashArtifact(
    val url: String,
)
