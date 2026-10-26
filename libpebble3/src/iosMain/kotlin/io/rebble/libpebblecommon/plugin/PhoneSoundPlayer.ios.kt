package io.rebble.libpebblecommon.plugin

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDuckOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.outputVolume
import platform.AVFAudio.setActive
import platform.Foundation.NSBundle
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationInterruptionLevel
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSettingEnabled
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

// System alarm sound, used unless the app bundles its own find_my_phone.caf.
private const val SYSTEM_ALARM_SOUND = "/System/Library/Audio/UISounds/alarm.caf"
private const val CRITICAL_NOTIFICATION_ID = "find_my_phone_critical"

// Below this system output volume the AVAudioPlayer is too quiet to find the phone by, so we ask
// for a critical alert instead. Heuristic threshold, tune against real devices.
private const val MIN_AUDIBLE_VOLUME = 0.45f

actual class PhoneSoundPlayer {
    private val logger = Logger.withTag("PhoneSoundPlayer")
    private val _isRinging = MutableStateFlow(false)
    actual val isRinging: Flow<Boolean> = _isRinging.asStateFlow()

    private var player: AVAudioPlayer? = null

    actual fun startRinging() {
        if (_isRinging.value) return
        activatePlaybackSession()
        // outputVolume reflects the last value seen while the session was active: a change made
        // between rings (session inactive) isn't picked up until the session has been active again.
        val volume = AVAudioSession.sharedInstance().outputVolume
        val needsCritical = volume < MIN_AUDIBLE_VOLUME
        logger.i { "startRinging: outputVolume=$volume needsCritical=$needsCritical" }
        if (!needsCritical) {
            startAudioPlayer()
            return
        }
        // Availability is only readable async, so the fallback choice is made in the callback.
        checkCriticalAlertsEnabled { enabled ->
            if (enabled) {
                deactivateSession() // the critical alert plays via the notification system
                postCriticalAlert()
            } else {
                startAudioPlayer()
            }
        }
    }

    // Playback: full volume, ignores the silent switch. It still follows the active output route,
    // though — iOS has no public API to force the built-in speaker over a connected headset or
    // Bluetooth sink, so it rings there if one is connected.
    private fun activatePlaybackSession() {
        memScoped {
            val err = alloc<ObjCObjectVar<NSError?>>()
            val session = AVAudioSession.sharedInstance()
            session.setCategory(
                AVAudioSessionCategoryPlayback,
                withOptions = AVAudioSessionCategoryOptionDuckOthers,
                error = err.ptr,
            )
            session.setActive(true, error = err.ptr)
        }
    }

    private fun deactivateSession() {
        memScoped {
            val err = alloc<ObjCObjectVar<NSError?>>()
            AVAudioSession.sharedInstance().setActive(false, error = err.ptr)
        }
    }

    /**
     * A critical alert plays above the set volume (and through silent/DND), but only when the app
     * holds the critical-alerts entitlement and the user has granted it. Without the entitlement
     * this is always false, so the critical path never runs.
     */
    private fun checkCriticalAlertsEnabled(result: (Boolean) -> Unit) {
        UNUserNotificationCenter.currentNotificationCenter()
            .getNotificationSettingsWithCompletionHandler { settings ->
                result(settings?.criticalAlertSetting == UNNotificationSettingEnabled)
            }
    }

    /** Posts a critical alert. The caller must have confirmed [checkCriticalAlertsEnabled]. */
    private fun postCriticalAlert() {
        val content = UNMutableNotificationContent().apply {
            setTitle("Finding your phone")
            setBody("Ringing from your watch")
            setInterruptionLevel(UNNotificationInterruptionLevel.UNNotificationInterruptionLevelCritical)
            setSound(UNNotificationSound.defaultCriticalSoundWithAudioVolume(1.0f))
        }
        val request = UNNotificationRequest.requestWithIdentifier(CRITICAL_NOTIFICATION_ID, content, null)
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request) { error ->
            if (error != null) logger.w { "Failed to post critical alert: $error" }
        }
        _isRinging.value = true
    }

    private fun startAudioPlayer() {
        try {
            val url = NSBundle.mainBundle.URLForResource("find_my_phone", withExtension = "caf")
                ?: NSURL.fileURLWithPath(SYSTEM_ALARM_SOUND)
            player = memScoped {
                val err = alloc<ObjCObjectVar<NSError?>>()
                AVAudioPlayer(contentsOfURL = url, error = err.ptr)
            }.apply {
                numberOfLoops = -1
                volume = 1.0f
                prepareToPlay()
                play()
            }
            _isRinging.value = true
        } catch (e: Exception) {
            logger.w(e) { "Failed to start ringing" }
            cleanUp()
        }
    }

    actual fun stopRinging() {
        if (!_isRinging.value) return
        cleanUp()
    }

    private fun cleanUp() {
        player?.stop()
        player = null
        memScoped {
            val err = alloc<ObjCObjectVar<NSError?>>()
            AVAudioSession.sharedInstance().setActive(false, error = err.ptr)
        }
        UNUserNotificationCenter.currentNotificationCenter()
            .removeDeliveredNotificationsWithIdentifiers(listOf(CRITICAL_NOTIFICATION_ID))
        _isRinging.value = false
    }
}
