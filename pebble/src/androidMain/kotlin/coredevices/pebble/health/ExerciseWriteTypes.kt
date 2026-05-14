package coredevices.pebble.health

import com.viktormykhailiv.kmp.health.HealthDataType

internal actual fun exerciseWriteTypes(): List<HealthDataType> = listOf(
    HealthDataType.Exercise(
        activeEnergyBurned = false,
        cyclingPower = false,
        cyclingSpeed = false,
        flightsClimbed = false,
        distanceWalkingRunning = true,
        runningSpeed = false,
    ),
)

internal actual fun supportsSleepWriting(): Boolean = true
