package coredevices.pebble.ui

import com.multiplatform.webview.web.NativeWebView
import com.multiplatform.webview.web.WebViewFactoryParam
import com.multiplatform.webview.web.defaultWebViewFactory
import coredevices.pebble.config.bridge.PebbleBridgeManager
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.io.rebble.libpebblecommon.js.WebViewJSLocalStorageInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.io.path.Path
import kotlin.uuid.Uuid

internal actual fun webViewFactory(
    params: WebViewFactoryParam,
    uuid: Uuid,
    bridgeEnabled: Boolean,
    bridgeConfig: Map<String, String>,
    onBridgeClose: (String) -> Unit,
): NativeWebView = defaultWebViewFactory(params).apply {
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

    if (bridgeEnabled) {
        val bridge = PebbleBridgeManager(
            context = context,
            appUuid = uuid.toString(),
            config = bridgeConfig,
            onClose = onBridgeClose,
        )
        bridge.attach(this)
    }
}

internal actual suspend fun restoreLocalStorage(webView: NativeWebView) {
    withContext(Dispatchers.Main) {
        webView.evaluateJavascript("""
            (function() {
                window.localStorage.clear();
                const localStorageData = JSON.parse(window._localStorage.restoreState());
                for (const [key, value] of Object.entries(localStorageData)) {
                    window.localStorage.setItem(key, value);
                }
            })();
                """.trimIndent(), null
        )
    }
}

internal actual fun persistLocalStorage(webView: NativeWebView) {
    runBlocking(Dispatchers.Main) {
        webView.evaluateJavascript("""
            (function() {
                const data = {};
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