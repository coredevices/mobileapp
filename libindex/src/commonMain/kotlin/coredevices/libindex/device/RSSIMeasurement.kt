package coredevices.libindex.device

/**
 * Both sides of the link between the phone and an Index device, in dBm.
 */
data class RSSIMeasurement(
    /** The device's mean measurement of the phone's signal. */
    val deviceRSSI: Float,
    /** The phone's most recent measurement of the device's signal, if it has one. */
    val phoneRSSI: Float?,
)
