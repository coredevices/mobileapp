package coredevices.ring.util

/** Phone vendor specific workarounds for the ring. */
expect object IndexVendorHacks {
    fun getScanByPairedAddressDefault(): Boolean
}
