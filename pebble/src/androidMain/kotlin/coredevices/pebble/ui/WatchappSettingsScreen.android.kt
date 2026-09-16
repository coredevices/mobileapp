package coredevices.pebble.ui

import android.content.ActivityNotFoundException
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.multiplatform.webview.web.AccompanistWebChromeClient
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
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.Path
import kotlin.uuid.Uuid

internal actual fun webViewFactory(
    params: WebViewFactoryParam,
    uuid: Uuid,
    bridge: ConfigPageBridge?,
): NativeWebView = defaultWebViewFactory(params).apply {
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
internal actual fun rememberWebViewFileChooserParams(): PlatformWebViewParams? {
    val pending = remember { AtomicReference<ValueCallback<Array<Uri>>?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        pending.getAndSet(null)?.onReceiveValue(uris)
    }
    return remember(launcher) {
        PlatformWebViewParams(
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