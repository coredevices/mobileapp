package coredevices.util

import PlatformUiContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBManager
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.EventKit.EKAuthorizationStatusAuthorized
import platform.EventKit.EKEntityType
import platform.EventKit.EKEventStore
import platform.Foundation.NSError
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import platform.UIKit.UIDevice
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNNotificationSettings
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.NSObject
import kotlin.coroutines.resume

class IosPermissionRequester(
    requiredPermissions: RequiredPermissions,
    appResumed: AppResumed,
) : PermissionRequester(requiredPermissions, appResumed) {
    override suspend fun requestPlatformPermission(
        permission: Permission,
        uiContext: PlatformUiContext
    ): PermissionResult {
        return when (permission) {
            Permission.Location -> requestLocationPermission()
            Permission.BackgroundLocation -> requestBackgroundLocationPermission()
            Permission.PostNotifications -> requestNotificationPermission()
            Permission.Bluetooth -> requestBluetoothPermission()
            Permission.ReadCallLog -> throw IllegalStateException("ReadCallLog permission not needed on iOS")
            Permission.Calendar -> requestCalendarPermission()
            Permission.Contacts -> throw IllegalStateException("Contacts permission not needed on iOS")
            Permission.ReadPhoneState -> throw IllegalStateException("ReadPhoneState permission not needed on iOS")
            Permission.ReadNotifications -> throw IllegalStateException("ReadNotifications permission not needed on iOS")
            Permission.RecordAudio -> requestAudioRecordPermission()
            Permission.SpeechRecognizer -> requestSpeechRecognitionPermission()
            Permission.ExternalStorage -> TODO()
            Permission.SetAlarms -> TODO()
            Permission.BatteryOptimization -> TODO()
            Permission.Beeper -> throw IllegalStateException("Beeper permission not needed on iOS")
        }
    }

    override suspend fun hasPermission(permission: Permission): Boolean = when (permission) {
        Permission.Location -> hasLocationPermission()
        Permission.BackgroundLocation -> hasBackgroundLocationPermission()
        Permission.PostNotifications -> hasNotificationPermission()
        Permission.Bluetooth -> hasBluetoothPermission()
        Permission.ReadCallLog -> throw IllegalStateException("ReadCallLog permission not needed on iOS")
        Permission.Calendar -> hasCalendarPermission()
        Permission.Contacts -> throw IllegalStateException("Contacts permission not needed on iOS")
        Permission.ReadPhoneState -> throw IllegalStateException("ReadPhoneState permission not needed on iOS")
        Permission.ReadNotifications -> throw IllegalStateException("ReadNotifications permission not needed on iOS")
        Permission.RecordAudio -> hasAudioRecordPermission()
        Permission.SpeechRecognizer -> hasSpeechRecognitionPermission()
        Permission.ExternalStorage -> TODO()
        Permission.SetAlarms -> TODO()
        Permission.BatteryOptimization -> TODO()
        Permission.Beeper -> throw IllegalStateException("Beeper permission not needed on iOS")
    }

    override fun openPermissionsScreen(uiContext: PlatformUiContext) {
        // no-op
    }

    private fun hasCalendarPermission(): Boolean = EKEventStore.authorizationStatusForEntityType(
        EKEntityType.EKEntityTypeEvent
    ) == EKAuthorizationStatusAuthorized

    private suspend fun requestCalendarPermission(): PermissionResult {
        if (hasCalendarPermission()) return PermissionResult.Granted
        return suspendCancellableCoroutine { continuation ->
            val es = EKEventStore()
            val majorVersion =
                UIDevice.currentDevice.systemVersion.split(".").firstOrNull()?.toIntOrNull() ?: 0

            val completionHandler = { granted: Boolean, error: NSError? ->
                val result = if (granted) {
                    PermissionResult.Granted
                } else {
                    logger.e { "Error getting iOS calendar access: $error" }
                    PermissionResult.Rejected
                }
                continuation.resumeIfActive(result, "requestCalendarPermission")
            }

            if (majorVersion >= 17) {
                es.requestFullAccessToEventsWithCompletion(completionHandler)
            } else {
                es.requestAccessToEntityType(EKEntityType.EKEntityTypeEvent, completionHandler)
            }
        }
    }

    private suspend fun hasNotificationPermission(): Boolean =
        suspendCancellableCoroutine { continuation ->
            val nc = UNUserNotificationCenter.currentNotificationCenter()
            nc.getNotificationSettingsWithCompletionHandler { settings: UNNotificationSettings? ->
                continuation.resumeIfActive(
                    settings?.authorizationStatus == UNAuthorizationStatusAuthorized,
                    "hasNotificationPermission"
                )
            }
        }

    private suspend fun requestNotificationPermission(): PermissionResult =
        suspendCancellableCoroutine { continuation ->
            val completionHandler = { granted: Boolean, error: NSError? ->
                logger.d { "requestNotificationPermission granted = $granted error = $error" }
                if (granted) {
                    continuation.resumeIfActive(
                        PermissionResult.Granted,
                        "requestNotificationPermission"
                    )
                } else {
                    continuation.resumeIfActive(
                        PermissionResult.Rejected,
                        "requestNotificationPermission"
                    )
                }
            }
            UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert, completionHandler
            )
        }

    private fun hasAudioRecordPermission(): Boolean =
        AVAudioSession.sharedInstance().recordPermission() == AVAudioSessionRecordPermissionGranted

    private suspend fun requestAudioRecordPermission(): PermissionResult =
        suspendCancellableCoroutine { continuation ->
            AVAudioSession.sharedInstance().requestRecordPermission {
                continuation.resumeIfActive(it.asPermissionResult(), "requestAudioRecordPermission")
            }
        }

    private fun hasSpeechRecognitionPermission(): Boolean =
        SFSpeechRecognizer.authorizationStatus() == SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized

    private suspend fun requestSpeechRecognitionPermission(): PermissionResult =
        suspendCancellableCoroutine { continuation ->
            SFSpeechRecognizer.requestAuthorization {
                continuation.resumeIfActive(
                    (it == SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized).asPermissionResult(),
                    "requestSpeechRecognitionPermission",
                )
            }
        }

    private fun hasBluetoothPermission(): Boolean {
        val state = CBManager.authorization
        return state == CBManagerAuthorizationAllowedAlways
    }

    private suspend fun requestBluetoothPermission(): PermissionResult {
        val state = suspendCancellableCoroutine { continuation ->
            CBCentralManager(
                object : NSObject(), CBCentralManagerDelegateProtocol {
                    override fun centralManagerDidUpdateState(central: CBCentralManager) {
                        continuation.resumeIfActive(central.state, "requestBluetoothPermission")
                    }
                },
                null
            )
        }
        return (state == CBManagerStatePoweredOn).asPermissionResult()
    }

    private val locationManagerForCheckingStatus by lazy {
        CLLocationManager()
    }

    private fun hasLocationPermission(): Boolean {
        val status = locationManagerForCheckingStatus.authorizationStatus
        return status == kCLAuthorizationStatusAuthorizedAlways || status == kCLAuthorizationStatusAuthorizedWhenInUse
    }

    private suspend fun requestLocationPermission(): PermissionResult {
        val result = suspendCancellableCoroutine { continuation ->
            val manager = CLLocationManager()
            var firstCallback = true
            manager.delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                    val status = manager.authorizationStatus
                    logger.v { "locationManagerDidChangeAuthorization (location): $status firstCallback=$firstCallback" }
                    if (firstCallback && status == kCLAuthorizationStatusNotDetermined) {
                        firstCallback = false
                        // it gives us this for some reason - don't get stuck
                        return
                    }
                    continuation.resumeIfActive(status, "requestLocationPermission")
                }
            }
            manager.requestWhenInUseAuthorization()
        }
        return (result == kCLAuthorizationStatusAuthorizedAlways || result == kCLAuthorizationStatusAuthorizedWhenInUse).asPermissionResult()
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        return locationManagerForCheckingStatus.authorizationStatus == kCLAuthorizationStatusAuthorizedAlways
    }

    private suspend fun requestBackgroundLocationPermission(): PermissionResult {
        val manager = CLLocationManager()
        val initialStatus = manager.authorizationStatus
        if (initialStatus != kCLAuthorizationStatusAuthorizedWhenInUse) {
            return PermissionResult.Rejected
        }
        if (initialStatus == kCLAuthorizationStatusAuthorizedAlways) {
            return PermissionResult.Granted
        }
        manager.requestAlwaysAuthorization()
        // If the user rejects this, there is no callback to the delegate (because the state didn't
        // change), so we would hang here infinitely if waitin for that. Instead, check again after
        // the app resumes.
        appResumed.appResumed.first()
        val finalStatus = manager.authorizationStatus
        logger.v { "requestBackgroundLocationPermission: finalStatus=$finalStatus" }
        return (finalStatus == kCLAuthorizationStatusAuthorizedAlways).asPermissionResult()
    }

    private fun <T> CancellableContinuation<T>.resumeIfActive(value: T, description: String) {
        if (isActive) {
            resume(value)
        } else {
            logger.w { "Continuation already resumed: $description" }
        }
    }
}
