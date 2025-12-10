package coredevices.pebble.services

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import coredevices.pebble.ui.SettingsKeys.KEY_ENABLE_MEMFAULT_UPLOADS
import coredevices.util.CommonBuildKonfig
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.services.FirmwareVersion
import io.rebble.libpebblecommon.services.WatchInfo
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlin.time.Instant

class Memfault(
    private val httpClient: HttpClient,
    private val settings: Settings,
) {
    private val logger = Logger.withTag("Memfault")

    suspend fun getLatestFirmware(watch: WatchInfo): FirmwareUpdateCheckResult {
        val token = CommonBuildKonfig.MEMFAULT_TOKEN
        if (token == null) {
            return FirmwareUpdateCheckResult.UpdateCheckFailed("No Memfault token")
        }
        val versionString = if (watch.runningFwVersion.isRecovery) {
            null
        } else {
            ensureVersionPrefix(watch.runningFwVersion.stringVersion)
        }
        val serial = watch.serialForMemfault()
        val params = buildMap {
            put("hardware_version", watch.platform.revision)
            put("software_type", "pebbleos")
            put("device_serial", serial)
            if (versionString != null) {
                put("current_version", versionString)
            }
        }
        val encodedParams = params.entries
            .map { (k, v) -> "${k.encodeURLParameter()}=${v.encodeURLParameter()}" }
            .joinToString("&")
        val url = "https://api.memfault.com/api/v0/releases/latest?$encodedParams"
        Logger.v { "url=$url" }
        val response = try {
            httpClient.get(url) {
                header("Memfault-Project-Key", token)
            }
        }  catch (e: IOException) {
            logger.w(e) { "Error checking for updates from memfault: ${e.message}" }
            return FirmwareUpdateCheckResult.UpdateCheckFailed("Failed to check for firmware update")
        }
        return when (response.status) {
            HttpStatusCode.OK -> try {
                val result = response.body<LatestResult>()
                logger.d { "result=$result" }
                val fwVersion = FirmwareVersion.from(
                    tag = result.version,
                    isRecovery = false,
                    gitHash = "", // TODO
                    timestamp = Instant.DISTANT_PAST, // TODO
                    isDualSlot = false, // not used from here
                    isSlot0 = false, // not used from here
                )
                logger.d { "fwVersion=$fwVersion" }
                if (fwVersion == null) {
                    FirmwareUpdateCheckResult.UpdateCheckFailed("Failed to check for firmware update")
                } else {
                    FirmwareUpdateCheckResult.FoundUpdate(
                        version = fwVersion,
                        notes = result.notes,
                        url = result.artifacts.first().url
                    )
                }
            } catch (e: NoTransformationFoundException) {
                logger.e("error: ${e.message}", e)
                FirmwareUpdateCheckResult.UpdateCheckFailed("Failed to check for firmware update")
            } catch (e: ContentConvertException) {
                logger.e("error: ${e.message}", e)
                FirmwareUpdateCheckResult.UpdateCheckFailed("Failed to check for firmware update")
            }

            HttpStatusCode.NoContent -> {
                logger.i("No new firmware available")
                FirmwareUpdateCheckResult.FoundNoUpdate
            }

            else -> {
                logger.e { "Error fetching latest FW: ${response.status}" }
                FirmwareUpdateCheckResult.UpdateCheckFailed("Failed to check for firmware update")
            }
        }
    }

    suspend fun uploadChunk(chunk: ByteArray, watchInfo: WatchInfo) {
        if (!settings.getBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, true)) {
            logger.d { "Not uploading Memfault chunk (disabled in settings)" }
            return
        }
        val token = CommonBuildKonfig.MEMFAULT_TOKEN
        if (token == null) {
            logger.i { "uploadChunk: no memfault token" }
            return
        }
        logger.d { "Sending chunk to Memfault" }
        val serial = watchInfo.serialForMemfault()
        val url = "https://chunks.memfault.com/api/v0/chunks/$serial"
        val response = try {
            httpClient.post(url) {
                header("Memfault-Project-Key", token)
                setBody(chunk)
            }
        } catch (e: IOException) {
            logger.w(e) { "Error sending chunk to memfault: ${e.message}" }
            return
        }
        if (!response.status.isSuccess()) {
            logger.w { "uploadChunk response = ${response.status}" }
        }
    }

    private fun ensureVersionPrefix(version: String): String {
        return if (version.startsWith("v")) {
            version
        } else {
            "v$version"
        }
    }

    companion object {
        private fun WatchInfo.partialMacAddress(): String = btAddress
            .split(":")
            // Backwards compatibility to before we fixed the mac address value
            .reversed()
            .joinToString("").take(8)

        fun WatchInfo.serialForMemfault(): String = when (serial) {
            // Hack for prototype watches which all have the same serial: use BT MAC to disambiguate
            "XXXXXXXXXXXX" -> "XXXX${partialMacAddress()}"
            else -> serial
        }
    }
}

@Serializable
data class LatestResult(
    val version: String,
    val display_name: String,
    val notes: String,
    val artifacts: List<LatestArtifact>
)

@Serializable
data class LatestArtifact(
    val url: String,
)