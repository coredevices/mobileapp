package coredevices.coreapp.push

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.rebble.libpebblecommon.connection.LibPebble
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/**
 * Service to fetch pins from localhost /sync endpoint
 */
class PinSyncService : KoinComponent {
    private val logger = Logger.withTag("PinSyncService")
    private val engine by inject<HttpClientEngine> { parametersOf(30.seconds) }

    // Inject dependencies via Koin
    private val libPebble: LibPebble by inject()
    private val payloadParser: FCMPayloadParser by inject()
    private val settings: Settings by inject()

    companion object {
        private const val SYNC_BASE_URL = "http://192.168.0.226:5000/v1"
        private const val TIMELINE_ID_KEY = "pin_sync_timeline_id"
    }

    private val client = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            })
        }
        install(Logging) {
            logger = object : io.ktor.client.plugins.logging.Logger {
                private val logger = Logger.withTag("PinSync-HTTP")
                override fun log(message: String) {
                    logger.v { message }
                }
            }
            level = LogLevel.INFO
        }
    }

    /**
     * Fetch pins from sync endpoint using timeline parameter for incremental updates
     * @return SyncResponse containing pin data, or null if request fails
     */
    private suspend fun fetchPins(): SyncResponse? {
        return try {
            val syncUrl = buildSyncUrl()
            logger.d { "Fetching pins from: $syncUrl" }

            val response = client.get(syncUrl) {
                header("Accept", "application/json")
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                logger.e {
                    "Server returned error ${response.status.value} ${response.status.description}: $errorBody"
                }
                return null
            }

            val syncResponse = response.body<SyncResponse>()
            logger.d { "Successfully fetched sync response with ${syncResponse.updates.size} updates" }

            // Extract and store timeline ID from syncURL for next request
            syncResponse.syncURL?.let { nextUrl ->
                extractAndSaveTimelineId(nextUrl)
            }

            syncResponse

        } catch (e: Exception) {
            logger.e(e) { "Failed to fetch pins from sync endpoint: ${e.message}" }
            null
        }
    }

    suspend fun sync() {
        try {
            val syncResponse = fetchPins() ?: return
            val timelinePins = payloadParser.parsePinsFromSyncResponse(syncResponse)

            // Send each pin to the connected watch
            timelinePins.forEach { timelinePin ->
                logger.d {
                    "Sending timeline pin: " +
                            "layout='${timelinePin.content.layout.name}', " +
                            "attributes='${timelinePin.content.attributes.size}', " +
                            "actions='${timelinePin.content.actions.size}'"
                }
                libPebble.sendPin(timelinePin)
            }
        } catch (e: Exception) {
            logger.e(e) { "Error processing FCM message with sync: ${e.message}" }
        }
    }

    /**
     * Register FCM token with the server
     * @param token The FCM registration token
     * @return true if registration was successful, false otherwise
     */
    suspend fun registerFCMToken(token: String) {
        if (token.isBlank()) {
            logger.w { "Cannot register empty FCM token" }
        }

        if (isTokenAlreadyRegistered(token)) {
            logger.i { "FCM token already registered" }
            return
        }


        return try {
            val response = client.put("$SYNC_BASE_URL/user/fcm_token/$token") {
                contentType(ContentType.Application.Json)
                header("Accept", "application/json")
                // TODO: Replace with actual device ID
                setBody(
                    RegisterToken(
                        deviceId = Uuid.toString(),
                        platform = "android"
                    )
                )
            }

            when (response.status.value) {
                200 -> {
                    logger.i { "Successfully registered FCM token" }
                    settings.putString("fcm_token_registered", token)
                }

                409 -> {
                    logger.w { "FCM token already registered" }
                    settings.putString("fcm_token_registered", token)
                }

                else -> {
                    val errorBody = response.bodyAsText()
                    logger.e {
                        "Failed to register FCM token - ${response.status.value} ${response.status.description}: $errorBody"
                    }
                }
            }

        } catch (e: Exception) {
            logger.e(e) { "Failed to register FCM token: ${e.message}" }
        }
    }

    /**
     * Check if the given token is already registered
     */
    private fun isTokenAlreadyRegistered(token: String): Boolean {
        val registeredToken = settings.getStringOrNull("fcm_token_registered")
        return registeredToken == token
    }

    /**
     * Build sync URL with timeline parameter if available
     */
    private fun buildSyncUrl(): String {
        val timelineId = settings.getStringOrNull(TIMELINE_ID_KEY)
        return if (timelineId != null) {
            val url = "$SYNC_BASE_URL/sync?timeline=$timelineId"
            url
        } else {
            val url = "$SYNC_BASE_URL/sync"
            url
        }
    }

    /**
     * Extract timeline parameter from syncURL and save for next request
     * Example: "http://127.0.0.1:5000/v1/sync?timeline=34" -> save "34"
     */
    private fun extractAndSaveTimelineId(syncUrl: String) {
        try {
            val timelineParam = syncUrl.substringAfter("timeline=", "")
            if (timelineParam.isNotEmpty()) {
                // Handle multiple parameters by taking only the timeline value
                val timelineId = timelineParam.substringBefore("&")
                settings.putString(TIMELINE_ID_KEY, timelineId)
                logger.d { "Extracted and saved timeline ID: $timelineId" }
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to extract timeline ID from: $syncUrl" }
        }
    }

    /**
     * Close the HTTP client when done
     */
    fun close() {
        client.close()
    }
}