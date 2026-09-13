package io.rebble.libpebblecommon.music

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.PermissionChecker

fun Context.hasMediaRoutingControlPermission(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        return false
    }
    val appOp = AppOpsManager.permissionToOp(Manifest.permission.MEDIA_ROUTING_CONTROL)
        ?: return false
    val mode = getSystemService(AppOpsManager::class.java)?.checkOpNoThrow(
        appOp,
        applicationInfo.uid,
        packageName,
    ) ?: AppOpsManager.MODE_DEFAULT
    val permissionGranted = PermissionChecker.checkSelfPermission(
        this,
        Manifest.permission.MEDIA_ROUTING_CONTROL,
    ) == PackageManager.PERMISSION_GRANTED
    return isMediaRoutingControlGranted(mode, permissionGranted)
}

internal fun isMediaRoutingControlGranted(appOpMode: Int, permissionGranted: Boolean): Boolean =
    appOpMode == AppOpsManager.MODE_ALLOWED ||
            appOpMode == AppOpsManager.MODE_DEFAULT && permissionGranted
