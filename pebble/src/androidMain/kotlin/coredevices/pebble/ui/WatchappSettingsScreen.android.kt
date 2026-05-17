package coredevices.pebble.ui

import android.content.ActivityNotFoundException
import android.net.Uri
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
import com.multiplatform.webview.web.defaultWebViewFactory
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
    uuid: Uuid
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
                    pending.getAndSet(filePathCallback)?.onReceiveValue(null)
                    val intent = fileChooserParams?.createIntent() ?: return false
                    return try {
                        launcher.launch(intent)
                        true
                    } catch (e: ActivityNotFoundException) {
                        pending.getAndSet(null)?.onReceiveValue(null)
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