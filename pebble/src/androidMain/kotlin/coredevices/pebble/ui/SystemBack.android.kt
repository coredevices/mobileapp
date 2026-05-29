package coredevices.pebble.ui

import PlatformUiContext

actual fun moveCurrentTaskToBackground(uiContext: PlatformUiContext?): Boolean {
    val activity = uiContext?.activity ?: return false
    activity.moveTaskToBack(true)
    return true
}
