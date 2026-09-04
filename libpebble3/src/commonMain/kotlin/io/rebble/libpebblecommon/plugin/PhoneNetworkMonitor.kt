package io.rebble.libpebblecommon.plugin

import kotlinx.coroutines.flow.Flow

/**
 * How the phone is connected. Both flows emit null when the platform won't say — no telephony
 * hardware, or a refused permission — which is not the same as [CellGenerations.NONE] or false,
 * both of which mean "connected to nothing".
 */
expect class PhoneNetworkMonitor {
    /**
     * Which cellular generation the phone's radio is on, one of [CellGenerations]. Says nothing
     * about whether data is going over it — see [connection] for that.
     */
    val cellGeneration: Flow<String?>

    /** What the phone's current route out actually uses, one of [Connections]. */
    val connection: Flow<String?>
}

/** How the phone is reaching the internet, if it is. */
internal object Connections {
    const val NONE = "None"
    const val WIFI = "Wi-Fi"
    const val CELLULAR = "Cellular"
}

/** The vocabulary both platforms report in, since a watchface may well match on it. */
internal object CellGenerations {
    const val NONE = "None"
    const val TWO_G = "2G"
    const val THREE_G = "3G"
    const val FOUR_G = "4G"
    const val FIVE_G = "5G"
}
