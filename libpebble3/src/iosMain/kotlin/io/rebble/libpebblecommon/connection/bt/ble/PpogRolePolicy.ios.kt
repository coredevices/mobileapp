package io.rebble.libpebblecommon.connection.bt.ble

import platform.Foundation.NSBundle

actual fun platformAllowsPhoneGattServer(): Boolean {
    // The existing app also declares AccessorySetupKit match data for Ring.
    // Only the explicit Bluetooth capability opts Pebble into the ASK radio role.
    val supports = NSBundle.mainBundle.infoDictionary?.get("NSAccessorySetupKitSupports") as? List<*>
    return supports?.contains("Bluetooth") != true
}
