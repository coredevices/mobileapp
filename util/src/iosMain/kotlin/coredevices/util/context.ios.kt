package coredevices.util

import PlatformUiContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController

@Composable
actual fun getAndroidActivity(): Any? = null

@Composable
actual fun rememberUiContext(): PlatformUiContext? {
    val viewController = LocalUIViewController.current
    return remember { PlatformUiContext(viewController) }
}