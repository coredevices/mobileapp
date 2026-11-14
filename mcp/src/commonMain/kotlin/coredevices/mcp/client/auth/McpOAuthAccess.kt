package coredevices.mcp.client.auth

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

class McpOAuthAccess(val id: String, val resource: String, private val oauth: McpOAuth) {
    internal fun plugin() = createClientPlugin("McpOAuth") {
        onRequest { request, _ ->
            if (request.url.build() != parseOAuthUrl(resource)) {
                throw McpOAuthException("MCP attempted to send credentials to another endpoint.")
            }
            request.headers[HttpHeaders.Authorization] = "Bearer ${oauth.accessToken(id, resource)}"
        }
        onResponse { response ->
            if (response.status == HttpStatusCode.Unauthorized) {
                response.call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.let {
                    oauth.rejected(id, it)
                }
                throw McpAuthorizationRejected()
            }
            if (response.status == HttpStatusCode.Forbidden) {
                throw McpOAuthException("This MCP server requires additional permissions. Reconnect in its settings.")
            }
        }
    }
}
