package io.rebble.libpebblecommon.connection

import android.bluetooth.BluetoothAdapter
import android.companion.CompanionDeviceManager
import android.os.Build
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.LibPebbleAnalytics
import io.rebble.libpebblecommon.di.logWatchEvent
import io.rebble.libpebblecommon.metadata.WatchColor

private val logger = Logger.withTag("ConnectionFailureHandler.android")

actual fun AppContext.handleMtuGattError(identifier: PebbleIdentifier, color: WatchColor, analytics: LibPebbleAnalytics) {
    logger.i { "handleMtuGattError" }
    analytics.logWatchEvent(color, "workaround.mtu-gatt-error.unpair")
//    unpairDevice(identifier)
}

actual fun AppContext.handleGattInsufficientAuth(identifier: PebbleIdentifier, color: WatchColor, analytics: LibPebbleAnalytics) {
    logger.i { "handleGattInsufficientAuth" }
    analytics.logWatchEvent(color, "workaround.gatt-insuff-auth.unpair")
//    unpairDevice(identifier)
}

actual fun AppContext.handleCreateBondFailed(identifier: PebbleIdentifier, color: WatchColor, analytics: LibPebbleAnalytics) {
    logger.i { "handleCreateBondFailed" }
    analytics.logWatchEvent(color, "workaround.create-bond-failed.unpair")
//    unpairDevice(identifier)
}

private fun AppContext.unpairDevice(identifier: PebbleIdentifier) {
    val macAddress = when (identifier) {
        is PebbleBleIdentifier -> identifier.macAddress
        is PebbleBtClassicIdentifier -> identifier.macAddress
        else -> return
    }
    val service = context.getSystemService(CompanionDeviceManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        val association = service.myAssociations.firstOrNull {
            it.deviceMacAddress?.toString().equals(macAddress, ignoreCase = true)
        }
        val associationId = association?.id
        logger.v { "CompanionDeviceManager unpairDevice: associationId=$associationId" }
        if (associationId != null) {
            try {
                val result = service.removeBond(associationId)
                logger.d { "CompanionDeviceManager removeBond result=$result" }
                if (result) {
                    return
                }
            } catch (e: SecurityException) {
                logger.e(e) { "Error removing pairing using CompanionDeviceManager" }
            }
        }
    }

    // Resort to reflection hack
    logger.i { "Using reflection to remove bond" }
    try {
        val device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(macAddress)
        if (device == null) {
            return
        }
        device::class.java.getMethod("removeBond").invoke(device)
    } catch (e: Exception) {
        logger.e(e) { "Error calling removeBond using reflection" }
    }
}
