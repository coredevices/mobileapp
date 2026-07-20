package coredevices.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLErrorCancelled
import platform.Foundation.NSURLRequest
import platform.UIKit.UIUserInterfaceStyle
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.javaScriptEnabled
import platform.darwin.NSObject
import theme.currentColorScheme

private val logger = Logger.withTag("PebbleWebview")

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PebbleWebview(
    url: String,
    interceptor: PebbleWebviewUrlInterceptor,
    modifier: Modifier,
    onPageFinishedJavaScript: String?,
    onPageError: ((message: String) -> Unit)?,
) {
    val currentInterceptor by rememberUpdatedState(interceptor)
    val currentOnPageError by rememberUpdatedState(onPageError)

    val navigationDelegate = remember {
        object : NSObject(), WKNavigationDelegateProtocol {
            override fun webView(
                webView: WKWebView,
                decidePolicyForNavigationAction: WKNavigationAction,
                decisionHandler: (WKNavigationActionPolicy) -> Unit
            ) {
                val request = decidePolicyForNavigationAction.request
                val requestUrlString = request.URL?.absoluteString
                val pebbleNavigator = object : PebbleWebviewNavigator {
                    override fun loadUrl(url: String) {
                        val nsUrl = NSURL.URLWithString(url)
                        if (nsUrl == null) {
                            logger.w { "Couldn't create NSURL for $url" }
                            return
                        }
                        webView.loadRequest(NSURLRequest(nsUrl))
                    }

                    override fun goBack(): Boolean {
                        if (webView.canGoBack) {
                            webView.goBack()
                            return true
                        } else {
                            return false
                        }
                    }

                    override fun reload() {
                        webView.reload()
                    }
                }
                interceptor.navigator = pebbleNavigator

                if (requestUrlString != null && currentInterceptor.onIntercept(requestUrlString, pebbleNavigator)) {
                    decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
                } else {
                    decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
                }
            }
            
            override fun webView(
                webView: WKWebView,
                didFinishNavigation: platform.WebKit.WKNavigation?
            ) {
                // Execute JavaScript when page finishes loading
                onPageFinishedJavaScript?.let { js ->
                    webView.evaluateJavaScript(js) { _, _ -> }
                }
            }

            // Failure after the response started arriving (e.g. content decoding error).
            // Both fail callbacks map to the same Kotlin signature, so disambiguate by selector.
            @ObjCSignatureOverride
            override fun webView(
                webView: WKWebView,
                didFailNavigation: platform.WebKit.WKNavigation?,
                withError: NSError
            ) {
                reportError(withError)
            }

            // Failure before any response (the common case: connection lost, DNS, server
            // unreachable, TLS failure). This is what fires on the network blips that
            // previously left a silent white screen.
            @ObjCSignatureOverride
            override fun webView(
                webView: WKWebView,
                didFailProvisionalNavigation: platform.WebKit.WKNavigation?,
                withError: NSError
            ) {
                reportError(withError)
            }

            override fun webViewWebContentProcessDidTerminate(webView: WKWebView) {
                logger.w { "WebView content process terminated" }
                currentOnPageError?.invoke("The page stopped responding")
            }

            private fun reportError(error: NSError) {
                // Ignore cancellations — e.g. our own interceptor rejecting a navigation,
                // or a load superseded by a newer one. These aren't real failures.
                if (error.code == NSURLErrorCancelled) return
                logger.w { "WebView navigation failed: ${error.localizedDescription} (code=${error.code})" }
                currentOnPageError?.invoke(error.localizedDescription)
            }
        }
    }

    val webView = remember {
        // Create and configure WKWebViewConfiguration
        val configuration = WKWebViewConfiguration().apply {
            preferences.javaScriptEnabled = true
            // Enable persistent storage for localStorage support
            websiteDataStore = platform.WebKit.WKWebsiteDataStore.defaultDataStore()
        }
        WKWebView(frame = CGRectZero.readValue(), configuration = configuration).apply {
            this.navigationDelegate = navigationDelegate
        }
    }

    // Mirror the in-app theme to the webview so prefers-color-scheme inside the page
    // matches the app's App Theme setting, not the OS setting.
    val colorScheme = currentColorScheme()
    LaunchedEffect(colorScheme) {
        webView.overrideUserInterfaceStyle = if (colorScheme.isDark) {
            UIUserInterfaceStyle.UIUserInterfaceStyleDark
        } else {
            UIUserInterfaceStyle.UIUserInterfaceStyleLight
        }
    }

    UIKitView(
        factory = {
            // This is called once to create the WKWebView instance
            webView
        },
        modifier = modifier,
        update = { view ->
            // This is called whenever 'url' or other state passed to UIKitView changes
            // 'view' here is the WKWebView instance created in factory
            val nsUrl = NSURL.URLWithString(url)
            if (nsUrl != null) {
                val request = NSURLRequest(nsUrl)
                view.loadRequest(request)
            }
        }
    )
}