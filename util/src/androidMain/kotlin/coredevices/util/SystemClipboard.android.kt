package coredevices.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

class AndroidSystemClipboard(private val context: Context) : SystemClipboard {
    override fun setText(text: String) {
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText(null, text))
    }
}
