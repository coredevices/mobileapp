package coredevices.util

import PlatformUiContext
import io.rebble.libpebblecommon.connection.PebbleIdentifier

interface CompanionDevice {
    suspend fun registerDevice(identifier: PebbleIdentifier, uiContext: PlatformUiContext)
    fun hasApprovedDevice(identifier: PebbleIdentifier): Boolean
    fun cdmPreviouslyCrashed(): Boolean
}
