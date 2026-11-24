package io.rebble.libpebblecommon.js

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpRequestBuilder

internal actual fun createHttpClient(): HttpClient = HttpClient { install(WebSockets) }

/** OkHttp passes gzip through when we ask for it ourselves, so the body read is already the wire size. */
internal actual class WireBytes actual constructor() {
    actual fun tag(request: HttpRequestBuilder) {}

    actual fun take(): Long? = null
}
