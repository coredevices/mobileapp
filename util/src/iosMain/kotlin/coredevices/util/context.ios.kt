package coredevices.util

import PlatformUiContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun getAndroidActivity(): Any? = null

@Composable
actual fun rememberUiContext(): PlatformUiContext? {
    return remember { PlatformUiContext() }
}