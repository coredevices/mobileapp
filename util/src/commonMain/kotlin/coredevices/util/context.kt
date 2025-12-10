package coredevices.util

import PlatformUiContext
import androidx.compose.runtime.Composable

@Composable
expect fun getAndroidActivity(): Any?

@Composable
expect fun rememberUiContext(): PlatformUiContext?