package coredevices.libindex.device

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

actual class IndexPlatformBluetoothAssociations(
    private val context: Context
): BroadcastReceiver() {
    private val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val _associations = MutableStateFlow<List<IndexAssociation>>(emptyList())
    actual val associations: StateFlow<List<IndexAssociation>> = _associations.asStateFlow()
    private val _bondStateChanges = MutableSharedFlow<IndexBondStateUpdate>(extraBufferCapacity = 5, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    actual val bondStateChanges: Flow<IndexBondStateUpdate> = _bondStateChanges.asSharedFlow()
    private val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
    private val _associationsReady = CompletableDeferred<Unit>()
    actual val associationsReady: Deferred<Unit> = _associationsReady

    private fun hasBluetoothConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun getAssociations(): List<IndexAssociation>? {
        if (!hasBluetoothConnectPermission()) {
            Logger.d("updateAssociations: BLUETOOTH_CONNECT not granted; skipping")
            return null
        }
        val bluetoothAdapter = manager.adapter
        val bondedDevices = try {
            bluetoothAdapter.bondedDevices ?: emptySet()
        } catch (e: SecurityException) {
            Logger.w("updateAssociations: SecurityException reading bonded devices", e)
            return null
        }
        return bondedDevices.map { device ->
            IndexAssociation(
                deviceName = device.name ?: "Unknown Device",
                identifier = IndexIdentifier.fromPlatformAddress(device.address)
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun updateAssociations() {
        _associations.value = getAssociations() ?: return
        _associationsReady.complete(Unit)
    }

    actual fun init(bluetoothPermissionChanged: Flow<Boolean>) {
        context.registerReceiver(this, filter)
        updateAssociations()
        bluetoothPermissionChanged.onEach {
            if (it) {
                updateAssociations()
            }
        }.launchIn(GlobalScope)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
            val reason = intent.getIntExtra("android.bluetooth.device.extra.REASON", -1).takeIf { it != -1 }
            logger.d { "Received bond state change: ${device?.address ?: "<No address>"} -> $bondState reason = $reason" }
            val address = device?.address ?: return
            val identifier = IndexIdentifier.fromPlatformAddress(address)
            val name = try {
                @SuppressLint("MissingPermission")
                device.name
            } catch (e: SecurityException) {
                null
            }
            val update = when (bondState) {
                BluetoothDevice.BOND_BONDED -> IndexBondStateUpdate(
                    name,
                    IndexBondState.Bonded,
                    identifier
                )
                BluetoothDevice.BOND_BONDING -> IndexBondStateUpdate(
                    name,
                    IndexBondState.Bonding,
                    identifier
                )

                BluetoothDevice.BOND_NONE -> IndexBondStateUpdate(
                    name,
                    IndexBondState.NotBonded,
                    identifier
                )
                else -> return
            }
            _bondStateChanges.tryEmit(update)
            updateAssociations()
        }
    }

    actual companion object {
        actual val isEnabled: Boolean = true
        private val logger = Logger.withTag("IndexPlatformBluetoothAssociations")
    }
}