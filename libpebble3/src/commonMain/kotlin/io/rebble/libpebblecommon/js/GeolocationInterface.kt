package io.rebble.libpebblecommon.js

import co.touchlab.kermit.Logger
import io.ktor.http.quote
import io.rebble.libpebblecommon.database.dao.LockerAppPermissionDao
import io.rebble.libpebblecommon.database.entity.LockerAppPermissionType
import io.rebble.libpebblecommon.di.LibPebbleKoinComponent
import io.rebble.libpebblecommon.util.GeolocationPositionResult
import io.rebble.libpebblecommon.util.SystemGeolocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

abstract class GeolocationInterface(
    private val scope: CoroutineScope,
    private val jsRunner: JsRunner,
): LibPebbleKoinComponent {
    private val logger = Logger.withTag("GeolocationInterface")
    private val permissionDao: LockerAppPermissionDao by inject()
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
                jsRunner.eval("_PebbleGeoCB._resultGetSuccess($id, ${result.latitude}, ${result.longitude}, ${result.accuracy}, ${result.altitude}, ${result.heading}, ${result.speed})")
            }
            is GeolocationPositionResult.Error -> {
                Logger.w { "Geolocation get position error: ${result.message}" }
                jsRunner.eval("_PebbleGeoCB._resultGetError($id, ${result.message.quote()})")
            }
        }
    }

    private suspend fun triggerPositionResultWatch(id: Int, result: GeolocationPositionResult) {
        when (result) {
            is GeolocationPositionResult.Success -> {
                jsRunner.eval("_PebbleGeoCB._resultWatchSuccess($id, ${result.latitude}, ${result.longitude}, ${result.accuracy}, ${result.altitude}, ${result.heading}, ${result.speed})")
            }
            is GeolocationPositionResult.Error -> {
                jsRunner.eval("_PebbleGeoCB._resultWatchError($id, ${result.message.quote()})")
            }
        }
    }

    private suspend fun geolocationPermissionGranted(): Boolean =
        permissionDao.getByAppUuidAndPermission(
            Uuid.parse(jsRunner.appInfo.uuid),
            LockerAppPermissionType.Location
        )?.granted != false //TODO: deny by default?

    open fun getRequestCallbackID() = getNextRequestID()
    open fun getWatchCallbackID() = getNextWatchID()

    open fun getCurrentPosition(id: Double): Int {
        logger.d { "getCurrentPosition()" }
        scope.launch {
            if (!geolocationPermissionGranted()) {
                Logger.w { "Watchapp location permission not granted for getCurrentPosition" }
                triggerPositionResultGet(id.toInt(), GeolocationPositionResult.Error("Location permission not granted"))
                return@launch
            }
            triggerPositionResultGet(id.toInt(), systemGeolocation.getCurrentPosition())
        }
        return id.toInt()
    }

    open fun watchPosition(id: Double, interval: Double): Int {
        logger.d { "watchPosition()" }
        val job = scope.launch {
            if (!geolocationPermissionGranted()) {
                triggerPositionResultWatch(id.toInt(), GeolocationPositionResult.Error("Location permission not granted"))
                return@launch
            }
            systemGeolocation.watchPosition(interval.coerceAtLeast(200.0).milliseconds).collect { result ->
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
}