package coredevices.coreapp.push

import PlatformContext
import android.annotation.SuppressLint
import android.provider.Settings

actual fun PlatformContext.getDeviceId(): String {
    @SuppressLint("HardwareIds")
    return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
}

