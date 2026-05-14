package coredevices.pebble.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import coredevices.libindex.device.KnownIndexDevice
import coredevices.util.Permission
import io.rebble.libpebblecommon.connection.AppContext
import java.io.ByteArrayOutputStream
import java.net.NetworkInterface

actual fun ImageBitmap.toPngBytes(): ByteArray {
    val out = ByteArrayOutputStream()
    asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
}

actual fun scanPermission(): Permission? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Permission.Bluetooth
    } else {
        Permission.Location
    }
}

actual fun getIPAddress(): Pair<String?, String?> {
    val v4 = NetworkInterface.getNetworkInterfaces()
        .asSequence()
        .flatMap { it.inetAddresses.asSequence() }
        .filter { !it.isLoopbackAddress && it.address.size == 4 }
        .map { it.hostAddress }
        .firstOrNull()
    val v6 = NetworkInterface.getNetworkInterfaces()
        .asSequence()
        .flatMap { it.inetAddresses.asSequence() }
        .filter { !it.isLoopbackAddress && it.address.size == 16 }
        .map { it.hostAddress?.substringBefore("%") }
        .firstOrNull()
    return Pair(v4, v6)
}

actual fun openSystemBluetoothSettings(appContext: AppContext) {
    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    try {
        appContext.context.startActivity(intent)
    } catch (e: Exception) {
        Logger.withTag("openSystemBluetoothSettings").e(e) { "Failed to open Bluetooth settings" }
    }
}

@Composable
actual fun RemovePairingMenuItem(
    ring: KnownIndexDevice,
    onShowRemoveDialog: () -> Unit,
    onHideMenu: () -> Unit
) {
    val context = LocalContext.current
    DropdownMenuItem(
        text = { Text("Remove in Android settings") },
        leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
        onClick = {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            try {
                context.startActivity(intent)
                onHideMenu()
            } catch (e: Exception) {
                Logger.withTag("RemovePairingMenuItem").e(e) { "Failed to open Bluetooth settings" }
                // If we can't open Bluetooth settings, fall back to the remove dialog
                onShowRemoveDialog()
            }
        },
    )
}