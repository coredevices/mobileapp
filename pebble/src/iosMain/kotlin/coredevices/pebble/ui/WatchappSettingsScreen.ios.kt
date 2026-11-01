package coredevices.pebble.ui

import androidx.compose.runtime.Composable
import co.touchlab.kermit.Logger
import com.multiplatform.webview.web.NativeWebView
import com.multiplatform.webview.web.PlatformWebViewParams
import com.multiplatform.webview.web.WebViewFactoryParam
import com.multiplatform.webview.web.defaultWebViewFactory
import coredevices.pebble.config.CONFIG_NATIVE_NAMESPACE
import coredevices.pebble.config.CONFIG_PAGE_SHIM_JS
import coredevices.pebble.config.ConfigPageBridge
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.skiko.OS
import org.jetbrains.skiko.OSVersion
import org.jetbrains.skiko.available
import platform.Foundation.NSUUID
import platform.UIKit.UIScrollViewContentInsetAdjustmentBehavior
import platform.WebKit.WKContentRuleList
import platform.WebKit.WKContentRuleListStore
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKUserScript
import platform.WebKit.WKUserScriptInjectionTime
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.uuid.Uuid

@OptIn(ExperimentalForeignApi::class)
internal actual fun webViewFactory(
    params: WebViewFactoryParam,
    uuid: Uuid,
    bridge: ConfigPageBridge?,
    mayLoad: (String) -> Boolean,
    network: ConfigPageNetwork,
): NativeWebView =
    defaultWebViewFactory(params).apply {
        network.rules?.let { configuration.userContentController.addContentRuleList(it) }
        // A content blocker doesn't cover WebRTC.
        configuration.userContentController.addUserScript(
            WKUserScript(
                source = "['RTCPeerConnection', 'webkitRTCPeerConnection'].forEach(function (k) { try { delete globalThis[k]; } catch (e) {} });",
                injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                forMainFrameOnly = false,
            )
        )
        if (bridge != null) {
            // Installed before the page parses, so a page's own <script> can call Pebble.* at the
            // top level rather than waiting for something to be injected after load.
            configuration.userContentController.addUserScript(
                WKUserScript(
                    source = CONFIG_PAGE_SHIM_JS,
                    injectionTime = WKUserScriptInjectionTime.WKUserScriptInjectionTimeAtDocumentStart,
                    forMainFrameOnly = true,
                )
            )
            configuration.userContentController.addScriptMessageHandler(
                NativeConfigBridge(bridge),
                CONFIG_NATIVE_NAMESPACE,
            )
            bridge.attach { js -> evaluateJavaScript(js, null) }
        }
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

internal actual class ConfigPageNetwork(val rules: WKContentRuleList?)

/**
 * WKWebView can't intercept http(s), so the page gets a content blocker: every http(s) and ws(s)
 * load is blocked except to the granted domains. Null rules if it can't be compiled, which
 * leaves the page unrestricted.
 */
internal actual suspend fun configPageNetwork(uuid: Uuid, domains: List<String>?): ConfigPageNetwork {
    val granted = domains ?: return ConfigPageNetwork(null)
    val rules = buildJsonArray {
        listOf("^https?://", "^wss?://").forEach { scheme ->
            add(rule(scheme, "block"))
            granted.forEach { domain ->
                add(rule("$scheme([^/:]+\\.)?${domain.replace(".", "\\.")}[:/]", "ignore-previous-rules"))
            }
        }
    }
    val store = WKContentRuleListStore.defaultStore() ?: return ConfigPageNetwork(null)
    val list = suspendCoroutine { cont ->
        store.compileContentRuleListForIdentifier("config-$uuid", rules.toString()) { list, error ->
            if (error != null) Logger.withTag("configPageNetwork").w { "Couldn't compile rules: ${error.localizedDescription}" }
            cont.resume(list)
        }
    }
    return ConfigPageNetwork(list)
}

private fun rule(urlFilter: String, action: String) = buildJsonObject {
    putJsonObject("trigger") { put("url-filter", urlFilter) }
    putJsonObject("action") { put("type", action) }
}

/** `_PebbleConfigNative.call(json)` on the page side; WebKit posts it here. */
private class NativeConfigBridge(
    private val bridge: ConfigPageBridge,
) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        (didReceiveScriptMessage.body as? String)?.let { bridge.onCall(it) }
    }
}

internal actual suspend fun restoreLocalStorage(webView: NativeWebView) {
    // no-op
}

internal actual fun persistLocalStorage(webView: NativeWebView) {
    // no-op
}

@Composable
internal actual fun rememberPlatformWebViewParams(mayLoad: (String) -> Boolean): PlatformWebViewParams? = null
