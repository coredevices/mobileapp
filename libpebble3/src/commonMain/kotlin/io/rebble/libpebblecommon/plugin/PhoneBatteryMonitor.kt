package io.rebble.libpebblecommon.plugin

import kotlinx.coroutines.flow.Flow

/**
 * Platform-agnostic phone battery monitor. Used by [PhoneStatePlugin] to surface
 * the *phone*'s battery level (not the watch's) via the source API.
 *
 * Each platform actual exposes a cold flow that emits the current percentage (0..100)
 * immediately on collection and again whenever the OS reports a change. Emits `null`
 * when the value is unknown (e.g. simulator, JVM target).
 */
expect class PhoneBatteryMonitor {
    val batteryLevel: Flow<Int?>
}
