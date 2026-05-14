package coredevices.pebble.health

import com.viktormykhailiv.kmp.health.HealthDataType
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo

@OptIn(ExperimentalForeignApi::class)
internal actual fun exerciseWriteTypes(): List<HealthDataType> {
    val version = NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion }
    if (version < 17) return emptyList()
    return listOf(
        HealthDataType.Exercise(
            activeEnergyBurned = false,
            cyclingPower = false,
            cyclingSpeed = false,
            flightsClimbed = false,
            distanceWalkingRunning = true,
            runningSpeed = false,
        ),
    )
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun supportsSleepWriting(): Boolean {
    val version = NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion }
    return version >= 16
}
