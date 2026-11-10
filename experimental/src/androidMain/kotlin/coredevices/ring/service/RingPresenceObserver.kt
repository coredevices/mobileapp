package coredevices.ring.service

import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import coredevices.ring.database.Preferences
import coredevices.util.CoreConfigHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Asks the system to watch for the paired ring via its CompanionDeviceManager association. The
 * system's own BLE scan is not subject to the OEM screen-off restrictions placed on app scans
 * (Xiaomi "Deep Doze" refuses non-system scans), so when the ring wakes the system binds
 * [RingCompanionDeviceService]. The presence is used to confirm the app's own scan still sees the
 * ring, restarting it via [RingScanWatchdog] when it has locked up.
 */
class RingPresenceObserver(
    private val context: Context,
    private val preferences: Preferences,
    private val coreConfigHolder: CoreConfigHolder,
    private val scanWatchdog: RingScanWatchdog,
) {
    private val logger = Logger.withTag("RingPresence")
    private val scope = CoroutineScope(Dispatchers.Main)
    private val wakeLock: PowerManager.WakeLock? by lazy {
        context.getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "coreapp:ringScanWatchdog")
            ?.apply { setReferenceCounted(false) }
    }
    private var observed: Observation? = null
    private val lastLogged = mutableMapOf<String, TimeMark>()

    // ObservingDevicePresenceRequest only exists on Android 16+; a field of that type fails to resolve on older versions.
    private class Observation(val address: String, val request: Any?)

    fun start() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (coreConfigHolder.config.value.disableCompanionDeviceManager) return
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) {
            logger.i { "Companion device setup unavailable; not observing ring presence" }
            return
        }
        scanWatchdog.start(scope)
        preferences.ringPaired
            .map { it?.toBleAddress() }
            .distinctUntilChanged()
            .onEach {
                if (!coreConfigHolder.config.value.disableCompanionDeviceManager) {
                    observe(it)
                }
            }
            .launchIn(scope)
    }

    fun onRingAppeared(address: String) {
        logThrottled("Ring $address appeared (companion presence)")
        // The system only keeps the CPU awake for this callback; the watchdog's wait and any
        // restart need it held a little longer.
        val check = scanWatchdog.onRingPresent() ?: return
        try {
            wakeLock?.acquire(WAKE_LOCK_TIMEOUT.inWholeMilliseconds)
        } catch (e: Exception) {
            logger.w(e) { "Unable to acquire scan watchdog wake lock" }
        }
        check.invokeOnCompletion { releaseWakeLock() }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (e: Exception) {
            logger.w(e) { "Unable to release scan watchdog wake lock" }
        }
    }

    fun onRingDisappeared(address: String) {
        logThrottled("Ring $address disappeared (companion presence)")
    }

    private fun logThrottled(message: String) {
        val last = lastLogged[message]
        if (last != null && last.elapsedNow() < LOG_THROTTLE) return
        lastLogged[message] = TimeSource.Monotonic.markNow()
        logger.i { message }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun observe(address: String?) {
        val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return
        observed?.let { old ->
            runCatching { cdm.stopObserving(old) }
                .onFailure { logger.w(it) { "Failed to stop observing ${old.address}" } }
            observed = null
        }
        if (address == null) return
        try {
            observed = cdm.startObserving(address)
            logger.i { "Observing companion presence of ring $address" }
        } catch (e: Exception) {
            logger.w(e) { "Failed to observe presence of ring $address" }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @Suppress("DEPRECATION")
    private fun CompanionDeviceManager.startObserving(address: String): Observation {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            val association = myAssociations.firstOrNull {
                it.deviceMacAddress?.toString().equals(address, ignoreCase = true)
            } ?: error("$address not associated")
            val request = ObservingDevicePresenceRequest.Builder().setAssociationId(association.id).build()
            startObservingDevicePresence(request)
            return Observation(address, request)
        }
        startObservingDevicePresence(address)
        return Observation(address, null)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @Suppress("DEPRECATION")
    private fun CompanionDeviceManager.stopObserving(observation: Observation) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
            observation.request is ObservingDevicePresenceRequest
        ) {
            stopObservingDevicePresence(observation.request)
        } else {
            stopObservingDevicePresence(observation.address)
        }
    }

    private companion object {
        val LOG_THROTTLE = 30.seconds
        val WAKE_LOCK_TIMEOUT = 10.seconds
    }
}

/** Paired ring ids are stored as the BLE address with or without colons. */
private fun String.toBleAddress(): String =
    replace(":", "").uppercase().chunked(2).joinToString(":")
