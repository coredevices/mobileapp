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
import io.rebble.libpebblecommon.util.SystemGeolocation.Companion.MAX_CACHED_TIME
import io.rebble.libpebblecommon.util.SystemGeolocation.Companion.MAX_FALLBACK_TIME
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
    private fun locationFlow(intervalMillis: Long) = callbackFlow {
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
            logger.d { "Flow using location provider: $bestProvider" }
            val locationListener = object : LocationListenerCompat {
                override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) {
                    logger.d { "Location provider $provider status changed: $status" }
                }
                override fun onLocationChanged(location: Location) {
                    Logger.d { "Got location via ${location.provider}" }
                    trySend(location.toResult())
                }
            }
            withContext(Dispatchers.Main) {
                // This can crash if done away from main thread
                LocationManagerCompat.requestLocationUpdates(
                    locationManager,
                    bestProvider,
                    LocationRequestCompat.Builder(intervalMillis)
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

    override suspend fun getCurrentPosition(): GeolocationPositionResult {
        logger.d { "getCurrentPosition called" }
        return if (checkPermission()) {
            val location = suspendCancellableCoroutine { cont ->
                val lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (Clock.System.now() - Instant.fromEpochMilliseconds(lastKnownLocation?.time ?: 0) < MAX_CACHED_TIME) {
                    logger.d { "Returning last known location" }
                    cont.resume(lastKnownLocation)
                    return@suspendCancellableCoroutine
                }
                val bestProvider = getBestProvider()
                if (bestProvider == null) {
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }
                
                logger.d { "Requesting current location from provider: $bestProvider" }
                val cancellationSignal = androidx.core.os.CancellationSignal()
                cont.invokeOnCancellation {
                    cancellationSignal.cancel()
                }
                
                val executor: Executor = ContextCompat.getMainExecutor(context)
                LocationManagerCompat.getCurrentLocation(
                    locationManager,
                    bestProvider,
                    cancellationSignal,
                    executor
                ) { location ->
                    cont.resume(
                        location ?: if (Clock.System.now() - Instant.fromEpochMilliseconds(lastKnownLocation?.time ?: 0) > MAX_FALLBACK_TIME) {
                            logger.w { "No current location available, last location too old" }
                            null
                        } else {
                            logger.w { "No current location available, returning last known location" }
                            lastKnownLocation
                        }
                    )
                }
            }
            if (location != null) {
                Logger.d { "Got location successfully" }
                location.toResult()
            } else {
                GeolocationPositionResult.Error("Location not available")
            }
        } else {
            GeolocationPositionResult.Error("Location permission not granted")
        }
    }

    override suspend fun watchPosition(interval: Duration): Flow<GeolocationPositionResult> = locationFlow(interval.inWholeMilliseconds)
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
