package io.rebble.libpebblecommon.plugin

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryLevelDidChangeNotification

actual class PhoneBatteryMonitor {
    actual val batteryLevel: Flow<Int?> = callbackFlow {
        val device = UIDevice.currentDevice
        val previouslyMonitoring = device.batteryMonitoringEnabled
        if (!previouslyMonitoring) {
            device.batteryMonitoringEnabled = true
        }

        trySend(device.readPercent())

        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(
            name = UIDeviceBatteryLevelDidChangeNotification,
            `object` = null,
            queue = null,
        ) { _: NSNotification? ->
            trySend(device.readPercent())
        }

        awaitClose {
            center.removeObserver(observer)
            if (!previouslyMonitoring) {
                device.batteryMonitoringEnabled = false
            }
        }
    }.distinctUntilChanged()

    private fun UIDevice.readPercent(): Int? {
        val level = batteryLevel
        // -1 when monitoring isn't enabled or value isn't yet known.
        return if (level < 0f) null else (level * 100f).toInt().coerceIn(0, 100)
    }
}
