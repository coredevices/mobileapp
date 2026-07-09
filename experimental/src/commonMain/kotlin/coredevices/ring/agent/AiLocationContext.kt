package coredevices.ring.agent

import co.touchlab.kermit.Logger
import coredevices.HackyPermissionRequesterProvider
import coredevices.util.Permission
import io.rebble.libpebblecommon.util.GeolocationPositionResult
import io.rebble.libpebblecommon.util.SystemGeolocation
import kotlin.math.round
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/** Round to ~100m (3 decimal places ≈ 111m of latitude) so we never send a precise fix to the LLM. */
internal fun coarsenCoordinate(value: Double): Double = round(value * 1000) / 1000

/** One reference line giving the user's coarse location, for location-dependent answers. */
fun locationContext(latitude: Double, longitude: Double): String {
    val lat = coarsenCoordinate(latitude)
    val lon = coarsenCoordinate(longitude)
    return "The user's approximate location is $lat, $lon (latitude, longitude, accurate to ~100m). " +
        "Only use this if the request depends on location (e.g. weather or nearby places); otherwise ignore it."
}

/**
 * Supplies the user's coarse current location as a context line for the AI call
 */
class LLMLocationProvider(
    private val permissionProvider: HackyPermissionRequesterProvider,
    private val geolocation: SystemGeolocation
) {
    private val logger = Logger.withTag("AiLocationProvider")

    suspend fun currentLocationContext(): String? {
        if (!permissionProvider.get().hasPermission(Permission.Location)) return null
        val result = try {
            geolocation.getCurrentPosition(maximumAge = 1.hours, timeout = 1.seconds, highAccuracy = false)
        } catch (e: Exception) {
            logger.w(e) { "Failed to get current location" }
            null
        } ?: return null
        return when (result) {
            is GeolocationPositionResult.Success -> locationContext(result.latitude, result.longitude)
            is GeolocationPositionResult.Error -> {
                logger.w { "Failed to get current location: ${result.message}" }
                null
            }
        }
    }
}
