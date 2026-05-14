package coredevices.pebble.ui

import PlatformUiContext
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
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

actual fun openGoogleFitApp(uiContext: PlatformUiContext?) {
    val activity = uiContext?.activity ?: return
    val packageName = "com.google.android.apps.fitness"
    val launchIntent = activity.packageManager.getLaunchIntentForPackage(packageName)
    if (launchIntent != null) {
        activity.startActivity(launchIntent)
    } else {
        activity.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        )
    }
}