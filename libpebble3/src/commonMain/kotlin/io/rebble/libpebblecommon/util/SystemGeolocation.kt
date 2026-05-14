package io.rebble.libpebblecommon.util

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

sealed class GeolocationPositionResult {
    data class Success(
        val timestamp: Instant,
        val latitude: Double,
        val longitude: Double,
        val accuracy: Double?,
        val altitude: Double?,
        val heading: Double?,
        val speed: Double?
    ) : GeolocationPositionResult()
    data class Error(val message: String) : GeolocationPositionResult()
}

interface SystemGeolocation {
    companion object {
        /**
         * Default cache freshness window when the caller doesn't specify a `maximumAge`.
         */
        val DEFAULT_MAX_AGE = 30.minutes

        /**
         * Default upper bound on how long to wait for an active fix when the caller doesn't
         * specify a `timeout`. Spec default is `Infinity`; we cap to keep JS callers from
         * hanging indefinitely.
         */
        val DEFAULT_TIMEOUT = 15.seconds
    }
    suspend fun getCurrentPosition(
        maximumAge: Duration? = null,
        timeout: Duration? = null,
        highAccuracy: Boolean = false,
    ): GeolocationPositionResult
    suspend fun watchPosition(
        interval: Duration,
        highAccuracy: Boolean = false,
    ): Flow<GeolocationPositionResult>
}