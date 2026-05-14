package io.rebble.libpebblecommon.time

import io.rebble.libpebblecommon.connection.AppContext
import platform.Foundation.NSNotificationCenter

class IosTimeChangedReceiver() : TimeChanged {
    override fun registerForTimeChanges(onChanged: () -> Unit) {
        NSNotificationCenter.defaultCenter()
            .addObserverForName(
                name = "NSSystemClockDidChangeNotification",
                `object` = null,
                queue = null
            ) {
                onChanged()
            }
        NSNotificationCenter.defaultCenter()
            .addObserverForName(
                name = "NSSystemTimeZoneDidChangeNotification",
                `object` = null,
                queue = null
            ) {
                onChanged()
            }
    }
}

actual fun createTimeChanged(appContext: AppContext): TimeChanged = IosTimeChangedReceiver()