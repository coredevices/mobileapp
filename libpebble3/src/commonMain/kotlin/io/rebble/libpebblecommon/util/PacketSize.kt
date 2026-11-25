package io.rebble.libpebblecommon.util

import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.packets.WatchVersion
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint

internal fun getMaxPebblePacketPayloadSize(
    endpoint: ProtocolEndpoint,
    watchVersion: WatchVersion.WatchVersionResponse?
): Int {
    if (endpoint != ProtocolEndpoint.APP_MESSAGE) {
        return STANDARD_MAX_PEBBLE_PACKET_SIZE
    }

    val capabilities = watchVersion?.capabilities?.let { ProtocolCapsFlag.fromFlags(it.get()) }

    return if (capabilities?.contains(ProtocolCapsFlag.Supports8kAppMessage) == true) {
        8222
    } else {
        STANDARD_MAX_PEBBLE_PACKET_SIZE
    }
}

internal fun getPutBytesMaximumDataSize(watchVersion: WatchVersion.WatchVersionResponse?): Int {
    // 4 bytes get used for the cookie
    return getMaxPebblePacketPayloadSize(ProtocolEndpoint.PUT_BYTES, watchVersion) - 4
}

internal val STANDARD_MAX_PEBBLE_PACKET_SIZE: Int = 2048
