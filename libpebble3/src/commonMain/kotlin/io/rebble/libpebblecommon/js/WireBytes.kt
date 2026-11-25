package io.rebble.libpebblecommon.js

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder

internal expect fun createHttpClient(): HttpClient

/** A response body's size as received, where the platform unpacks it before Ktor sees it. */
internal expect class WireBytes() {
    fun tag(request: HttpRequestBuilder)

    /** Only valid once the body has been read; null if the platform didn't measure it. */
    fun take(): Long?
}
