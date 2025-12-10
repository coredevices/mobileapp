package coredevices.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.javaScriptEnabled
import platform.darwin.NSObject

private val logger = Logger.withTag("PebbleWebview")

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PebbleWebview(
    url: String,
    interceptor: PebbleWebviewUrlInterceptor,
    modifier: Modifier,
    onPageFinishedJavaScript: String?,
) {
    val currentInterceptor by rememberUpdatedState(interceptor)

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