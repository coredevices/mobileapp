package coredevices.ring

import android.content.Context
import android.content.pm.PackageManager
import org.koin.mp.KoinPlatform

actual fun isBeeperAvailable(): Boolean {
    val context: Context = KoinPlatform.getKoin().get()
    val packageManager = context.packageManager
    return try {
        packageManager.getPackageInfo("com.beeper.android", 0)
        true
    } catch (e: Exception) {
        false
    }
}