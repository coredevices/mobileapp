package coredevices.ring.agent.integrations.memos

import co.touchlab.kermit.Logger
import coredevices.ring.api.ApiConfig
import coredevices.api.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class CreateMemoRequest(
    val baseUrl: String,
    val token: String,
    val content: String,
)

interface MemosApi {
    /** Returns the created memo's resource name. Throws if the server rejected the note. */
    suspend fun createMemo(request: CreateMemoRequest): String

    /** True when the server accepts the token. */
    suspend fun validateCredentials(baseUrl: String, token: String): Boolean
}

class MemosApiImpl(config: ApiConfig) : ApiClient(config.version), MemosApi {

    companion object {
        private val logger = Logger.withTag("MemosApi")
    }

    override suspend fun createMemo(request: CreateMemoRequest): String {
        val response = client.post("${request.baseUrl}/api/v1/memos") {
            bearerAuth(request.token)
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("content", request.content) })
        }
        // CreateNoteTool reports a null return as a saved note; only an exception surfaces failure.
        if (!response.status.isSuccess()) {
            error("Memos rejected the note: ${response.status}")
        }
        return response.body<JsonObject>()["name"]?.jsonPrimitive?.contentOrNull
            ?: error("Memos returned no memo name")
    }

    override suspend fun validateCredentials(baseUrl: String, token: String): Boolean {
        val response = client.get("$baseUrl/api/v1/auth/me") { bearerAuth(token) }
        if (!response.status.isSuccess()) {
            logger.w { "Memos rejected credentials: ${response.status}" }
            return false
        }
        return true
    }
}
