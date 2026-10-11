package coredevices.pebble.plugin

import co.touchlab.kermit.Logger
import com.eygraber.uri.Uri
import coredevices.api.ApiClient
import coredevices.util.integrations.OAuthCancelledException
import coredevices.util.integrations.OAuthLauncher
import coredevices.util.integrations.generateOAuthCodeVerifier
import coredevices.util.integrations.oauthCodeChallenge
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.rebble.libpebblecommon.plugin.PluginErrors
import io.rebble.libpebblecommon.plugin.PluginNativeException
import io.rebble.libpebblecommon.plugin.PluginOAuthApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal fun isExpectedHostedOAuthRedirect(
    redirect: Uri,
    pluginUuid: String,
    connectorSlug: String,
): Boolean =
    redirect.scheme == "pebble" &&
        redirect.host == "oauth" &&
        redirect.pathSegments == listOf(pluginUuid, connectorSlug) &&
        redirect.getQueryParameter("plugin_uuid") == pluginUuid &&
        redirect.getQueryParameter("connector_slug") == connectorSlug

/** Broker-backed [PluginOAuthApi], run from the signed-in user's session. */
class HostedPluginOAuthApi(
    private val oauthLauncher: OAuthLauncher,
) : ApiClient(version = "plugin-oauth"), PluginOAuthApi {
    private val json = Json { ignoreUnknownKeys = true }
    private val logger = Logger.withTag("HostedPluginOAuth")

    override suspend fun authorize(pluginUuid: String, connectorSlug: String): JsonObject {
        val verifier = generateOAuthCodeVerifier()
        val startResponse = client.get("$BROKER_BASE/start") {
            bearerAuth(requireUserToken())
            parameter("plugin_uuid", pluginUuid)
            parameter("connector_slug", connectorSlug)
            parameter("code_challenge", oauthCodeChallenge(verifier))
            parameter("code_challenge_method", "S256")
        }
        val start = startResponse.requireJson("start")
        val authUrl = start.string("url")
            ?: throw PluginNativeException(PluginErrors.UNKNOWN, "OAuth broker returned no authorization URL")

        val redirect = try {
            oauthLauncher.authenticate(
                authUrl = authUrl,
                callbackScheme = "pebble",
                expectedPathSegment = connectorSlug,
            )
        } catch (error: OAuthCancelledException) {
            throw PluginNativeException("CANCELLED", error.message ?: "OAuth sign in was cancelled")
        }
        // The broker echoes this identity on both success and error redirects. Validate it before
        // trusting even an error value, so another same-scheme callback cannot terminate this flow.
        if (!isExpectedHostedOAuthRedirect(redirect, pluginUuid, connectorSlug)) {
            throw PluginNativeException(PluginErrors.PERMISSION_DENIED, "OAuth callback identity mismatch")
        }
        redirect.getQueryParameter("error")?.let { providerError ->
            throw PluginNativeException(PluginErrors.AUTH_REQUIRED, "OAuth failed: $providerError")
        }
        val code = redirect.getQueryParameter("code")
            ?: throw PluginNativeException(PluginErrors.AUTH_REQUIRED, "OAuth callback returned no code")

        return client.post("$BROKER_BASE/exchange") {
            bearerAuth(requireUserToken())
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("code", code)
                put("code_verifier", verifier)
            }.toString())
        }.requireJson("exchange")
    }

    override suspend fun refresh(
        pluginUuid: String,
        connectorSlug: String,
        brokerRefresh: String,
    ): JsonObject {
        // The sealed wrapper is bound server-side to the uuid, slug, and Firebase uid, so it cannot
        // be moved across connectors; the slug/uuid here are already manifest-verified by the bridge.
        require(pluginUuid.isNotBlank() && connectorSlug.isNotBlank())
        return client.post("$BROKER_BASE/refresh") {
            bearerAuth(requireUserToken())
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject { put("broker_refresh", brokerRefresh) }.toString())
        }.requireJson("refresh")
    }

    private suspend fun HttpResponse.requireJson(operation: String): JsonObject {
        val body = bodyAsText()
        val parsed = try {
            json.parseToJsonElement(body).jsonObject
        } catch (error: Exception) {
            logger.w(error) { "$operation returned malformed JSON (${status.value})" }
            throw PluginNativeException(PluginErrors.UNKNOWN, "OAuth broker returned an invalid response")
        }
        if (status.isSuccess()) return parsed

        val code = parsed.string("code") ?: when (status.value) {
            401 -> PluginErrors.AUTH_REQUIRED
            403 -> PluginErrors.PERMISSION_DENIED
            404 -> PluginErrors.PLUGIN_UNAVAILABLE
            429 -> PluginErrors.RATE_LIMITED
            else -> PluginErrors.UNKNOWN
        }
        val message = parsed.string("error") ?: "OAuth broker request failed (${status.value})"
        throw PluginNativeException(code, message)
    }

    private fun JsonObject.string(name: String): String? =
        get(name)?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

    private companion object {
        const val BROKER_BASE = "https://appstore-api.repebble.com/api/oauth/broker"
    }
}
