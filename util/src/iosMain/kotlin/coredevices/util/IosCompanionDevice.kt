package coredevices.util

import PlatformUiContext
import coredevices.libindex.device.IndexIdentifier
import io.rebble.libpebblecommon.connection.PebbleIdentifier

class IosCompanionDevice : CompanionDevice {
    override suspend fun registerDevice(
        identifier: IndexIdentifier,
        uiContext: PlatformUiContext,
        useClassicAssociation: Boolean
    ) {
    }

    override suspend fun registerDevice(
        identifier: PebbleIdentifier,
        uiContext: PlatformUiContext,
    ) {
    }

    override fun hasApprovedDevice(identifier: PebbleIdentifier): Boolean {
        return true
    }

    override fun hasApprovedDevice(identifier: IndexIdentifier): Boolean {
        return true
    }

    override fun cdmPreviouslyCrashed(): Boolean {
        return false
    }
}