package coredevices.coreapp.api

import CommonApiConfig
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BugReportsListResponse(
    val success: Boolean,
    val ticketIds: List<String>,
    val userHash: String,
    val appId: String,
    val ticketDetails: List<AtlasTicketDetails>? = null
)

@Serializable
data class AtlasTicketDetails(
    val ticketId: String,
    val subject: String,
    val simpleId: Int,
    val lastMessageText: String? = null,
    val lastMessageSide: String? = null,
    val displayTime: String
)

class BugReportsService(
    private val config: CommonApiConfig,
) {
    private val logger = Logger.withTag("BugReportsService")
    
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    suspend fun fetchBugReports(email: String, googleIdToken: String? = null): BugReportsListResponse? {
        val url = "${config.bugUrl}/bug-reports/list"
        
        return try {
            val response = client.get(url) {
                parameter("email", email)
                googleIdToken?.let { parameter("googleIdToken", it) }
            }
            
            if (response.status.isSuccess()) {
                val result = response.body<BugReportsListResponse>()
                return result
            } else {
                logger.e { "Backend returned error: ${response.status}" }
                null
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to fetch bug reports from $url" }
            null
        }
    }
}