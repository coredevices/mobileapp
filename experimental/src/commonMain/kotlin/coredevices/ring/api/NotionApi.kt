package coredevices.ring.api

import coredevices.api.ApiClient
import coredevices.indexai.data.notion.NotionBlock
import coredevices.indexai.data.notion.NotionErrorResponse
import coredevices.indexai.data.notion.NotionSearchFilter
import coredevices.indexai.data.notion.NotionSearchResponse
import coredevices.indexai.data.oauth.OAuthTokenResponse
import coredevices.indexai.data.oauth.OAuthURLResponse
import coredevices.indexai.util.JsonSnake
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

class NotionApi(config: ApiConfig): ApiClient(config.version), OAuthProxyApi {
    private val backendBaseUrl = config.notionOAuthBackendUrl
    private val baseUrl = config.notionApiUrl

    private fun HttpRequestBuilder.notionVersion() {
        header("Notion-Version", "2022-06-28")
    }

    override suspend fun getAuthorizationUrl(challenge: String): String {
        val res = client.get("$backendBaseUrl/auth/start") {
            firebaseAuth()
            parameter("code_challenge", challenge)
            parameter("code_challenge_method", "S256")
        }
        if (!res.status.isSuccess()) {
            error("Failed to get Notion OAuth link: ${res.status}")
        }
        val response = try {
            res.body<OAuthURLResponse>()
        } catch (e: Exception) {
            null
        }
        return response?.url ?: error("No URL in response")
    }

    override suspend fun exchangeCodeForToken(code: String, verifier: String): String {
        val res = client.post("$backendBaseUrl/auth/exchange") {
            firebaseAuth()
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("code", code)
                put("code_verifier", verifier)
            })
        }
        val response = try {
            res.body<OAuthTokenResponse>()
        } catch (e: Exception) {
            null
        }
        if (!res.status.isSuccess()) {
            error("Failed to exchange code for token: ${res.status} message: ${response?.error ?: res.bodyAsText()}")
        }
        return response?.accessToken ?: error("No access token in response")
    }

    override suspend fun refreshToken(): String {
        val res = client.post("$backendBaseUrl/auth/token") {
            firebaseAuth()
        }
        if (!res.status.isSuccess()) {
            error("Failed to refresh Notion token: ${res.status}")
        }
        return res.body()
    }

    override suspend fun revokeToken() {
        val res = client.post("$backendBaseUrl/revoke") {
            firebaseAuth()
        }
        if (!res.status.isSuccess()) {
            error("Failed to revoke Notion token: ${res.status}")
        }
    }

    suspend fun search(token: String, filter: NotionSearchFilter, query: String? = null): NotionSearchResponse {
        val body = buildJsonObject {
            query?.let {
                put("query", it)
            }
            put("filter", JsonSnake.encodeToJsonElement(filter))
        }
        val res = client.post("$baseUrl/search") {
            notionVersion()
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (!res.status.isSuccess()) {
            val errorResponse: NotionErrorResponse? = try {
                res.body<NotionErrorResponse>()
            } catch (e: Exception) {
                null
            }
            error("Failed to search Notion: ${res.status} message: ${errorResponse?.message}")
        } else {
            return res.body()
        }
    }

    suspend fun retrieveBlock(token: String, blockId: String): NotionBlock {
        val res = client.get("$baseUrl/blocks/$blockId") {
            notionVersion()
            bearerAuth(token)
        }
        if (!res.status.isSuccess()) {
            val errorResponse: NotionErrorResponse? = try {
                res.body<NotionErrorResponse>()
            } catch (e: Exception) {
                null
            }
            error("Failed to retrieve block: ${res.status} message: ${errorResponse?.message}")
        }
        return res.body()
    }

    suspend fun blockAppendChild(token: String, blockId: String, child: NotionBlock, after: String? = null): NotionBlock {
        val res = client.patch("$baseUrl/blocks/$blockId/children") {
            bearerAuth(token)
            notionVersion()
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                if (after != null) {
                    put("after", after)
                }
                put("children", JsonSnake.encodeToJsonElement(listOf(child)))
            })
        }
        if (!res.status.isSuccess()) {
            val errorResponse: NotionErrorResponse? = try {
                res.body<NotionErrorResponse>()
            } catch (e: Exception) {
                null
            }
            error("Failed to append child to block: ${res.status} message: ${errorResponse?.message}")
        }
        return res.body<JsonObject>()["results"]?.jsonArray?.firstOrNull()?.let {
            return JsonSnake.decodeFromJsonElement(it.jsonObject)
        } ?: error("No results in response")
    }
}