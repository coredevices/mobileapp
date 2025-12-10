package coredevices.pebble.ui

import android.content.ClipData
import android.content.Context
import android.os.PersistableBundle
import androidx.compose.ui.platform.ClipEntry
import org.koin.mp.KoinPlatform

actual fun makeTokenClipEntry(token: String): ClipEntry = ClipEntry(ClipData.newPlainText("Token", token).apply {
    description.extras = PersistableBundle().apply {
        putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
})

actual fun getPlatformSTTLanguages(): List<Pair<String, String>> {
    return listOf("en-US" to "English (US)")
}