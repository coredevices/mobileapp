package coredevices.pebble.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.multiplatform.webview.web.AccompanistWebChromeClient
import com.multiplatform.webview.web.AccompanistWebViewClient
import com.multiplatform.webview.web.NativeWebView
import com.multiplatform.webview.web.PlatformWebViewParams
import com.multiplatform.webview.web.WebViewFactoryParam
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import co.touchlab.kermit.Logger
import com.multiplatform.webview.web.defaultWebViewFactory
import coredevices.pebble.config.CONFIG_NATIVE_NAMESPACE
import coredevices.pebble.config.CONFIG_PAGE_SHIM_JS
import coredevices.pebble.config.ConfigPageBridge
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.js.WebViewJSLocalStorageInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.Path
import kotlin.uuid.Uuid

internal actual fun webViewFactory(
    params: WebViewFactoryParam,
    uuid: Uuid,
    bridge: ConfigPageBridge?,
    mayLoad: (String) -> Boolean,
    network: ConfigPageNetwork,
): NativeWebView = defaultWebViewFactory(params).apply {
    addJavascriptInterface(ConfigNetwork(mayLoad), CONFIG_NETWORK_NAMESPACE)
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        // Iframes inherit the policy, so their fresh WebSocket is limited too.
        network.csp?.let { WebViewCompat.addDocumentStartJavaScript(this, cspJs(it), setOf("*")) }
        WebViewCompat.addDocumentStartJavaScript(this, CONFIG_NETWORK_JS, setOf("*"))
    }
    if (bridge != null) {
        addJavascriptInterface(NativeConfigBridge(bridge), CONFIG_NATIVE_NAMESPACE)
        bridge.attach { js -> post { evaluateJavascript(js, null) } }
        // Runs before the page's own <script>, which is the whole point: pages call Pebble.* from
        // the top level. Needs WebView 83+; without it the page has to wait for onPageStarted.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(this, CONFIG_PAGE_SHIM_JS, setOf("*"))
        } else {
            Logger.withTag("webViewFactory").w("No DOCUMENT_START_SCRIPT; config API lands late")
        }
    }
    // Don't store the webview state (which includes localstorage) in bundle - can be too large
    isSaveEnabled = false
    val localStorageInterface = WebViewJSLocalStorageInterface("$uuid-config", AppContext(context)) {
        runBlocking(Dispatchers.Main) {
            evaluateJavascript(
                it,
                null
            )
        }
    }
    addJavascriptInterface(localStorageInterface, "_localStorage")
    settings.domStorageEnabled = true
    settings.databasePath = Path(context.filesDir.path, "watchapp_settings/$uuid").toString()
}

private const val CONFIG_NETWORK_NAMESPACE = "_PebbleConfigNet"

internal actual class ConfigPageNetwork(val csp: String?)

internal actual suspend fun configPageNetwork(uuid: Uuid, domains: List<String>?) = ConfigPageNetwork(
    domains?.let { granted ->
        val sources = granted.flatMap { domain ->
            listOf("https", "wss", "http", "ws").flatMap { scheme -> listOf("$scheme://$domain", "$scheme://*.$domain") }
        }
        "connect-src ${sources.ifEmpty { listOf("'none'") }.joinToString(" ")}"
    }
)

private fun cspJs(csp: String) = """
    (function () {
      var meta = document.createElement('meta');
      meta.httpEquiv = 'Content-Security-Policy';
      meta.content = ${Json.encodeToString(String.serializer(), csp)};
      var head = document.head;
      if (!head) {
        head = document.createElement('head');
        document.documentElement.prepend(head);
      }
      head.prepend(meta);
    })();
""".trimIndent()

// shouldInterceptRequest never sees WebSockets or WebRTC.
private val CONFIG_NETWORK_JS = """
    (function (global) {
      if (typeof $CONFIG_NETWORK_NAMESPACE === 'undefined') return;
      ['RTCPeerConnection', 'webkitRTCPeerConnection'].forEach(function (key) {
        try { delete global[key]; } catch (e) {}
      });
      var NativeWebSocket = global.WebSocket;
      if (!NativeWebSocket) return;
      var GatedWebSocket = function (url, protocols) {
        var absolute = new URL(String(url), global.location.href).href;
        if (!$CONFIG_NETWORK_NAMESPACE.allowed(absolute)) {
          throw new DOMException('This page may not connect to ' + absolute, 'SecurityError');
        }
        return protocols === undefined ? new NativeWebSocket(url) : new NativeWebSocket(url, protocols);
      };
      GatedWebSocket.prototype = NativeWebSocket.prototype;
      Object.defineProperty(NativeWebSocket.prototype, 'constructor', { value: GatedWebSocket });
      ['CONNECTING', 'OPEN', 'CLOSING', 'CLOSED'].forEach(function (key) { GatedWebSocket[key] = NativeWebSocket[key]; });
      global.WebSocket = GatedWebSocket;
    })(globalThis);
""".trimIndent()

private class ConfigNetwork(private val mayLoad: (String) -> Boolean) {
    @JavascriptInterface
    fun allowed(url: String) = mayLoad(url)
}

private class NativeConfigBridge(private val bridge: ConfigPageBridge) {
    @JavascriptInterface
    fun call(json: String) = bridge.onCall(json)
}

internal actual suspend fun restoreLocalStorage(webView: NativeWebView) {
    withContext(Dispatchers.Main) {
        webView.evaluateJavascript("""
            (function() {
                // Storage is disabled inside data: URLs, which a plugin's config page is.
                try { window.localStorage.clear(); } catch (e) { return; }
                const localStorageData = JSON.parse(window._localStorage.restoreState());
                for (const [key, value] of Object.entries(localStorageData)) {
                    window.localStorage.setItem(key, value);
                }
            })();
                """.trimIndent(), null
        )
    }
}

@Composable
internal actual fun rememberPlatformWebViewParams(mayLoad: (String) -> Boolean): PlatformWebViewParams? {
    val pending = remember { AtomicReference<ValueCallback<Array<Uri>>?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = parseFileChooserResult(result.resultCode, result.data)
        pending.getAndSet(null)?.onReceiveValue(uris)
    }
    return remember(launcher, mayLoad) {
        PlatformWebViewParams(
            client = object : AccompanistWebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val url = request?.url
                    val scheme = url?.scheme?.lowercase()
                    if ((scheme == "http" || scheme == "https") && !mayLoad(url.toString())) {
                        return WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), null)
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            },
            chromeClient = object : AccompanistWebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    val intent = fileChooserParams?.createIntent() ?: return false
                    return try {
                        launcher.launch(intent)
                        // Returning false leaves the callback to the webview, so only take
                        // ownership of it once the chooser is actually up.
                        pending.getAndSet(filePathCallback)?.onReceiveValue(null)
                        true
                    } catch (_: ActivityNotFoundException) {
                        false
                    }
                }
            }
        )
    }
}

private fun parseFileChooserResult(resultCode: Int, data: Intent?): Array<Uri>? {
    if (resultCode != Activity.RESULT_OK || data == null) return null
    val clip = data.clipData
    if (clip != null && clip.itemCount > 0) {
        return Array(clip.itemCount) { clip.getItemAt(it).uri }
    }
    return data.data?.let { arrayOf(it) }
}

internal actual fun persistLocalStorage(webView: NativeWebView) {
    runBlocking(Dispatchers.Main) {
        webView.evaluateJavascript("""
            (function() {
                const data = {};
                try { window.localStorage.length; } catch (e) { return; }
                for (let i = 0; i < window.localStorage.length; i++) {
                    const key = window.localStorage.key(i);
                    const value = window.localStorage.getItem(key);
                    data[key] = value;
                }
                window.localStorage.clear();
                window._localStorage.saveState(JSON.stringify(data));
            })();
                """.trimIndent(), null
        )
    }
}