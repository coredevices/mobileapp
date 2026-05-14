package io.rebble.libpebblecommon.io.rebble.libpebblecommon.util

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.util.GeolocationPositionResult
import io.rebble.libpebblecommon.util.SystemGeolocation
import io.rebble.libpebblecommon.util.SystemGeolocation.Companion.DEFAULT_MAX_AGE
import io.rebble.libpebblecommon.util.SystemGeolocation.Companion.DEFAULT_TIMEOUT
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class AndroidSystemGeolocation(appContext: AppContext): SystemGeolocation {
    companion object {
        private val logger = Logger.withTag("AndroidSystemGeolocation")
    }
    private val context = appContext.context
    private val locationManager by lazy {
        context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
    }

    @SuppressLint("MissingPermission")
    private fun locationFlow(intervalMillis: Long, highAccuracy: Boolean) = callbackFlow {
        if (!checkPermission()) {
            trySend(GeolocationPositionResult.Error("Location permission not granted"))
            close()
            awaitClose()
        } else {
            val bestProvider = getBestProvider()
            if (bestProvider == null) {
                trySend(GeolocationPositionResult.Error("Location not available, no suitable provider found"))
                close()
                awaitClose()
                return@callbackFlow
            }
            logger.d { "Flow using location provider: $bestProvider (highAccuracy=$highAccuracy)" }
            val locationListener = object : LocationListenerCompat {
                override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) {
                    logger.d { "Location provider $provider status changed: $status" }
                }
                override fun onLocationChanged(location: Location) {
                    Logger.d { "Got location via ${location.provider}" }
                    trySend(location.toResult())
                }
            }
            val quality = if (highAccuracy) {
                LocationRequestCompat.QUALITY_HIGH_ACCURACY
            } else {
                LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY
            }
            withContext(Dispatchers.Main) {
                // This can crash if done away from main thread
                LocationManagerCompat.requestLocationUpdates(
                    locationManager,
                    bestProvider,
                    LocationRequestCompat.Builder(intervalMillis)
                        .setQuality(quality)
                        .setMinUpdateDistanceMeters(0f)
                        .build(),
                    locationListener,
                    Looper.getMainLooper(),
                )
            }
            awaitClose {
                LocationManagerCompat.removeUpdates(locationManager, locationListener)
            }
        }
    }.shareIn(GlobalScope, SharingStarted.WhileSubscribed(1000))

    private fun checkPermission(): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun getBestProvider(): String? {
        val enabledProviders = locationManager.getProviders(true)
        val result = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    LocationManager.FUSED_PROVIDER in enabledProviders -> LocationManager.FUSED_PROVIDER
            LocationManager.NETWORK_PROVIDER in enabledProviders -> LocationManager.NETWORK_PROVIDER
            LocationManager.GPS_PROVIDER in enabledProviders -> LocationManager.GPS_PROVIDER
            else -> null
        }
        result ?: run {
            val providerStates = locationManager.getProviders(false).joinToString {
                "$it: ${locationManager.isProviderEnabled(it)}"
            }
            val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locationManager.isLocationEnabled.toString()
            } else {
                "Unknown (requires API 31+)"
            }
            logger.e { """No suitable location provider found, location may be disabled?
                | $providerStates
                | Location enabled: $locationEnabled
            """.trimMargin() }
        }
        return result
    }

    @SuppressLint("MissingPermission")
    private fun freshestLastKnownLocation(): Location? {
        return locationManager.getProviders(true)
            .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    override suspend fun getCurrentPosition(
        maximumAge: Duration?,
        timeout: Duration?,
        highAccuracy: Boolean,
    ): GeolocationPositionResult {
        logger.d { "getCurrentPosition called (maximumAge=$maximumAge, timeout=$timeout, highAccuracy=$highAccuracy)" }
        if (!checkPermission()) {
            return GeolocationPositionResult.Error("Location permission not granted")
        }
        val effectiveMaxAge = maximumAge ?: DEFAULT_MAX_AGE
        val effectiveTimeout = timeout ?: DEFAULT_TIMEOUT
        val now = Clock.System.now()
        val freshest = freshestLastKnownLocation()
        val freshestAge = freshest?.let { now - Instant.fromEpochMilliseconds(it.time) }
        if (freshest != null && freshestAge != null && freshestAge < effectiveMaxAge) {
            logger.d { "Returning last known location (provider=${freshest.provider}, age=$freshestAge)" }
            return freshest.toResult()
        }

        val bestProvider = getBestProvider()
        if (bestProvider == null) {
            return if (freshest != null) {
                logger.w { "No active provider; returning stale last known (age=$freshestAge)" }
                freshest.toResult()
            } else {
                GeolocationPositionResult.Error("Location not available")
            }
        }

        logger.d { "Requesting current location from provider: $bestProvider (timeout=$effectiveTimeout)" }
        val active = withTimeoutOrNull(effectiveTimeout) {
            suspendCancellableCoroutine { cont ->
                val cancellationSignal = androidx.core.os.CancellationSignal()
                cont.invokeOnCancellation { cancellationSignal.cancel() }
                val executor: Executor = ContextCompat.getMainExecutor(context)
                LocationManagerCompat.getCurrentLocation(
                    locationManager,
                    bestProvider,
                    cancellationSignal,
                    executor
                ) { location -> cont.resume(location) }
            }
        }
        return when {
            active != null -> active.toResult()
            freshest != null -> {
                logger.w { "No current location available, returning stale last known (age=$freshestAge)" }
                freshest.toResult()
            }
            else -> {
                logger.w { "No current location available and no last known location" }
                GeolocationPositionResult.Error("Location not available")
            }
        }
    }

    override suspend fun watchPosition(
        interval: Duration,
        highAccuracy: Boolean,
    ): Flow<GeolocationPositionResult> = locationFlow(interval.inWholeMilliseconds, highAccuracy)
}

private fun Location.toResult(): GeolocationPositionResult {
    return GeolocationPositionResult.Success(
        timestamp = Instant.fromEpochMilliseconds(time),
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy.toDouble().takeIf { hasAccuracy() },
        altitude = altitude,
        heading = bearing.toDouble().takeIf { hasBearing() },
        speed = speed.toDouble().takeIf { hasSpeed() }
    )
}
