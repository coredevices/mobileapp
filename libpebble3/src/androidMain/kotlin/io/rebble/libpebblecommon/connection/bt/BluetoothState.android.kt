package io.rebble.libpebblecommon.connection.bt

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.AppContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private val logger = Logger.withTag("nativeBluetoothStateFlow")

actual fun nativeBluetoothStateFlow(appContext: AppContext): Flow<BluetoothState>? = callbackFlow {
    val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    fun currentState(): BluetoothState =
        if (adapter?.isEnabled == true) BluetoothState.Enabled else BluetoothState.Disabled

    logger.v { "btState native at init: ${adapter?.state}" }
    trySend(currentState())

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            logger.v { "btState native changed: ${adapter?.state}" }
            trySend(currentState())
        }
    }
    appContext.context.registerReceiver(receiver, IntentFilter(ACTION_STATE_CHANGED))

    awaitClose {
        appContext.context.unregisterReceiver(receiver)
    }
}.distinctUntilChanged()
