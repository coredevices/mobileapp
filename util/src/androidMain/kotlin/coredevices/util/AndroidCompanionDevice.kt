package coredevices.util

import PlatformUiContext
import android.app.Activity
import android.bluetooth.le.ScanFilter
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import co.touchlab.kermit.Logger
import com.russhwolf.settings.set
import io.rebble.libpebblecommon.connection.PebbleBleIdentifier
import io.rebble.libpebblecommon.connection.PebbleIdentifier
import io.rebble.libpebblecommon.notification.LibPebbleNotificationListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

class AndroidCompanionDevice(
    private val appResumed: AppResumed,
    private val context: Context,
    private val coreConfigFlow: CoreConfigFlow,
    private val settings: com.russhwolf.settings.Settings,
) : CompanionDevice {
    private val logger = Logger.withTag("AndroidCompanionDevice")

    private fun CompanionDeviceManager.hasApproved(identifier: PebbleBleIdentifier): Boolean {
        @Suppress("DEPRECATION")
        val existingBoundDevices = associations
        val macAddress = identifier.macAddress
        return existingBoundDevices.contains(macAddress)
    }

    override suspend fun registerDevice(
        identifier: PebbleIdentifier,
        uiContext: PlatformUiContext,
    ) {
        if (identifier !is PebbleBleIdentifier) {
            return
        }
        if (coreConfigFlow.value.disableCompanionDeviceManager) {
            logger.i { "Not using companion device manager because user disabled it" }
            settings[PENDING_CDM_POSSIBLE_CRASH] = false
            return
        }
        val activity = uiContext.activity
        val service = activity.getSystemService(CompanionDeviceManager::class.java)
        val macAddress = identifier.macAddress

        if (service.hasApproved(identifier)) {
            requestNotificationAccess(activity)
            settings[PENDING_CDM_POSSIBLE_CRASH] = false
            return
        }
        settings[PENDING_CDM_POSSIBLE_CRASH] = true

        val filter = BluetoothLeDeviceFilter.Builder()
                .setScanFilter(ScanFilter.Builder().setDeviceAddress(macAddress).build())
                .build()
        val associationRequest = AssociationRequest.Builder().apply {
            addDeviceFilter(filter)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
            }
            setSingleDevice(true)
        }.build()

        val result = CompletableDeferred<Boolean>()
        val callback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            object : CompanionDeviceManager.Callback() {
                override fun onAssociationPending(intentSender: IntentSender) {
                    logger.d("onAssociationPending (API >= 33)")
                    activity.startIntentSender(intentSender, null, 0, 0, 0)
                }

                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    logger.d("onAssociationCreated")
                    result.complete(true)
                }

                override fun onFailure(error: CharSequence?) {
                    logger.d("onFailure: $error")
                    result.complete(false)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            object : CompanionDeviceManager.Callback() {
                override fun onDeviceFound(intentSender: IntentSender) {
                    logger.d("onDeviceFound (API < 33)")
                    activity.startIntentSender(intentSender, null, 0, 0, 0)
                }

                override fun onAssociationCreated(associationInfo: AssociationInfo) {
                    logger.d("onAssociationCreated")
                    result.complete(true)
                }

                override fun onFailure(error: CharSequence?) {
                    logger.d("onFailure: $error")
                    result.complete(false)
                }
            }
        }
        logger.d("requesting association")

        try {
            service.associate(associationRequest, callback, null)
            val succeeded = withTimeoutOrNull(30.seconds) {
                if (!result.await()) {
                    false
                } else {
                    requestNotificationAccess(activity)
                    true
                }
            } ?: false
            logger.d { "CompanionDeviceManager succeeded=$succeeded" }
            settings[PENDING_CDM_POSSIBLE_CRASH] = false
            return
        } catch (e: ClassCastException) {
            // CompanionDeviceManager isn't working; don't use it
            logger.w { "Not using CompanionDeviceManager because it crashed!" }
            settings[PENDING_CDM_POSSIBLE_CRASH] = false
        }
    }

    override fun hasApprovedDevice(identifier: PebbleIdentifier): Boolean {
        if (identifier !is PebbleBleIdentifier) {
            return true
        }
        val service = context.getSystemService(CompanionDeviceManager::class.java)
        return service.hasApproved(identifier)
    }

    override fun cdmPreviouslyCrashed(): Boolean {
        return settings.getBoolean(PENDING_CDM_POSSIBLE_CRASH, false)
    }

    fun hasNotificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    suspend fun requestNotificationAccess(activity: Activity): PermissionResult {
        val service = activity.getSystemService(CompanionDeviceManager::class.java)
        if (hasNotificationAccess(activity)) {
            return PermissionResult.Granted
        }

        @Suppress("DEPRECATION")
        val companionBoundDevices = service.associations
        if (companionBoundDevices.isNotEmpty()) {
            val component =
                LibPebbleNotificationListener.componentName(activity.applicationContext)
            service.requestNotificationAccess(component)
        } else {
            // Fall back to using legacy approval process (bounce user into system settings menu, and maybe
            // highlight our app).
            logger.d { "Use legacy notification access approval" }
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            intent.putExtra(":settings:fragment_args_key", activity.packageName)
            intent.putExtra(":settings:show_fragment_args", Bundle().apply {
                putString(":settings:fragment_args_key", activity.packageName)
            })
            activity.startActivity(intent)
        }

        val resumed = withTimeoutOrNull(10.seconds) {
            appResumed.appResumed.first()
        }
        return if (resumed != null) {
            hasNotificationAccess(activity).asPermissionResult()
        } else {
            PermissionResult.Error
        }
    }

    companion object {
        private const val PENDING_CDM_POSSIBLE_CRASH = "pending_cdm_possible_crash"
    }
}
