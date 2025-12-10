package coredevices.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun PebbleWebview(
    url: String,
    interceptor: PebbleWebviewUrlInterceptor,
    modifier: Modifier,
    onPageFinishedJavaScript: String? = null,
)

interface PebbleWebviewUrlInterceptor {
    fun onIntercept(url: String, navigator: PebbleWebviewNavigator): Boolean
    var navigator: PebbleWebviewNavigator?
}

interface PebbleWebviewNavigator {
    fun loadUrl(url: String)
    fun goBack(): Boolean
}
