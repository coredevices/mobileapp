package coredevices.ring

import co.touchlab.kermit.Logger
import coredevices.ring.database.Preferences
import coredevices.ring.service.RingBackgroundManager
import coredevices.util.Permission
import platform.CoreBluetooth.CBCentralManager
import platform.UserNotifications.UNUserNotificationCenter

actual class RingDelegate(
    private val ringBackgroundManager: RingBackgroundManager
) {
    companion object {
        private val logger = Logger.withTag("RingDelegate")
    }
    /**
     * Called by activity onCreate / didFinishLaunching to initialize the Ring module.
     */
    actual suspend fun init() {
        ringBackgroundManager.startBackgroundIfEnabled()
    }

    actual fun requiredRuntimePermissions(): Set<Permission> {
        return setOf(Permission.Reminders)
    }
}