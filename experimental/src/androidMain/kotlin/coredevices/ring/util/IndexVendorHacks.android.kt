package coredevices.ring.util

import android.os.Build

actual object IndexVendorHacks {
    private val XIAOMI_BRANDS = listOf("Xiaomi", "Redmi", "POCO")
    private val ONEPLUS_BRANDS = listOf("OnePlus", "OPPO", "realme")

    /**
     * Get if scan by paired address should be enabled, true unless otherwise unsupported.
     *
     * - Xiaomi & related devices observed to not support the address-specific scan filter
     */
    actual fun getScanByPairedAddressDefault(): Boolean = !isVendor(XIAOMI_BRANDS)

    /**
     * Get if scan is stopped during doze, currently OnePlus-specific workaround to nudge the driver
     * into actually starting the bt chip's scan again as their code is inconsistent and weird
     *
     * - OnePlus & related devices observed to stop scan in doze + 5s after screen-off
     */
    fun getScanStoppedInDoze(): Boolean = ONEPLUS_BRANDS.any { it.equals(Build.MANUFACTURER, ignoreCase = true) }

    private fun isVendor(names: List<String>): Boolean = names.any {
        it.equals(Build.MANUFACTURER, ignoreCase = true) || it.equals(Build.BRAND, ignoreCase = true)
    }
}
