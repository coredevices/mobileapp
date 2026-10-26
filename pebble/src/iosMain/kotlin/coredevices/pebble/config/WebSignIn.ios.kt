package coredevices.pebble.config

import PlatformUiContext
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectZero
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSTimer
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.UIAction
import platform.UIKit.UIApplication
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UINavigationController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.navigationItem
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKUIDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.WebKit.WKWindowFeatures
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.coroutines.resume

// The sign-in WebView gets its own non-persistent data store, which is always available.
internal actual fun webSignInSupported(): Boolean = true

@OptIn(ExperimentalForeignApi::class)
internal actual suspend fun performWebSignIn(
    uiContext: PlatformUiContext,
    request: WebSignInRequest,
): Map<String, String>? = mutex.withLock {
    withContext(Dispatchers.Main) { capture(uiContext, request) }
}

private val mutex = Mutex()

@OptIn(ExperimentalForeignApi::class)
private suspend fun capture(uiContext: PlatformUiContext, request: WebSignInRequest): Map<String, String>? =
    suspendCancellableCoroutine { continuation ->
        var completed = false
        lateinit var navigationController: UINavigationController
        // A non-persistent store is isolated and empty, so the WebView starts clean and leaves
        // nothing behind when it is torn down.
        val configuration = WKWebViewConfiguration().apply {
            websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
        }
        val webView = WKWebView(frame = CGRectZero.readValue(), configuration = configuration)
        val pages = mutableListOf(webView)
        var uiDelegate: WKUIDelegateProtocol? = null
        var delegate: WKNavigationDelegateProtocol? = null
        var pollTimer: NSTimer? = null

        fun finish(value: Map<String, String>?) {
            if (completed) return
            completed = true
            pollTimer?.invalidate()
            pollTimer = null
            pages.forEach {
                it.stopLoading()
                it.navigationDelegate = null
                it.UIDelegate = null
            }
            pages.clear()
            uiDelegate = null
            delegate = null
            navigationController.dismissViewControllerAnimated(true, completion = null)
            if (continuation.isActive) continuation.resume(value)
        }

        fun check() {
            if (completed) return
            webView.configuration.websiteDataStore.httpCookieStore.getAllCookies { all ->
                if (completed) return@getAllCookies
                val found = mutableMapOf<String, String>()
                all?.forEach { entry ->
                    val cookie = entry as? NSHTTPCookie ?: return@forEach
                    if (cookie.name in request.cookies && domainMatches(request.domain, cookie.domain)) {
                        found[cookie.name] = cookie.value
                    }
                }
                if (request.cookies.all { it in found }) finish(found)
            }
        }

        delegate = object : NSObject(), WKNavigationDelegateProtocol {
            override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) = check()
        }
        webView.navigationDelegate = delegate

        val content = UIViewController(nibName = null, bundle = null).apply {
            view = webView
            title = "Sign in to ${request.domain}"
            // Set on the VC's own navigationItem, not navigationBar.topItem, which is nil until
            // the bar lays out — a full-screen modal with no Cancel would trap the user.
            navigationItem.leftBarButtonItem = UIBarButtonItem(
                primaryAction = UIAction.actionWithTitle(
                    title = "Cancel",
                    image = null,
                    identifier = null,
                    handler = { finish(null) },
                ),
            )
        }
        navigationController = UINavigationController(rootViewController = content).apply {
            modalPresentationStyle = UIModalPresentationFullScreen
            interactivePopGestureRecognizer?.enabled = false
        }
        fun closePopup(popup: WKWebView) {
            // The app-created root stays open until cookie capture or explicit cancellation.
            if (popup == webView || !pages.remove(popup)) return
            popup.stopLoading()
            popup.navigationDelegate = null
            popup.UIDelegate = null
            navigationController.setViewControllers(
                navigationController.viewControllers.filter { (it as UIViewController).view != popup },
                animated = false,
            )
        }

        uiDelegate = object : NSObject(), WKUIDelegateProtocol {
            override fun webView(
                webView: WKWebView,
                createWebViewWithConfiguration: WKWebViewConfiguration,
                forNavigationAction: WKNavigationAction,
                windowFeatures: WKWindowFeatures,
            ): WKWebView? {
                if (completed) return null
                val popup = WKWebView(frame = CGRectZero.readValue(), configuration = createWebViewWithConfiguration)
                popup.UIDelegate = this
                popup.navigationDelegate = delegate
                pages.add(popup)
                val popupContent = UIViewController(nibName = null, bundle = null).apply {
                    view = popup
                    title = "Sign in to ${request.domain}"
                    navigationItem.leftBarButtonItem = UIBarButtonItem(
                        primaryAction = UIAction.actionWithTitle(
                            title = "Back", image = null, identifier = null,
                            handler = { closePopup(popup) },
                        ),
                    )
                    navigationItem.rightBarButtonItem = UIBarButtonItem(
                        primaryAction = UIAction.actionWithTitle(
                            title = "Cancel", image = null, identifier = null,
                            handler = { finish(null) },
                        ),
                    )
                }
                navigationController.pushViewController(popupContent, animated = false)
                // WKUIDelegate: WebKit loads the request in the returned web view.
                return popup
            }

            override fun webViewDidClose(webView: WKWebView) = closePopup(webView)
        }
        webView.UIDelegate = uiDelegate
        pollTimer = NSTimer.scheduledTimerWithTimeInterval(interval = POLL_INTERVAL_SECONDS, repeats = true) { check() }
        continuation.invokeOnCancellation {
            dispatch_async(dispatch_get_main_queue()) { finish(null) }
        }

        NSURL.URLWithString(request.url)?.let { webView.loadRequest(NSURLRequest(it)) }
        topmostViewController(uiContext.viewController)
            .presentViewController(navigationController, animated = true, completion = null)
    }

@OptIn(ExperimentalForeignApi::class)
private fun topmostViewController(fallback: UIViewController): UIViewController {
    var best: UIWindow? = null
    for (candidate in UIApplication.sharedApplication.windows) {
        val window = candidate as? UIWindow ?: continue
        if (window.hidden) continue
        if (best == null || window.windowLevel > best!!.windowLevel) best = window
    }
    var current = best?.rootViewController ?: fallback
    while (true) current = current.presentedViewController ?: return current
}

private const val POLL_INTERVAL_SECONDS = 0.5
