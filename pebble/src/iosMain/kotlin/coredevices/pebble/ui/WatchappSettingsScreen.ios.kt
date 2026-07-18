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
import platform.UIKit.UIScrollViewContentInsetAdjustmentBehavior
import platform.WebKit.WKWebsiteDataStore
import kotlin.uuid.Uuid

@OptIn(ExperimentalForeignApi::class)
internal actual fun webViewFactory(
    params: WebViewFactoryParam,
    uuid: Uuid,
    bridgeEnabled: Boolean,
    bridgeConfig: Map<String, String>,
    onBridgeClose: (String) -> Unit,
): NativeWebView =
    defaultWebViewFactory(params).apply {
        if (available(OS.Ios to OSVersion(17))) {
            configuration.websiteDataStore =
                WKWebsiteDataStore.dataStoreForIdentifier(NSUUID("$uuid"))
        } else {
            Logger.withTag("webViewFactory").w("dataStoreForIdentifier not available, using defaultDataStore")
            configuration.websiteDataStore =
                WKWebsiteDataStore.nonPersistentDataStore()
        }

        // Stop the webview from auto-adjusting its scroll inset when a text field is
        // focused. Compose renders through Metal and applies its own keyboard/IME
        // insets to the interop view; WKWebView's default inset adjustment fights
        // that, producing the violent up/down scrolling users see when tapping inputs
        // on PKJS settings pages (MOB-5387 / MOB-9386). Content scrolling still works.
        scrollView.contentInsetAdjustmentBehavior =
            UIScrollViewContentInsetAdjustmentBehavior.UIScrollViewContentInsetAdjustmentNever
    }

internal actual suspend fun restoreLocalStorage(webView: NativeWebView) {
    // no-op
}

internal actual fun persistLocalStorage(webView: NativeWebView) {
    // no-op
}