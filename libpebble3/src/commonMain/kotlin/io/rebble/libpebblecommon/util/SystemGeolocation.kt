package io.rebble.libpebblecommon.util

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds

public sealed class GeolocationPositionResult {
    public data class Success(
        val timestamp: Instant,
        val latitude: Double,
        val longitude: Double,
        val accuracy: Double?,
        val altitude: Double?,
        val heading: Double?,
        val speed: Double?
    ) : GeolocationPositionResult()
    public data class Error(val message: String) : GeolocationPositionResult()
}

public interface SystemGeolocation {
    public companion object {
        /**
         * Default cache freshness window when the caller doesn't specify a `maximumAge`. Spec
         * default is zero: an unspecified `maximumAge` means the caller wants a fresh fix.
         */
        public val DEFAULT_MAX_AGE: Duration = Duration.ZERO

        /**
         * Default upper bound on how long to wait for an active fix when the caller doesn't
         * specify a `timeout`. Spec default is `Infinity`; we cap to keep JS callers from
         * hanging indefinitely.
         */
        public val DEFAULT_TIMEOUT: Duration = 15.seconds
    }
    public suspend fun getCurrentPosition(
        maximumAge: Duration? = null,
        timeout: Duration? = null,
        highAccuracy: Boolean = false,
    ): GeolocationPositionResult
    public suspend fun watchPosition(
        interval: Duration,
        highAccuracy: Boolean = false,
    ): Flow<GeolocationPositionResult>
}