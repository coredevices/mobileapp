package io.rebble.libpebblecommon.plugin

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

actual class PhoneNetworkMonitor(
    private val appContext: AppContext,
) {
    actual val connection: Flow<String?> = callbackFlow {
        val connectivity = appContext.context.getSystemService(ConnectivityManager::class.java)
        if (connectivity == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = send()
            override fun onLost(network: Network) = send()
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) = send()

            fun send() {
                trySend(connectivity.route())
            }
        }
        connectivity.registerDefaultNetworkCallback(callback)
        trySend(connectivity.route())
        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    // Wi-Fi first: a VPN's capabilities carry the transport underneath it, and that is the one
    // the user would name.
    private fun ConnectivityManager.route(): String {
        val capabilities = getNetworkCapabilities(activeNetwork) ?: return Connections.NONE
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Connections.WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Connections.CELLULAR
            else -> Connections.NONE
        }
    }

    actual val cellGeneration: Flow<String?> = callbackFlow {
        val context = appContext.context
        val telephony = context.getSystemService(TelephonyManager::class.java)
        if (telephony == null) {
            trySend(null)
            awaitClose { }
            return@callbackFlow
        }
        trySend(telephony.currentGeneration())

        var unregister: (() -> Unit)? = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
                    override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                        trySend(info.generation())
                    }
                }
                telephony.registerTelephonyCallback(context.mainExecutor, callback)
                unregister = { telephony.unregisterTelephonyCallback(callback) }
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
                        trySend(networkType.generation())
                    }
                }
                @Suppress("DEPRECATION")
                telephony.listen(listener, PhoneStateListener.LISTEN_DATA_CONNECTION_STATE)
                unregister = {
                    @Suppress("DEPRECATION")
                    telephony.listen(listener, PhoneStateListener.LISTEN_NONE)
                }
            }
        } catch (e: SecurityException) {
            // READ_PHONE_STATE was refused. The initial read already reported that as null, and
            // the permission UI is the app's job, not a background flow's.
        }
        awaitClose { unregister?.invoke() }
    }.distinctUntilChanged()

    private fun TelephonyManager.currentGeneration(): String? = try {
        dataNetworkType.generation()
    } catch (e: SecurityException) {
        null
    }
}

private fun TelephonyDisplayInfo.generation(): String = when (overrideNetworkType) {
    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NONE,
    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA,
    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO,
    -> networkType.generation()
    // Every other override is a flavour of NR sitting on top of an LTE network type.
    else -> CellGenerations.FIVE_G
}

internal fun Int.generation(): String = when (this) {
    TelephonyManager.NETWORK_TYPE_GPRS,
    TelephonyManager.NETWORK_TYPE_EDGE,
    TelephonyManager.NETWORK_TYPE_CDMA,
    TelephonyManager.NETWORK_TYPE_1xRTT,
    TelephonyManager.NETWORK_TYPE_IDEN,
    TelephonyManager.NETWORK_TYPE_GSM,
    -> CellGenerations.TWO_G

    TelephonyManager.NETWORK_TYPE_UMTS,
    TelephonyManager.NETWORK_TYPE_EVDO_0,
    TelephonyManager.NETWORK_TYPE_EVDO_A,
    TelephonyManager.NETWORK_TYPE_EVDO_B,
    TelephonyManager.NETWORK_TYPE_HSDPA,
    TelephonyManager.NETWORK_TYPE_HSUPA,
    TelephonyManager.NETWORK_TYPE_HSPA,
    TelephonyManager.NETWORK_TYPE_HSPAP,
    TelephonyManager.NETWORK_TYPE_EHRPD,
    TelephonyManager.NETWORK_TYPE_TD_SCDMA,
    -> CellGenerations.THREE_G

    TelephonyManager.NETWORK_TYPE_LTE,
    TelephonyManager.NETWORK_TYPE_IWLAN,
    -> CellGenerations.FOUR_G

    TelephonyManager.NETWORK_TYPE_NR -> CellGenerations.FIVE_G

    else -> CellGenerations.NONE
}
