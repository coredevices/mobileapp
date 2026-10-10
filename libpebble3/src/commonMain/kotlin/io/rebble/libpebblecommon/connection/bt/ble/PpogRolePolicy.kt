package io.rebble.libpebblecommon.connection.bt.ble

/** Platform authorization constraint, independent of user pairing consent or stored preferences. */
expect fun platformAllowsPhoneGattServer(): Boolean

class PpogRolePolicy(val allowPhoneGattServer: Boolean) {
    val requiresWatchHostedTransport: Boolean get() = !allowPhoneGattServer
    fun useReversedV2(requested: Boolean): Boolean = requiresWatchHostedTransport || requested
    fun useReversedV1(requested: Boolean): Boolean = requiresWatchHostedTransport || requested
}
