package coredevices.ring.agent.builtin_servlets.js

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AndroidWebviewJsEngine(private val context: Context): JsEngine {
    companion object {
        private val logger = Logger.withTag(AndroidWebviewJsEngine::class.simpleName!!)
    }
    private val logsChannel = Channel<String>(Channel.BUFFERED)
    private val scope = CoroutineScope(Dispatchers.Default)
    @SuppressLint("SetJavaScriptEnabled")
    private val webView = scope.async(Dispatchers.Main) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.loadsImagesAutomatically = false

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    logger.d { "JS: ${consoleMessage.message()}" }
                    logsChannel.trySend("${consoleMessage.messageLevel()} ${consoleMessage.message()}")
                    return true
                }
            }
            loadUrl("about:blank")
        }
    }
    override suspend fun evaluate(js: String): String {
        val result = CompletableDeferred<String?>()
        val logs = mutableListOf<String>()
        val job = scope.launch {
            while (isActive) {
                for (log in logsChannel) {
                    logs.add(log)
                }
            }
        }
        val sanitizedJs = js.split("\n").map {
            if (it.contains("//"))
                it.substring(0, it.indexOf("//"))
            else
                it
        }
        val jsToEvaluate = "(function() { try { $js } catch (e) { console.error(e); } })();"
        withContext(Dispatchers.Main) {
            webView.await().evaluateJavascript(jsToEvaluate) {
                result.complete(it)
            }
        }
        val evaluation = result.await()
        job.cancel()
        return buildString {
            append(logs.joinToString("\n"))
            if (!evaluation.isNullOrBlank() && evaluation != "null") {
                append("\n")
                append(evaluation)
            }
        }
    }
}