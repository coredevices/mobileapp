package coredevices.util

actual fun Permission.requestIsFullScreen(): Boolean = when (this) {
    Permission.BackgroundLocation -> true
    Permission.MediaRouting -> true
    else -> false
}