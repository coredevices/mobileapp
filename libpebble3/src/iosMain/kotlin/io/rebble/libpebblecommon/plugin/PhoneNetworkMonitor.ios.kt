package io.rebble.libpebblecommon.plugin

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.CoreTelephony.CTRadioAccessTechnologyCDMA1x
import platform.CoreTelephony.CTRadioAccessTechnologyCDMAEVDORev0
import platform.CoreTelephony.CTRadioAccessTechnologyCDMAEVDORevA
import platform.CoreTelephony.CTRadioAccessTechnologyCDMAEVDORevB
import platform.CoreTelephony.CTRadioAccessTechnologyEdge
import platform.CoreTelephony.CTRadioAccessTechnologyGPRS
import platform.CoreTelephony.CTRadioAccessTechnologyHSDPA
import platform.CoreTelephony.CTRadioAccessTechnologyHSUPA
import platform.CoreTelephony.CTRadioAccessTechnologyLTE
import platform.CoreTelephony.CTRadioAccessTechnologyNR
import platform.CoreTelephony.CTRadioAccessTechnologyNRNSA
import platform.CoreTelephony.CTRadioAccessTechnologyWCDMA
import platform.CoreTelephony.CTRadioAccessTechnologyeHRPD
import platform.CoreTelephony.CTServiceRadioAccessTechnologyDidChangeNotification
import platform.CoreTelephony.CTTelephonyNetworkInfo
import platform.Network.nw_interface_type_cellular
import platform.Network.nw_interface_type_wifi
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_uses_interface_type
import platform.darwin.dispatch_get_main_queue
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter

actual class PhoneNetworkMonitor {
    @OptIn(ExperimentalForeignApi::class)
    actual val connection: Flow<String?> = callbackFlow {
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_update_handler(monitor) { path ->
            trySend(
                when {
                    nw_path_get_status(path) != nw_path_status_satisfied -> Connections.NONE
                    nw_path_uses_interface_type(path, nw_interface_type_wifi) -> Connections.WIFI
                    nw_path_uses_interface_type(path, nw_interface_type_cellular) ->
                        Connections.CELLULAR
                    else -> Connections.NONE
                }
            )
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
        awaitClose { nw_path_monitor_cancel(monitor) }
    }.distinctUntilChanged()

    actual val cellGeneration: Flow<String?> = callbackFlow {
        val info = CTTelephonyNetworkInfo()
        trySend(info.generation())

        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(
            name = CTServiceRadioAccessTechnologyDidChangeNotification,
            `object` = null,
            queue = null,
        ) { _: NSNotification? ->
            trySend(info.generation())
        }

        awaitClose { center.removeObserver(observer) }
    }.distinctUntilChanged()

    /** Dual-SIM reports per service; whichever has a radio is the one to show. */
    private fun CTTelephonyNetworkInfo.generation(): String =
        serviceCurrentRadioAccessTechnology
            ?.values
            ?.filterIsInstance<String>()
            ?.firstOrNull()
            ?.generation()
            ?: CellGenerations.NONE
}

private fun String.generation(): String = when (this) {
    CTRadioAccessTechnologyGPRS,
    CTRadioAccessTechnologyEdge,
    CTRadioAccessTechnologyCDMA1x,
    -> CellGenerations.TWO_G

    CTRadioAccessTechnologyWCDMA,
    CTRadioAccessTechnologyHSDPA,
    CTRadioAccessTechnologyHSUPA,
    CTRadioAccessTechnologyCDMAEVDORev0,
    CTRadioAccessTechnologyCDMAEVDORevA,
    CTRadioAccessTechnologyCDMAEVDORevB,
    CTRadioAccessTechnologyeHRPD,
    -> CellGenerations.THREE_G

    CTRadioAccessTechnologyLTE -> CellGenerations.FOUR_G
    CTRadioAccessTechnologyNRNSA, CTRadioAccessTechnologyNR -> CellGenerations.FIVE_G
    // Something Apple added since: better to say nothing than to name the wrong generation.
    else -> CellGenerations.NONE
}
