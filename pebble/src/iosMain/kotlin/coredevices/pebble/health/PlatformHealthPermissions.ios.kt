package coredevices.pebble.health

import com.viktormykhailiv.kmp.health.HealthDataType
import com.viktormykhailiv.kmp.health.HealthManager

internal actual suspend fun requestPlatformHealthPermissions(
    healthManager: HealthManager,
    readTypes: List<HealthDataType>,
    writeTypes: List<HealthDataType>,
): Boolean {
    val result = healthManager.requestAuthorization(
        readTypes = readTypes,
        writeTypes = writeTypes,
    )
    return result.getOrDefault(false)
}
