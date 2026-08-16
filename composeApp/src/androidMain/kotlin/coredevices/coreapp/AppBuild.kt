package coredevices.coreapp

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build

// The KMP Android library plugin generates no BuildConfig, so read the same values off the package.

val Context.appVersionName: String
    get() = packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"

val Context.appVersionCode: Long
    get() = packageManager.getPackageInfo(packageName, 0).let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            it.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            it.versionCode.toLong()
        }
    }

val Context.isDebuggableBuild: Boolean
    get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
