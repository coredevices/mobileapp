package coredevices.libindex.device

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.IntentFilter
import android.os.Build
import androidx.annotation.RequiresPermission
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.util.asFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
actual suspend fun createBond(
    context: AppContext,
    identifier: IndexIdentifier
): Boolean {
    Logger.d("createBond()")
    @Suppress("DEPRECATION")
    val adapter = BluetoothAdapter.getDefaultAdapter()
    val macAddress = identifier.asPlatformAddress
    val device = adapter.getRemoteDevice(macAddress)
    return try {
        device.createBond()
    } catch (e: SecurityException) {
        Logger.e("failed to create bond", e)
        false
    }
}

actual fun getBluetoothDevicePairEvents(
    context: AppContext,
    identifier: IndexIdentifier
): Flow<BluetoothDevicePairEvent> {
    return IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED).asFlow(context.context, exported = true)
        .mapNotNull {
            val device: BluetoothDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            } ?: return@mapNotNull null
            BluetoothDevicePairEvent(
                device = IndexIdentifier.fromPlatformAddress(device.address),
                bondState = it.getIntExtra(
                    BluetoothDevice.EXTRA_BOND_STATE,
                    BluetoothDevice.BOND_NONE
                ),
                unbondReason = it.getIntExtra("android.bluetooth.device.extra.REASON", -1)
                    .takeIf { it != -1 }
            )
        }
        .filter {
            identifier == it.device
        }
}