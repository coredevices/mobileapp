package coredevices.pebble.health

import io.rebble.libpebblecommon.database.entity.Spo2ReadingEntity

// Apple Health blood-oxygen writing is not yet wired up; treat as unsupported.
internal actual fun supportsOxygenSaturationWriting(): Boolean = false

internal actual suspend fun hasOxygenSaturationPermission(): Boolean = false

internal actual suspend fun requestOxygenSaturationPermission(): Boolean = false

internal actual suspend fun writeOxygenSaturationToPlatform(readings: List<Spo2ReadingEntity>): Boolean = false
