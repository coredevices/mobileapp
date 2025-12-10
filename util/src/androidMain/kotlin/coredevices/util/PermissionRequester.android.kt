package coredevices.util

actual fun Permission.requestIsFullScreen(): Boolean = when (this) {
    Permission.BackgroundLocation -> true
    else -> false
}