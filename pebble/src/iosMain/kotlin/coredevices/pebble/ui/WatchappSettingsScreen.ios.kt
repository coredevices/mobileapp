package coredevices.pebble.ui

import co.touchlab.kermit.Logger
import com.multiplatform.webview.web.NativeWebView
import com.multiplatform.webview.web.WebViewFactoryParam
import com.multiplatform.webview.web.defaultWebViewFactory
import kotlinx.cinterop.ExperimentalForeignApi
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.OSVersion
import org.jetbrains.skiko.available
import platform.Foundation.NSUUID
import platform.WebKit.WKWebsiteDataStore
import kotlin.uuid.Uuid

@OptIn(ExperimentalForeignApi::class)
internal actual fun webViewFactory(params: WebViewFactoryParam, uuid: Uuid): NativeWebView =
    defaultWebViewFactory(params).apply {
        if (available(OS.Ios to OSVersion(17))) {
            configuration.websiteDataStore =
                WKWebsiteDataStore.dataStoreForIdentifier(NSUUID("$uuid"))
        } else {
            Logger.withTag("webViewFactory").w("dataStoreForIdentifier not available, using defaultDataStore")
            configuration.websiteDataStore =
                WKWebsiteDataStore.nonPersistentDataStore()
        }
    }

internal actual suspend fun restoreLocalStorage(webView: NativeWebView) {
    // no-op
}

internal actual fun persistLocalStorage(webView: NativeWebView) {
    // no-op
}