package coredevices.pebble.ui

import android.os.Build
import coredevices.util.Permission
import java.net.NetworkInterface

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