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
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewFactoryParam
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import coreapp.util.generated.resources.Res
import coreapp.util.generated.resources.back
import coredevices.pebble.rememberLibPebble
import io.ktor.http.decodeURLPart
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.Uuid

private const val URL_DATA_PREFIX = "data:text/html;charset=utf-8,"
internal expect fun webViewFactory(
    params: WebViewFactoryParam,
    uuid: Uuid
): NativeWebView

internal expect suspend fun restoreLocalStorage(webView: NativeWebView)
internal expect fun persistLocalStorage(webView: NativeWebView)

@OptIn(ExperimentalCoroutinesApi::class)
@Composable
fun WatchappSettingsScreen(
    coreNav: CoreNav,
    watchIdentifier: String,
    title: String,
    url: String,
) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val libPebble = rememberLibPebble()
        val pkjsSessionFlow = remember(watchIdentifier) {
            libPebble.watches
                .flatMapLatest { watches ->
                    watches.filterIsInstance<ConnectedPebbleDevice>().firstOrNull { watch ->
                        watch.identifier.asString == watchIdentifier
                    }?.currentPKJSSession ?: emptyFlow()
                }
        }
        val pkjsSession by pkjsSessionFlow.collectAsState(null)
        val state = rememberWebViewState(url) {
            androidWebSettings.domStorageEnabled = true
            iOSWebSettings.isInspectable = true
        }
        val interceptor = remember {
            SettingsRequestInterceptor(
                onSuccess = { data ->
                    persistLocalStorage(state.nativeWebView)
                    pkjsSession?.triggerOnWebviewClosed(data) ?: run {
                        Logger.w { "No PKJS session found for $watchIdentifier, cannot handle webview close" }
                    }
                    withContext(Dispatchers.Main) {
                        coreNav.goBack()
                    }
                },
                onError = {
                    persistLocalStorage(state.nativeWebView)
                    withContext(Dispatchers.Main) {
                        coreNav.goBack()
                    }
                }
            )
        }
        val navigator = rememberWebViewNavigator(requestInterceptor = interceptor)
        LaunchedEffect(state.loadingState) {
            if (state.loadingState is LoadingState.Loading) {
                Logger.d("WatchappSettingsScreen") { "Page load finished, applying shims" }
                restoreLocalStorage(state.nativeWebView)
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
            pkjsSession?.uuid?.let { uuid ->
                WebView(
                    state = state,
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    navigator = navigator,
                    factory = { webViewFactory(it, uuid) }
                )
            }
        }
    }
}

private class SettingsRequestInterceptor(
    private val onError: suspend () -> Unit,
    private val onSuccess: suspend (String) -> Unit,
) : RequestInterceptor {
    private val PREFIX = "pebblejs://close#"
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onInterceptUrlRequest(
        request: WebRequest,
        navigator: WebViewNavigator,
    ): WebRequestInterceptResult {
        if (!request.url.startsWith(PREFIX)) {
            return WebRequestInterceptResult.Allow
        }
        val data = request.url.removePrefix(PREFIX).decodeURLPart()
        if (data.isNotEmpty()) {
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