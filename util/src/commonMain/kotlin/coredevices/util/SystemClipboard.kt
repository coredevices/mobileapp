package coredevices.util

/** Writes to the system clipboard from outside the UI, where Compose's clipboard isn't available. */
fun interface SystemClipboard {
    fun setText(text: String)
}
