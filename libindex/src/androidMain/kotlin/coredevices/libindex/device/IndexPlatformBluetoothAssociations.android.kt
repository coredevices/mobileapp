package coredevices.libindex.device

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.CompanionDeviceManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
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
    private val _associations = MutableStateFlow<List<IndexAssociation>?>(null)
    actual val associations: StateFlow<List<IndexAssociation>?> = _associations.asStateFlow()
    private val _bondStateChanges = MutableSharedFlow<IndexBondStateUpdate>(extraBufferCapacity = 5, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    actual val bondStateChanges: Flow<IndexBondStateUpdate> = _bondStateChanges.asSharedFlow()
    private val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED).apply {
        addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
    }

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
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            // BT is off or still coming up (e.g. right after a phone reboot). (MOB-9358)
            Logger.d("updateAssociations: BT adapter not ready (enabled=${bluetoothAdapter?.isEnabled}); skipping")
            return null
        }
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
        // Keep the last good list rather than reverting to null if it becomes unreadable.
        _associations.value = getAssociations() ?: return
    }

    private val NOTIFICATION_CHANNEL_ID = "index_warnings"

    @SuppressLint("MissingPermission")
    actual fun warnIfNoCompanionAssociations() {
        val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return
        @Suppress("DEPRECATION")
        val associations = try {
            cdm.associations
        } catch (e: SecurityException) {
            logger.w("hasAnyCompanionAssociations: SecurityException reading CDM associations", e)
            return
        }
        if (associations.isEmpty()) {
            logger.w { "Paired ring but no CompanionDeviceManager associations; warning user" }
            val channel = NotificationChannelCompat.Builder(NOTIFICATION_CHANNEL_ID, NotificationManager.IMPORTANCE_HIGH)
                .setName("Index Warnings")
                .setDescription("Notifications about Index issues")
                .build()
            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Index 01 background access limited")
                .setContentText("Your Index 01 isn't registered as a companion device, so some background " +
                        "features may not work correctly. Tap here to fix this.")
                .setStyle(NotificationCompat.BigTextStyle().bigText("Your Index 01 isn't registered as a companion device, so some background " +
                        "features may not work correctly. Tap here to fix this."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setGroup("index_warnings")
                .setAutoCancel(true)
                .setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        0,
                        context.packageManager.getLaunchIntentForPackage(context.packageName)
                            ?.setData("pebble://${REQUEST_URI_HOST}".toUri()),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .build()

            val notificationManager = NotificationManagerCompat.from(context)
            notificationManager.createNotificationChannel(channel)
            notificationManager.notify("index_warning_cdm".hashCode(), notification)
        }
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
        if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (state == BluetoothAdapter.STATE_ON) {
                // BT finished turning on (e.g. after a reboot); the bond list is now readable.
                logger.d { "BT turned on, refreshing associations" }
                updateAssociations()
            }
            return
        }
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
            // Important that these are this order to avoid duplicate ops (e.g. pair erase)
            _bondStateChanges.tryEmit(update)
            updateAssociations()
        }
    }

    actual companion object {
        actual val isEnabled: Boolean = true
        private val logger = Logger.withTag("IndexPlatformBluetoothAssociations")
    }
}