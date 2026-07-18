package coredevices.pebble.config.bridge

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import io.ktor.util.flattenEntries

/**
 * Performs HTTP requests through Ktor/OkHttp so the config page is not subject to
 * WebView CORS or mixed-content restrictions.
 */
class BridgeFetch(private val client: HttpClient) {

    suspend fun execute(request: FetchRequest): FetchResponse {
        val response: HttpResponse = client.request(request.url) {
            method = parseMethod(request.method)
            timeout {
                requestTimeoutMillis = request.timeout
                connectTimeoutMillis = 30_000
            }
            headers {
                request.headers.forEach { (key, value) ->
                    append(key, value)
                }
            }
            request.body?.let { setBody(it) }
        }

        val body = response.bodyAsText()
        return FetchResponse(
            ok = response.status.isSuccess(),
            status = response.status.value,
            statusText = response.status.description,
            headers = response.headers.toMap(),
            body = body,
        )
    }

    private fun parseMethod(method: String): HttpMethod = when (method.uppercase()) {
        "GET" -> HttpMethod.Get
        "POST" -> HttpMethod.Post
        "PUT" -> HttpMethod.Put
        "DELETE" -> HttpMethod.Delete
        "PATCH" -> HttpMethod.Patch
        "HEAD" -> HttpMethod.Head
        "OPTIONS" -> HttpMethod.Options
        else -> HttpMethod.parse(method)
    }

    private fun Headers.toMap(): Map<String, String> {
        return flattenEntries().groupBy({ it.first }, { it.second })
            .mapValues { it.value.joinToString(", ") }
    }
}
