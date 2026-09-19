package coredevices.api

import coredevices.util.CommonBuildKonfig
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.isSuccess
import kotlinx.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface EngDashOtaApi {
    suspend fun getLatestFirmware(
        deviceSerial: String,
        hardwareVersion: String,
        currentVersion: String? = null,
        deviceMac: String? = null
    ): EngDashLatestResult?
}

class EngDashOtaApiImpl(
    private val baseUrl: String = BASE_URL,
) : ApiClient(CommonBuildKonfig.USER_AGENT_VERSION), EngDashOtaApi {
    /**
     * Get the latest firmware
     * @return the latest artifacts or `null` if nothing available
     */
    override suspend fun getLatestFirmware(
        deviceSerial: String,
        hardwareVersion: String,
        currentVersion: String?,
        deviceMac: String?
    ): EngDashLatestResult? {
        val resp = client.get(Url("$baseUrl/ota/latest")) {
            maybeFirebaseAuth()
            parameter("device_serial", deviceSerial)
            parameter("hardware_version", hardwareVersion)
            currentVersion?.let {
                parameter("current_version", currentVersion)
            }
            deviceMac?.let {
                parameter("device_mac", it)
            }
        }

        return when {
            resp.status == HttpStatusCode.NoContent -> null
            resp.status.isSuccess() -> resp.body()
            else -> throw Exception("Failed to get latest firmware: ${resp.status}")
        }
    }

    companion object {
        private const val BASE_URL = "https://dash.repebble.com/api"
    }
}

fun ensureVersionPrefix(version: String): String {
    return if (version.startsWith("v")) {
        version
    } else {
        "v$version"
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
