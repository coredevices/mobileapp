package coredevices.coreapp.ui.screens

import android.content.Context
import android.provider.Settings
import org.koin.mp.KoinPlatform
import java.io.File

actual fun isThirdPartyTest(): Boolean {
    val context = KoinPlatform.getKoin().get<Context>()
    return Settings.System.getString(context.contentResolver, "firebase.test.lab") == "true"
}

actual fun getExperimentalDebugInfoDirectory(): String {
    val context = KoinPlatform.getKoin().get<Context>()
    val path = File(context.cacheDir.absolutePath).resolve("haversine_debug")
    path.mkdirs()
    return path.absolutePath
}
