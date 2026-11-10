package coredevices.pebble.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
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
        WebViewCompat.addDocumentStartJavaScript(this, configNetworkJs(context), setOf("*"))
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

internal actual class ConfigPageNetwork(val csp: String?) {
    // Without document-start scripts there's no CSP and no WebSocket/WebRTC gate.
    actual val enforced = csp == null || WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
}

internal actual suspend fun configPageNetwork(uuid: Uuid, domains: List<String>?) = ConfigPageNetwork(
    domains?.let { granted ->
        val sources = granted.flatMap { domain ->
            listOf("https", "wss", "http", "ws").flatMap { scheme -> listOf("$scheme://$domain", "$scheme://*.$domain") }
        }
        // CSP is checked again on every redirect, which shouldInterceptRequest never sees. Frames
        // inherit it, so a window the page reaches before realm_guard.js can't open a WebSocket either.
        // <object>/<embed> make their window later, when nothing would check it. pebblejs: is how
        // some pages close.
        val allowed = sources.ifEmpty { listOf("'none'") }.joinToString(" ")
        "default-src $allowed 'unsafe-inline' 'unsafe-eval' data: blob:; connect-src $allowed; " +
            "frame-src $allowed; worker-src 'none'; object-src 'none'; form-action $allowed pebblejs:"
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

// shouldInterceptRequest never sees WebSockets, WebRTC or workers.
private fun configNetworkJs(context: Context) =
    context.assets.open("realm_guard.js").bufferedReader().use { it.readText() } + """
        if (typeof $CONFIG_NETWORK_NAMESPACE !== 'undefined') {
          __pebbleGuardRealms(globalThis, $CONFIG_NETWORK_NAMESPACE.allowed.bind($CONFIG_NETWORK_NAMESPACE), false);
        }
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