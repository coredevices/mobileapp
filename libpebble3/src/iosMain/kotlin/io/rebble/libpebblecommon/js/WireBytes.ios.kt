package io.rebble.libpebblecommon.js

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.KtorNSURLSessionDelegate
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.util.collections.ConcurrentMap
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURLProtocol
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionDataDelegateProtocol
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskMetrics
import platform.Foundation.NSURLSessionTaskTransactionMetrics
import platform.Foundation.NSURLSessionWebSocketCloseCode
import platform.Foundation.NSURLSessionWebSocketDelegateProtocol
import platform.Foundation.NSURLSessionWebSocketTask
import platform.Foundation.setValue
import platform.Foundation.valueForHTTPHeaderField
import platform.darwin.NSObject
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.incrementAndFetch

// Moved off the request into an NSURLProtocol property before it's sent, so servers never see it.
private const val TAG = "X-Pebble-Wire-Bytes"
private val nextTag = AtomicLong(0)
private val measured = ConcurrentMap<String, Long>()

internal actual class WireBytes actual constructor() {
    private val tag = nextTag.incrementAndFetch().toString()

    actual fun tag(request: HttpRequestBuilder) {
        request.header(TAG, tag)
    }

    actual fun take(): Long? = measured.remove(tag)
}

internal actual fun createHttpClient(): HttpClient {
    val ktor = KtorNSURLSessionDelegate()
    val configuration = NSURLSessionConfiguration.defaultSessionConfiguration().apply { setHTTPCookieStorage(null) }
    val session = NSURLSession.sessionWithConfiguration(configuration, MetricsDelegate(ktor), delegateQueue = null)
    return HttpClient(Darwin) {
        install(WebSockets)
        engine {
            usePreconfiguredSession(session, ktor)
            configureRequest {
                valueForHTTPHeaderField(TAG)?.let {
                    NSURLProtocol.setProperty(it, forKey = TAG, inRequest = this)
                    setValue(null, forHTTPHeaderField = TAG)
                }
            }
        }
    }
}

/** Ktor's delegate is final, so this forwards every callback it implements and adds the metrics one. */
private class MetricsDelegate(private val ktor: KtorNSURLSessionDelegate) :
    NSObject(), NSURLSessionDataDelegateProtocol, NSURLSessionWebSocketDelegateProtocol {

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didFinishCollectingMetrics: NSURLSessionTaskMetrics) {
        val request = task.originalRequest ?: return
        val tag = NSURLProtocol.propertyForKey(TAG, inRequest = request) as? String ?: return
        val bytes = didFinishCollectingMetrics.transactionMetrics
            .sumOf { (it as NSURLSessionTaskTransactionMetrics).countOfResponseBodyBytesReceived }
        if (bytes > 0) measured[tag] = bytes
    }

    override fun URLSession(session: NSURLSession, dataTask: NSURLSessionDataTask, didReceiveData: NSData) {
        ktor.URLSession(session, dataTask = dataTask, didReceiveData = didReceiveData)
    }

    override fun URLSession(session: NSURLSession, task: NSURLSessionTask, didCompleteWithError: NSError?) {
        ktor.URLSession(session, task = task, didCompleteWithError = didCompleteWithError)
    }

    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        willPerformHTTPRedirection: NSHTTPURLResponse,
        newRequest: NSURLRequest,
        completionHandler: (NSURLRequest?) -> Unit,
    ) {
        ktor.URLSession(session, task, willPerformHTTPRedirection, newRequest, completionHandler)
    }

    override fun URLSession(session: NSURLSession, webSocketTask: NSURLSessionWebSocketTask, didOpenWithProtocol: String?) {
        ktor.URLSession(session, webSocketTask = webSocketTask, didOpenWithProtocol = didOpenWithProtocol)
    }

    override fun URLSession(
        session: NSURLSession,
        webSocketTask: NSURLSessionWebSocketTask,
        didCloseWithCode: NSURLSessionWebSocketCloseCode,
        reason: NSData?,
    ) {
        ktor.URLSession(session, webSocketTask, didCloseWithCode, reason)
    }
}
