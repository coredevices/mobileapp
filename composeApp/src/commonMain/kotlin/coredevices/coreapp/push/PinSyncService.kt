package coredevices.coreapp.push

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.rebble.libpebblecommon.connection.LibPebble
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration.Companion.seconds

/**
 * Service to fetch pins from localhost /sync endpoint
 */
class PinSyncService : KoinComponent {
    private val logger = Logger.withTag("PinSyncService")
    private val engine by inject<HttpClientEngine> { parametersOf(30.seconds) }

    // Inject dependencies via Koin
    private val libPebble: LibPebble by inject()
    private val payloadParser: FCMPayloadParser by inject()

    companion object {
        private const val SYNC_BASE_URL = "http://192.168.0.226:5000/v1"
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
     * Fetch pins from localhost /sync endpoint
     * @return SyncResponse containing pin data, or null if request fails
     */
    private suspend fun fetchPins(): SyncResponse? {
        return try {
            logger.d { "Fetching pins from $SYNC_BASE_URL/sync" }

            val response = client.get("$SYNC_BASE_URL/sync") {
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
            syncResponse

        } catch (e: Exception) {
            logger.e(e) { "Failed to fetch pins from $SYNC_BASE_URL/sync: ${e.message}" }
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
     * Close the HTTP client when done
     */
    fun close() {
        client.close()
    }
}