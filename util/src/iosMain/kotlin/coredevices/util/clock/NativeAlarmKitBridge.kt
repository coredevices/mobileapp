package coredevices.util.clock

import platform.Foundation.NSDate
import platform.UIKit.UIDevice
import kotlin.concurrent.Volatile

/** AlarmKit bridge to the Swift calls */
object NativeAlarmKitBridge {
    /** One of [AuthorizationState]. */
    @Volatile
    var authorizationState: (() -> String)? = null

    /** Completes with whether the user granted access. */
    @Volatile
    var requestAuthorization: ((completion: (Boolean) -> Unit) -> Unit)? = null

    /** Completes with an error description, or null once the alarm is scheduled. */
    @Volatile
    var schedule: ((id: String, title: String, fireAt: NSDate, deepLink: String, completion: (String?) -> Unit) -> Unit)? = null

    @Volatile
    var cancel: ((id: String) -> Unit)? = null

    val isAvailable: Boolean by lazy {
        (UIDevice.currentDevice.systemVersion.split(".").firstOrNull()?.toIntOrNull() ?: 0) >= 26
    }

    /** Strings the Swift side reports from `AlarmManager.authorizationState`. */
    object AuthorizationState {
        const val AUTHORIZED = "authorized"
        const val DENIED = "denied"
        const val NOT_DETERMINED = "notDetermined"
    }
}
