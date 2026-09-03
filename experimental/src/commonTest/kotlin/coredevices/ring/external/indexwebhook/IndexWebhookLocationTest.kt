package coredevices.ring.external.indexwebhook

import io.rebble.libpebblecommon.util.GeolocationPositionResult
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IndexWebhookLocationTest {

    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000L)

    @Test
    fun validLocationIsRoundedToThreeDecimals() {
        val location = indexWebhookLocation(
            success(latitude = 37.7749, longitude = -122.4194),
            now,
        )

        assertEquals(
            IndexWebhookLocation(
                latitude = 37.775,
                longitude = -122.419,
                timestamp = now.toEpochMilliseconds(),
            ),
            location,
        )
    }

    @Test
    fun staleOrFutureLocationIsRejected() {
        assertNull(indexWebhookLocation(success(timestamp = now - 1.hours - 1.seconds), now))
        assertNull(indexWebhookLocation(success(timestamp = now + 1.hours), now))
    }

    @Test
    fun invalidCoordinatesAreRejected() {
        assertNull(indexWebhookLocation(success(latitude = Double.NaN), now))
        assertNull(indexWebhookLocation(success(longitude = 181.0), now))
    }

    @Test
    fun disabledLocationDoesNotCheckPermissionOrPosition() = runTest {
        var permissionChecks = 0
        var positionRequests = 0

        val location = resolveIndexWebhookLocation(
            includeLocation = false,
            hasPermission = {
                permissionChecks++
                true
            },
            getCurrentPosition = {
                positionRequests++
                success()
            },
            now = { now },
        )

        assertNull(location)
        assertEquals(0, permissionChecks)
        assertEquals(0, positionRequests)
    }

    @Test
    fun deniedPermissionDoesNotRequestPosition() = runTest {
        var positionRequests = 0

        val location = resolveIndexWebhookLocation(
            includeLocation = true,
            hasPermission = { false },
            getCurrentPosition = {
                positionRequests++
                success()
            },
            now = { now },
        )

        assertNull(location)
        assertEquals(0, positionRequests)
    }

    @Test
    fun positionErrorsAndExceptionsOmitLocation() = runTest {
        assertNull(
            resolveIndexWebhookLocation(
                includeLocation = true,
                hasPermission = { true },
                getCurrentPosition = { GeolocationPositionResult.Error("unavailable") },
                now = { now },
            )
        )
        assertNull(
            resolveIndexWebhookLocation(
                includeLocation = true,
                hasPermission = { true },
                getCurrentPosition = { error("provider failed") },
                now = { now },
            )
        )
    }

    private fun success(
        timestamp: Instant = now,
        latitude: Double = 1.0,
        longitude: Double = 2.0,
    ) = GeolocationPositionResult.Success(
        timestamp = timestamp,
        latitude = latitude,
        longitude = longitude,
        accuracy = null,
        altitude = null,
        heading = null,
        speed = null,
    )
}
