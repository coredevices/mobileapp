package io.rebble.libpebblecommon.io.rebble.libpebblecommon.util

import LibPebbleSwift.IOSLocation
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.util.GeolocationPositionResult
import io.rebble.libpebblecommon.util.SystemGeolocation
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlin.time.Instant
import platform.CoreLocation.CLLocation
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970
import kotlin.time.Duration

class IosSystemGeolocation: SystemGeolocation {
    private val logger = Logger.withTag("IosSystemGeolocation")
    private val location = callbackFlow {
        val iosLocation = IOSLocation(
            locationCallback = { location: CLLocation? ->
                val result = if (location != null) {
                    val timestamp = Instant.fromEpochMilliseconds((location.timestamp.timeIntervalSince1970 * 1000).toLong())
                    GeolocationPositionResult.Success(
                        timestamp = timestamp,
                        latitude = location.coordinate.useContents { latitude },
                        longitude = location.coordinate.useContents { longitude },
                        accuracy = location.horizontalAccuracy,
                        altitude = location.altitude,
                        heading = location.course,
                        speed = location.speed
                    )
                } else {
                    GeolocationPositionResult.Error("Location is null")
                }
                trySend(result)
            },
            authorizationCallback = { granted: Boolean ->
                if (granted) {
                    logger.d { "Location access granted" }
                } else {
                    logger.w { "Location access denied" }
                    trySend(GeolocationPositionResult.Error("Location access denied"))
                }
            },
            errorCallback = { error: NSError? ->
                if (error != null) {
                    logger.e { "Location error: ${error.localizedDescription}" }
                    trySend(GeolocationPositionResult.Error("Location error: ${error.localizedDescription}"))
                } else {
                    logger.w { "Unknown location error" }
                    trySend(GeolocationPositionResult.Error("Unknown location error"))
                }
            }
        )
        awaitClose {
            logger.d { "Stopping location updates" }
            iosLocation.stop()
        }
    }.flowOn(Dispatchers.Main).shareIn(GlobalScope, SharingStarted.WhileSubscribed(1000), 1)

    override suspend fun getCurrentPosition(): GeolocationPositionResult {
        return location.first()
    }

    override suspend fun watchPosition(interval: Duration): Flow<GeolocationPositionResult> = location
}