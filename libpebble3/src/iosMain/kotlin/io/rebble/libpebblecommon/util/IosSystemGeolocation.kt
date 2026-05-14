package io.rebble.libpebblecommon.io.rebble.libpebblecommon.util

import LibPebbleSwift.IOSLocation
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.util.GeolocationPositionResult
import io.rebble.libpebblecommon.util.SystemGeolocation
import io.rebble.libpebblecommon.util.SystemGeolocation.Companion.DEFAULT_MAX_AGE
import io.rebble.libpebblecommon.util.SystemGeolocation.Companion.DEFAULT_TIMEOUT
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import platform.CoreLocation.CLLocation
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970

class IosSystemGeolocation: SystemGeolocation {
    private val logger = Logger.withTag("IosSystemGeolocation")
    private var iosLocationRef: IOSLocation? = null
    private val accuracyLock = Mutex()
    private var highAccuracySubscribers = 0

    private val location = callbackFlow {
        val iosLocation = IOSLocation(
            locationCallback = { location: CLLocation? ->
                trySend(location?.toResult() ?: GeolocationPositionResult.Error("Location is null"))
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
        iosLocationRef = iosLocation
        // A high-accuracy subscriber may have arrived before the producer ran.
        applyCurrentAccuracy()
        awaitClose {
            logger.d { "Stopping location updates" }
            iosLocation.stop()
            // Keep iosLocationRef pointing at this instance — CLLocationManager retains its
            // last fix after stopUpdatingLocation, so lastLocation() stays usable for the
            // maximumAge fast-path between subscriptions.
        }
    }.flowOn(Dispatchers.Main).shareIn(GlobalScope, SharingStarted.WhileSubscribed(1000), 1)

    // CLLocationManager calls must run on the same thread the manager was created on
    // (Dispatchers.Main, via flowOn above) — touching it from a background queue throws.
    private suspend fun cachedLocation(): GeolocationPositionResult.Success? =
        withContext(Dispatchers.Main) {
            iosLocationRef?.lastLocation()?.toResult()
        }

    private suspend fun applyAccuracyOnMain(highAccuracy: Boolean) {
        withContext(Dispatchers.Main) {
            iosLocationRef?.setHighAccuracy(highAccuracy)
        }
    }

    private suspend fun applyCurrentAccuracy() = accuracyLock.withLock {
        applyAccuracyOnMain(highAccuracySubscribers > 0)
    }

    private suspend fun addHighAccuracySubscriber() = accuracyLock.withLock {
        highAccuracySubscribers++
        if (highAccuracySubscribers == 1) {
            applyAccuracyOnMain(true)
        }
    }

    private suspend fun removeHighAccuracySubscriber() = accuracyLock.withLock {
        highAccuracySubscribers--
        if (highAccuracySubscribers == 0) {
            applyAccuracyOnMain(false)
        }
    }

    override suspend fun getCurrentPosition(
        maximumAge: Duration?,
        timeout: Duration?,
        highAccuracy: Boolean,
    ): GeolocationPositionResult {
        val effectiveMaxAge = maximumAge ?: DEFAULT_MAX_AGE
        val effectiveTimeout = timeout ?: DEFAULT_TIMEOUT
        val now = Clock.System.now()
        cachedLocation()?.let { cached ->
            val age = now - cached.timestamp
            if (age < effectiveMaxAge) {
                logger.d { "Returning last known location (age=$age)" }
                return cached
            }
        }
        val active = withTimeoutOrNull(effectiveTimeout) { location.first() }
        if (active is GeolocationPositionResult.Success) return active
        cachedLocation()?.let { cached ->
            logger.w { "No current location available, returning stale last known (age=${now - cached.timestamp})" }
            return cached
        }
        return active ?: GeolocationPositionResult.Error("Location not available")
    }

    override suspend fun watchPosition(
        interval: Duration,
        highAccuracy: Boolean,
    ): Flow<GeolocationPositionResult> {
        return location
            .onStart {
                if (highAccuracy) addHighAccuracySubscriber()
            }
            .onCompletion {
                if (highAccuracy) removeHighAccuracySubscriber()
            }
    }
}

private fun CLLocation.toResult(): GeolocationPositionResult.Success {
    val ts = Instant.fromEpochMilliseconds((timestamp.timeIntervalSince1970 * 1000).toLong())
    return GeolocationPositionResult.Success(
        timestamp = ts,
        latitude = coordinate.useContents { latitude },
        longitude = coordinate.useContents { longitude },
        accuracy = horizontalAccuracy,
        altitude = altitude,
        heading = course,
        speed = speed
    )
}
