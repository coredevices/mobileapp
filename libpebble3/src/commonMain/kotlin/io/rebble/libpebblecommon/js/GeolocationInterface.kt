package io.rebble.libpebblecommon.js

import co.touchlab.kermit.Logger
import io.ktor.http.quote
import io.rebble.libpebblecommon.permissions.AppPermissions
import io.rebble.libpebblecommon.permissions.PermissionDecision
import io.rebble.libpebblecommon.plugin.PluginPermissions
import io.rebble.libpebblecommon.di.LibPebbleKoinComponent
import io.rebble.libpebblecommon.util.GeolocationPositionResult
import io.rebble.libpebblecommon.util.SystemGeolocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

abstract class GeolocationInterface(
    private val scope: CoroutineScope,
    private val eval: suspend (String) -> Unit,
    private val appUuid: Uuid,
    private val prompt: Boolean = true,
): LibPebbleKoinComponent {
    private val logger = Logger.withTag("GeolocationInterface")
    private val permissions: AppPermissions by inject()
    private val systemGeolocation: SystemGeolocation by inject()
    private var requestIDs = (1..Int.MAX_VALUE).iterator()
    private var watchIDs = (1..Int.MAX_VALUE).iterator()
    private val watchJobs = mutableMapOf<Int, Job>()

    private fun getNextRequestID(): Int {
        return if (requestIDs.hasNext()) {
            requestIDs.next()
        } else {
            requestIDs = (0..Int.MAX_VALUE).iterator()
            requestIDs.next()
        }
    }

    private fun getNextWatchID(): Int {
        return if (watchIDs.hasNext()) {
            watchIDs.next()
        } else {
            watchIDs = (0..Int.MAX_VALUE).iterator()
            watchIDs.next()
        }
    }

    private suspend fun triggerPositionResultGet(id: Int, result: GeolocationPositionResult) {
        when (result) {
            is GeolocationPositionResult.Success -> {
                Logger.i { "Geolocation get position success" }
                eval("_PebbleGeoCB._resultGetSuccess($id, ${result.latitude}, ${result.longitude}, ${result.accuracy}, ${result.altitude}, ${result.heading}, ${result.speed})")
            }
            is GeolocationPositionResult.Error -> {
                Logger.w { "Geolocation get position error: ${result.message}" }
                eval("_PebbleGeoCB._resultGetError($id, ${result.message.quote()})")
            }
        }
    }

    private suspend fun triggerPositionResultWatch(id: Int, result: GeolocationPositionResult) {
        when (result) {
            is GeolocationPositionResult.Success -> {
                eval("_PebbleGeoCB._resultWatchSuccess($id, ${result.latitude}, ${result.longitude}, ${result.accuracy}, ${result.altitude}, ${result.heading}, ${result.speed})")
            }
            is GeolocationPositionResult.Error -> {
                eval("_PebbleGeoCB._resultWatchError($id, ${result.message.quote()})")
            }
        }
    }

    private suspend fun geolocationPermissionGranted(): Boolean {
        val decision = if (prompt) {
            permissions.request(appUuid, PluginPermissions.LOCATION, wait = true)
        } else {
            permissions.check(appUuid, PluginPermissions.LOCATION)
        }
        return decision == PermissionDecision.Granted
    }

    open fun getRequestCallbackID() = getNextRequestID()
    open fun getWatchCallbackID() = getNextWatchID()

    open fun getCurrentPosition(
        id: Double,
        maximumAgeMs: Double,
        timeoutMs: Double,
        highAccuracy: Double,
    ): Int {
        logger.d { "getCurrentPosition(maximumAgeMs=$maximumAgeMs, timeoutMs=$timeoutMs, highAccuracy=$highAccuracy)" }
        val maxAge: Duration? = if (maximumAgeMs >= 0) maximumAgeMs.toLong().milliseconds else null
        val timeout: Duration? = if (timeoutMs >= 0) timeoutMs.toLong().milliseconds else null
        val highAccuracyBool = highAccuracy > 0
        scope.launch {
            if (!geolocationPermissionGranted()) {
                Logger.w { "Watchapp location permission not granted for getCurrentPosition" }
                triggerPositionResultGet(id.toInt(), GeolocationPositionResult.Error("Location permission not granted"))
                return@launch
            }
            triggerPositionResultGet(id.toInt(), systemGeolocation.getCurrentPosition(maxAge, timeout, highAccuracyBool))
        }
        return id.toInt()
    }

    open fun watchPosition(id: Double, interval: Double, highAccuracy: Double): Int {
        logger.d { "watchPosition(highAccuracy=$highAccuracy)" }
        val highAccuracyBool = highAccuracy > 0
        val job = scope.launch {
            if (!geolocationPermissionGranted()) {
                triggerPositionResultWatch(id.toInt(), GeolocationPositionResult.Error("Location permission not granted"))
                return@launch
            }
            systemGeolocation.watchPosition(interval.coerceAtLeast(200.0).milliseconds, highAccuracyBool).collect { result ->
                triggerPositionResultWatch(id.toInt(), result)
            }
        }
        watchJobs[id.toInt()] = job
        return id.toInt()
    }

    open fun clearWatch(id: Int) {
        logger.d { "clearWatch()" }
        watchJobs.remove(id)?.cancel("Watch cleared")
    }

    /** For engines that pass arguments as a list. */
    fun dispatchCall(method: String, args: List<Any?>): Int? {
        fun num(i: Int) = args.getOrNull(i) as? Number
        return when (method) {
            "getCurrentPosition" -> {
                val id = num(0)?.toDouble() ?: return null
                val maxAge = num(1)?.toDouble() ?: -1.0
                val timeout = num(2)?.toDouble() ?: -1.0
                val highAccuracy = num(3)?.toDouble() ?: 0.0
                getCurrentPosition(id, maxAge, timeout, highAccuracy)
            }
            "watchPosition" -> {
                val id = num(0)?.toDouble() ?: return null
                val interval = num(1)?.toDouble() ?: 500.0
                val highAccuracy = num(2)?.toDouble() ?: 0.0
                watchPosition(id, interval, highAccuracy)
            }
            "clearWatch" -> { num(0)?.toInt()?.let { clearWatch(it) }; null }
            "getRequestCallbackID" -> getRequestCallbackID()
            "getWatchCallbackID" -> getWatchCallbackID()
            else -> error("Unknown method: $method")
        }
    }

    fun clearAllWatches() {
        watchJobs.values.forEach { it.cancel() }
        watchJobs.clear()
    }
}