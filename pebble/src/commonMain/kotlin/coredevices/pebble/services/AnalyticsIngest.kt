package coredevices.pebble.services

import CommonApiConfig
import CoreAppVersion
import co.touchlab.kermit.Logger
import coredevices.database.AnalyticsHeartbeatEntity
import coredevices.pebble.Platform
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

class AnalyticsIngest(
    private val httpClient: HttpClient,
    private val apiConfig: CommonApiConfig,
    private val platform: Platform,
    private val appVersion: CoreAppVersion,
) {
    private val logger = Logger.withTag("AnalyticsIngest")

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun uploadHeartbeat(row: AnalyticsHeartbeatEntity): Boolean {
        val baseUrl = apiConfig.bugUrl
        if (baseUrl == null) {
            logger.d { "No base URL configured; skipping analytics upload" }
            return true
        }
        val url = "$baseUrl/analytics/ingest"
        val body = buildEnvelope(row)
        val response = try {
            httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        } catch (e: IOException) {
            logger.w(e) { "Failed to POST analytics heartbeat: ${e.message}" }
            return false
        }
        return if (!response.status.isSuccess()) {
            logger.w { "Analytics ingest response = ${response.status}" }
            false
        } else true
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun buildEnvelope(row: AnalyticsHeartbeatEntity): JsonObject = buildJsonObject {
        val userToken = try {
            Firebase.auth.currentUser?.getIdToken(false)
        } catch (e: Exception) {
            logger.w(e) { "Failed to get ID token" }
            null
        }
        putJsonObject("global_properties") {
            putJsonObject("identity") {
                put("serial_number", row.serial)
            }
            putJsonObject("device") {
                putJsonObject("remote_device") {
                    putJsonObject("firmware_description") {
                        if (row.fwVersion != null) put("version", row.fwVersion)
                    }
                }
            }
        }
        put("tz_offset", row.tzOffsetMinutes)
        put("analytics_data", Base64.encode(row.payload))
        put("mobile_version", appVersion.version)
        put("mobile_os", platform.storeString())
        userToken?.let {
            put("firebase_token", userToken)
        }
    }
}
