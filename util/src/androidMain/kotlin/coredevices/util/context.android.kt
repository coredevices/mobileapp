package coredevices.util

import PlatformUiContext
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import findActivity

@Composable
actual fun getAndroidActivity(): Any? = LocalContext.current.findActivity()

@Composable
actual fun rememberUiContext(): PlatformUiContext? {
    val activity = LocalActivity.current
    return remember(activity) {
        activity?.let { PlatformUiContext(activity) }
    }
}