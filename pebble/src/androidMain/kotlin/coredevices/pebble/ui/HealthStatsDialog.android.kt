package coredevices.pebble.ui

actual fun Double.format(digits: Int): String =
    "%.${digits}f".format(this)
