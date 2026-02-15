package io.rebble.libpebblecommon.js

import android.net.Uri
import android.webkit.JavascriptInterface
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class WebViewPrivatePKJSInterface(
    jsRunner: WebViewJsRunner,
    device: CompanionAppDevice,
    scope: CoroutineScope,
    outgoingAppMessages: MutableSharedFlow<AppMessageRequest>,
    logMessages: Channel<String>,
    jsTokenUtil: JsTokenUtil,
    remoteTimelineEmulator: RemoteTimelineEmulator,
    httpInterceptorManager: HttpInterceptorManager,
): PrivatePKJSInterface(jsRunner, device, scope, outgoingAppMessages, logMessages, jsTokenUtil, remoteTimelineEmulator, httpInterceptorManager) {

    companion object {
        private val logger = Logger.withTag(WebViewPrivatePKJSInterface::class.simpleName!!)
    }

    @JavascriptInterface
    fun startupScriptHasLoaded(data: String?) {
        logger.v { "Startup script has loaded: $data" }
        if (data == null) {
            logger.e { "Startup script has loaded, but data is null" }
            return
        }
        val uri = data.toUri()
        val params = uri.getQueryParameter("params")
        val paramsDecoded = Uri.decode(params)
        val paramsJson = Json.decodeFromString<Map<String, String>>(paramsDecoded)
        val jsUrl = paramsJson["loadUrl"] ?: run {
            logger.e { "No loadUrl in params" }
            return
        }
        scope.launch {
            jsRunner.loadAppJs(jsUrl)
        }
    }

    @JavascriptInterface
    override fun getTimelineTokenAsync(): String {
        return super.getTimelineTokenAsync()
    }

    @JavascriptInterface
    override fun logInterceptedSend() {
        super.logInterceptedSend()
    }

    @JavascriptInterface
    override fun privateFnConfirmReadySignal(success: Boolean) {
        super.privateFnConfirmReadySignal(success)
    }

    @JavascriptInterface
    override fun sendAppMessageString(jsonAppMessage: String): Int {
        return super.sendAppMessageString(jsonAppMessage)
    }

    @JavascriptInterface
    override fun privateLog(message: String) {
        super.privateLog(message)
    }

    @JavascriptInterface
    override fun onConsoleLog(level: String, message: String, source: String?) {
        val sourceLine = source?.split("\n")?.getOrNull(3)?.trim()
        super.onConsoleLog(level, message, sourceLine)
    }

    @JavascriptInterface
    override fun onError(message: String?, source: String?, line: Double?, column: Double?) {
        super.onError(message, source, line, column)
    }

    @JavascriptInterface
    override fun onUnhandledRejection(reason: String) {
        super.onUnhandledRejection(reason)
    }

    @JavascriptInterface
    override fun getVersionCode(): Int {
        return super.getVersionCode()
    }

    @JavascriptInterface
    override fun signalAppScriptLoadedByBootstrap() {
        super.signalAppScriptLoadedByBootstrap()
    }

    @JavascriptInterface
    override fun getActivePebbleWatchInfo(): String {
        return super.getActivePebbleWatchInfo()
    }

    @JavascriptInterface
    override fun shouldIntercept(url: String): Boolean {
        return super.shouldIntercept(url)
    }

    @JavascriptInterface
    override fun onIntercepted(callbackId: String, url: String, method: String, body: String?) {
        return super.onIntercepted(callbackId, url, method, body)
    }

    @JavascriptInterface
    override fun insertTimelinePin(pinJson: String) {
        super.insertTimelinePin(pinJson)
    }

    @JavascriptInterface
    override fun deleteTimelinePin(id: String) {
        super.deleteTimelinePin(id)
    }
}
