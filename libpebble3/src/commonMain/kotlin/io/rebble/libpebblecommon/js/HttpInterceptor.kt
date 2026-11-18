package io.rebble.libpebblecommon.js

import io.rebble.libpebblecommon.js.InterceptResponse.Companion.ERROR
import io.rebble.libpebblecommon.permissions.DataUsageRecorder
import io.rebble.libpebblecommon.permissions.requestBytes
import kotlin.uuid.Uuid

interface HttpInterceptor {
    /** Answered from the phone's own data, without using the network. */
    val servedLocally: Boolean get() = false

    fun shouldIntercept(url: String): Boolean
    suspend fun onIntercepted(url: String, method: String, body: String?, appUuid: Uuid): InterceptResponse
}

data class InterceptResponse(
    val result: String,
    val status: Int,
) {
    companion object {
        val OK = InterceptResponse(result = "", status = 200)
        val ERROR = InterceptResponse(result = "", status = 500)
        val FORBIDDEN = InterceptResponse(result = "", status = 403)
    }
}

class InjectedPKJSHttpInterceptors(
    val interceptors: List<HttpInterceptor>,
)

class HttpInterceptorManager(
    private val interceptors: List<HttpInterceptor>,
    private val dataUsage: DataUsageRecorder,
) {
    constructor(
        timeline: RemoteTimelineEmulator,
        injectedHttpInterceptors: InjectedPKJSHttpInterceptors,
        dataUsage: DataUsageRecorder,
    ) : this(listOf(timeline) + injectedHttpInterceptors.interceptors, dataUsage)

    fun shouldIntercept(url: String): Boolean {
        return interceptors.any { it.shouldIntercept(url) }
    }

    suspend fun onIntercepted(url: String, method: String, body: String?, appUuid: Uuid): InterceptResponse {
        val interceptor = interceptors.firstOrNull { it.shouldIntercept(url) }
        if (interceptor == null) {
            return ERROR
        }
        val response = interceptor.onIntercepted(url, method, body, appUuid)
        if (!interceptor.servedLocally) {
            // Counted as the size of what the app is given: the interceptor's own requests aren't seen.
            val sent = requestBytes(method, url, emptyMap(), body?.encodeToByteArray()?.size?.toLong() ?: 0)
            dataUsage.record(appUuid, url, sent, response.result.encodeToByteArray().size.toLong(), requests = 1)
        }
        return response
    }
}