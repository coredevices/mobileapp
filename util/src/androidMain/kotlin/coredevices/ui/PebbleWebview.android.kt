package coredevices.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.multiplatform.webview.request.RequestInterceptor
import com.multiplatform.webview.request.WebRequest
import com.multiplatform.webview.request.WebRequestInterceptResult
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.WebViewNavigator
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState

@Composable
actual fun PebbleWebview(
    url: String,
    interceptor: PebbleWebviewUrlInterceptor,
    modifier: Modifier,
    onPageFinishedJavaScript: String?,
) {
    val state = rememberWebViewState(url)

    DisposableEffect(Unit) {
        state.webSettings.apply {
            isJavaScriptEnabled = true
            androidWebSettings.apply {
                domStorageEnabled = true
            }
        }
        onDispose { }
    }

    val int = object : RequestInterceptor {
        override fun onInterceptUrlRequest(
            request: WebRequest,
            navigator: WebViewNavigator,
        ): WebRequestInterceptResult {
            val pebbleNavigator = object : PebbleWebviewNavigator {
                override fun loadUrl(url: String) {
                    navigator.loadUrl(url)
                }

                override fun goBack(): Boolean {
                    if (navigator.canGoBack) {
                        navigator.navigateBack()
                        return true
                    } else {
                        return false
                    }
                }
            }
            interceptor.navigator = pebbleNavigator
            return if (interceptor.onIntercept(request.url, pebbleNavigator)) {
                WebRequestInterceptResult.Allow
            } else {
                WebRequestInterceptResult.Reject
            }
        }
    }
    val navigator = rememberWebViewNavigator(requestInterceptor = int)
    
    // Execute JavaScript when page finishes loading
    LaunchedEffect(state.loadingState, onPageFinishedJavaScript) {
        if (state.loadingState is LoadingState.Finished && onPageFinishedJavaScript != null) {
            navigator.evaluateJavaScript(onPageFinishedJavaScript)
        }
    }
    
    WebView(
        state = state,
        navigator = navigator,
        modifier = modifier,
    )
}