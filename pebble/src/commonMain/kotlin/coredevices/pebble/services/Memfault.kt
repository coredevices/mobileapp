package coredevices.pebble.services

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import coredevices.pebble.Platform
import coredevices.pebble.ui.SettingsKeys.KEY_ENABLE_MEMFAULT_UPLOADS
import coredevices.util.CommonBuildKonfig
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.http.userAgent
import io.ktor.serialization.ContentConvertException
import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.services.FirmwareVersion
import io.rebble.libpebblecommon.services.WatchInfo
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

class Memfault(
    private val httpClient: HttpClient,
    private val settings: Settings,
    private val platform: Platform,
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
            return FirmwareUpdateCheckResult.UpdateCheckFailed("Failed to check for PebbleOS update")
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
                    FirmwareUpdateCheckResult.UpdateCheckFailed("Failed to check for PebbleOS update")
                } else {
                    FirmwareUpdateCheckResult.FoundUpdate(
                        version = fwVersion,
                        notes = result.notes,
                        url = result.artifacts.first().url
                    )
                }
            } catch (e: NoTransformationFoundException) {
                logger.e("error: ${e.message}", e)
                FirmwareUpdateCheckResult.UpdateCheckFailed("Failed to check for PebbleOS update")
            } catch (e: ContentConvertException) {
                logger.e("error: ${e.message}", e)
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

    suspend fun uploadChunkBatch(chunks: List<ByteArray>, serial: String): Boolean {
        if (!settings.getBoolean(KEY_ENABLE_MEMFAULT_UPLOADS, true)) {
            logger.d { "Not uploading Memfault chunks (disabled in settings)" }
            return true
        }
        val token = CommonBuildKonfig.MEMFAULT_TOKEN ?: run {
            logger.i { "uploadChunkBatch: no memfault token" }
            return true
        }
        logger.d { "Sending ${chunks.size} chunk(s) to Memfault for serial=$serial" }
        val url = "https://chunks.memfault.com/api/v0/chunks/$serial"
        val response = try {
            if (chunks.size == 1) {
                httpClient.post(url) {
                    header("Memfault-Project-Key", token)
                    userAgent(platform.name)
                    setBody(chunks.first())
                }
            } else {
                val boundary = Uuid.random().toString()
                httpClient.post(url) {
                    header("Memfault-Project-Key", token)
                    userAgent(platform.name)
                    setBody(ByteArrayContent(
                        bytes = buildMultipartMixedBody(chunks, boundary),
                        contentType = ContentType.MultiPart.Mixed.withParameter("boundary", boundary),
                    ))
                }
            }
        } catch (e: IOException) {
            logger.w(e) { "Error sending chunks to Memfault: ${e.message}" }
            return false
        }
        return if (!response.status.isSuccess()) {
            logger.w { "uploadChunkBatch response = ${response.status}" }
            false
        } else true
    }

    /**
     * Build a multipart/mixed body matching the format the Memfault chunks API expects.
     * Each part has only a Content-Length header and the raw chunk bytes.
     */
    private fun buildMultipartMixedBody(chunks: List<ByteArray>, boundary: String): ByteArray {
        val parts = mutableListOf<ByteArray>()
        for (chunk in chunks) {
            parts.add("--$boundary\r\nContent-Length: ${chunk.size}\r\n\r\n".encodeToByteArray())
            parts.add(chunk)
            parts.add("\r\n".encodeToByteArray())
        }
        parts.add("--$boundary--\r\n".encodeToByteArray())
        val result = ByteArray(parts.sumOf { it.size })
        var offset = 0
        for (part in parts) {
            part.copyInto(result, offset)
            offset += part.size
        }
        return result
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
