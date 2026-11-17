package coredevices.ring.database

import android.os.Build

internal actual val pendingIntentScanSupported: Boolean = true

private val XIAOMI_VENDORED_BRANDS = listOf("Xiaomi", "Redmi", "POCO")

internal actual val scanByPairedAddressDefault: Boolean
    get() = XIAOMI_VENDORED_BRANDS.none {
        it.equals(Build.MANUFACTURER, ignoreCase = true) || it.equals(Build.BRAND, ignoreCase = true)
    }
