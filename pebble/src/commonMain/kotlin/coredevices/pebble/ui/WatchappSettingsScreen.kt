package coredevices.pebble.ui

import CoreNav
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import co.touchlab.kermit.Logger
import com.multiplatform.webview.request.RequestInterceptor
import com.multiplatform.webview.request.WebRequest
import com.multiplatform.webview.request.WebRequestInterceptResult
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.NativeWebView
import com.multiplatform.webview.web.PlatformWebViewParams
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewFactoryParam
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import coreapp.util.generated.resources.Res
import coreapp.util.generated.resources.back
import coredevices.pebble.config.ConfigPageBridge
import coredevices.pebble.config.ConfigPageSessions
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

private const val URL_DATA_PREFIX = "data:text/html;charset=utf-8,"

// The settings URL can be a large data: URI (hundreds of KB of HTML) which would cause
// TransactionTooLargeException if passed as a navigation route argument. Store it here
// in memory instead, keyed by watchIdentifier.
internal object WatchappSettingsUrlCache {
    private val urls = mutableMapOf<String, String>()
    fun put(watchIdentifier: String, url: String) { urls[watchIdentifier] = url }
    fun get(watchIdentifier: String): String? = urls[watchIdentifier]
    fun remove(watchIdentifier: String) { urls.remove(watchIdentifier) }
}
internal expect fun webViewFactory(
    params: WebViewFactoryParam,
    uuid: Uuid,
    /** Null leaves the page with no `Pebble` object, which is what a plain webview should be. */
    bridge: ConfigPageBridge?,
): NativeWebView

internal expect suspend fun restoreLocalStorage(webView: NativeWebView)
internal expect fun persistLocalStorage(webView: NativeWebView)

@Composable
internal expect fun rememberWebViewFileChooserParams(): PlatformWebViewParams?

private val logger = Logger.withTag("WatchappSettingsScreen")

@Composable
fun WatchappSettingsScreen(
    coreNav: CoreNav,
    /** What is being configured: a watchapp, a plugin, or a pbw that ships both. */
    uuid: String,
    title: String,
) {
    val url = remember(uuid) {
        normalizeWatchappSettingsUrl(WatchappSettingsUrlCache.get(uuid) ?: "")
    }
    DisposableEffect(uuid) {
        onDispose { WatchappSettingsUrlCache.remove(uuid) }
    }
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val sessions: ConfigPageSessions = koinInject()
        val session by remember(uuid, title) { sessions.sessionsFor(uuid, title) }
            .collectAsState(null)
        // Nothing left to configure — the watchapp stopped and there is no plugin behind it.
        LaunchedEffect(session) {
            if (session?.isOrphaned == true) {
                logger.d { "No scripts left for $uuid; closing settings" }
                withContext(Dispatchers.Main) { coreNav.goBack() }
            }
        }
        val webViewUuid = remember(uuid) { runCatching { Uuid.parse(uuid) }.getOrNull() }
        val fileChooserParams = rememberWebViewFileChooserParams()
        val state = rememberWebViewState(url) {
            androidWebSettings.domStorageEnabled = true
            iOSWebSettings.isInspectable = true
        }
        val interceptor = remember {
            SettingsRequestInterceptor(
                onSuccess = { data ->
                    runCatching { state.nativeWebView }.getOrNull()?.let { persistLocalStorage(it) }
                    session?.onPageClosed(data)
                    withContext(Dispatchers.Main) {
                        coreNav.goBack()
                    }
                },
                onError = {
                    runCatching { state.nativeWebView }.getOrNull()?.let { persistLocalStorage(it) }
                    withContext(Dispatchers.Main) {
                        coreNav.goBack()
                    }
                }
            )
        }
        val navigator = rememberWebViewNavigator(requestInterceptor = interceptor)
        val scope = rememberCoroutineScope()
        // One bridge for the life of the screen: it is installed when the WebView is built,
        // before any page has loaded, and picks up scripts as they resolve.
        val bridge = remember { if (sessions.enabled) ConfigPageBridge(scope) else null }
        DisposableEffect(session) {
            val open = session
            bridge?.scripts = open?.scripts.orEmpty()
            // Starting a plugin takes a moment; the page waits for its 'ready' event.
            val starting = scope.launch { open?.open { target -> bridge?.ready(target) } }
            onDispose {
                starting.cancel()
                open?.scripts?.keys?.forEach { bridge?.forget(it) }
                bridge?.scripts = emptyMap()
                scope.launch { open?.close() }
            }
        }
        LaunchedEffect(session) {
            session?.messages?.collect { (target, json) -> bridge?.push(target, json) }
        }
        LaunchedEffect(state.loadingState) {
            if (state.loadingState is LoadingState.Loading) {
                logger.d { "Page load finished, applying shims" }
                val nativeWebView = runCatching { state.nativeWebView }.getOrNull() ?: return@LaunchedEffect
                restoreLocalStorage(nativeWebView)
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("App Settings")
                            Text("Configuring $title", style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = coreNav::goBack) {
                            Icon(
                                Icons.AutoMirrored.Default.ArrowBack,
                                contentDescription = stringResource(
                                    Res.string.back
                                )
                            )
                        }
                    }
                )
            }
        ) { paddingValues ->
            webViewUuid?.let { uuid ->
                WebView(
                    state = state,
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    navigator = navigator,
                    platformWebViewParams = fileChooserParams,
                    factory = { webViewFactory(it, uuid, bridge) }
                )
            }
        }
    }
}

internal fun normalizeWatchappSettingsUrl(url: String): String {
    val parsed = runCatching { Url(url) }.getOrNull() ?: return url
    val host = parsed.host.lowercase()

    val isLegacyRawGitHost = host == "cdn.rawgit.com" || host == "rawgit.com"
    if (!isLegacyRawGitHost) return url

    return URLBuilder(parsed).apply {
        this.host = "raw.githack.com"
    }.buildString()
}

private class SettingsRequestInterceptor(
    private val onError: suspend () -> Unit,
    private val onSuccess: suspend (String) -> Unit,
) : RequestInterceptor {
    private val PREFIX = "pebblejs://close" // non-compliant intercept for close, because some apps just use "close"
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onInterceptUrlRequest(
        request: WebRequest,
        navigator: WebViewNavigator,
    ): WebRequestInterceptResult {
        if (!request.url.startsWith(PREFIX)) {
            return WebRequestInterceptResult.Allow
        }

        // Matches PREFIX followed by #, /, or /? 
        // Ideally all apps would send pebblejs://close#param1=value1&param2=value2 but it's not the case.
        // The original Pebble Technology Corp App did deviate from the official spec and handled these wrong cases too.
        val closeUrlRegex = Regex("""^pebblejs://close(?:#|/\?|/)(.*)$""")

        val data = closeUrlRegex.find(request.url)?.groupValues?.get(1)?.decodeURLPart()
        if (data?.isNotEmpty() == true) {
            scope.launch {
                delay(10)
                onSuccess(data)
            }
        } else {
            scope.launch {
                delay(10)
                onError()
            }
        }

        return WebRequestInterceptResult.Reject
    }
}
