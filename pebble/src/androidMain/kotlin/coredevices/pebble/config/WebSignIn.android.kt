package coredevices.pebble.config

import PlatformUiContext
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

// Android WebViews share one process-global cookie jar, so the sign-in runs in a dedicated WebView
// profile with its own jar instead. Without multi-profile support the feature is disabled upstream
// ([webSignInSupported]), so performWebSignIn is never reached on a device that can't isolate it.
private val mutex = Mutex()

internal actual fun webSignInSupported(): Boolean =
    WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

private const val PROFILE = "web-sign-in"

internal actual suspend fun performWebSignIn(
    uiContext: PlatformUiContext,
    request: WebSignInRequest,
): Map<String, String>? = mutex.withLock {
    withContext(Dispatchers.Main) { capture(uiContext.activity, request) }
}

private suspend fun capture(activity: Activity, request: WebSignInRequest): Map<String, String>? =
    suspendCancellableCoroutine { cont ->
        var completed = false
        lateinit var view: WebSignInView
        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)

        fun finish(value: Map<String, String>?) {
            if (completed) return
            completed = true
            dialog.setOnCancelListener(null)
            dialog.dismiss()
            view.dispose()
            if (cont.isActive) cont.resume(value)
        }

        view = WebSignInView(activity, request, onCaptured = { finish(it) }, onCancelled = { finish(null) })
        dialog.setContentView(view.rootView)
        dialog.setOnCancelListener { finish(null) }
        dialog.show()
        view.load()
        cont.invokeOnCancellation { activity.runOnUiThread { finish(null) } }
    }

private class WebSignInView(
    private val activity: Activity,
    private val request: WebSignInRequest,
    private val onCaptured: (Map<String, String>) -> Unit,
    private val onCancelled: () -> Unit,
) {
    private val profile = ProfileStore.getInstance().getOrCreateProfile(PROFILE)
    private val cookies = profile.cookieManager
    private val handler = Handler(Looper.getMainLooper())
    private var finished = false
    private val poll = object : Runnable {
        override fun run() {
            if (!finished && !check()) handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }
    private val webView = createWebView()
    val rootView = createRootView()

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView() = WebView(activity).apply {
        WebViewCompat.setProfile(this, PROFILE)
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(this, true)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Run on the handler, not this callback stack: a hit tears the WebView down,
                // and destroying it from inside its own client callback crashes.
                handler.post { check() }
            }
        }
    }

    private fun createRootView(): LinearLayout {
        val top = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(24, 16, 24, 16)
            addView(Button(activity).apply { text = "Cancel"; setOnClickListener { cancel() } })
            addView(TextView(activity).apply {
                text = "Sign in to ${request.domain}"
                textSize = 16f
                setTextColor(Color.BLACK)
                setPadding(24, 0, 0, 0)
            })
        }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            isFocusableInTouchMode = true
            addView(top, LinearLayout.LayoutParams(-1, -2))
            addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    if (webView.canGoBack()) webView.goBack() else cancel()
                    true
                } else false
            }
        }
    }

    fun load() {
        rootView.requestFocus()
        // removeAllCookies is async; load only once it's done, or the wipe can race the page's
        // own Set-Cookie and delete the sign-in cookies we're waiting for.
        cookies.removeAllCookies {
            if (finished) return@removeAllCookies
            cookies.flush()
            profile.webStorage.deleteAllData()
            webView.loadUrl(request.url)
            handler.postDelayed(poll, POLL_INTERVAL_MS)
        }
    }

    private fun check(): Boolean {
        if (finished) return true
        val jar = parseCookies(cookies.getCookie("https://${request.domain}/"))
        if (request.cookies.all { it in jar }) {
            finished = true
            onCaptured(request.cookies.associateWith { jar.getValue(it) })
            return true
        }
        return false
    }

    private fun cancel() {
        if (finished) return
        finished = true
        onCancelled()
    }

    fun dispose() {
        finished = true
        handler.removeCallbacks(poll)
        cookies.removeAllCookies(null)
        cookies.flush()
        profile.webStorage.deleteAllData()
        webView.stopLoading()
        webView.loadUrl("about:blank")
        webView.destroy()
    }

    private fun parseCookies(header: String?): Map<String, String> =
        header.orEmpty().split(";").mapNotNull {
            val eq = it.indexOf('=')
            if (eq <= 0) null else it.substring(0, eq).trim() to it.substring(eq + 1).trim()
        }.toMap()

    private companion object {
        const val POLL_INTERVAL_MS = 500L
    }
}
