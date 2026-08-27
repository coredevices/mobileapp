package io.rebble.libpebblecommon.datalogging

import io.rebble.libpebblecommon.packets.DataItemType
import kotlin.uuid.Uuid

/**
 * Forwards third-party watchapp datalogging to the app's phone companion.
 * Background workers cannot use AppMessage, so datalogging is their only
 * channel to the phone while the watchapp is closed.
 *
 * Delivery is best-effort: items are forwarded as they arrive and are not
 * buffered or retried phone-side (the watch protocol has already ACKed
 * them). Session ids are connection-local, so all callbacks carry the
 * watch identity.
 */
interface CompanionDatalogging {
    fun onSessionOpened(
        watchIdentity: String,
        sessionId: UByte,
        appUuid: Uuid,
        timestamp: UInt,
        tag: UInt,
        itemType: DataItemType,
        itemSize: UShort,
    )

    fun onDataItems(watchIdentity: String, sessionId: UByte, data: ByteArray)

    fun onSessionClosed(watchIdentity: String, sessionId: UByte)
}

object NoOpCompanionDatalogging : CompanionDatalogging {
    override fun onSessionOpened(
        watchIdentity: String,
        sessionId: UByte,
        appUuid: Uuid,
        timestamp: UInt,
        tag: UInt,
        itemType: DataItemType,
        itemSize: UShort,
    ) = Unit

    override fun onDataItems(watchIdentity: String, sessionId: UByte, data: ByteArray) = Unit

    override fun onSessionClosed(watchIdentity: String, sessionId: UByte) = Unit
}
