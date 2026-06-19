package coredevices.pebble.health

import io.rebble.libpebblecommon.database.entity.Spo2ReadingEntity

/**
 * Platform hooks for writing blood oxygen (SpO2) to the phone's health platform (Health Connect
 * on Android). The health-kmp library (1.4.0) does not model OxygenSaturation, so SpO2 is
 * handled through these expect/actual seams. On unsupported platforms every call is a no-op.
 */
internal expect fun supportsOxygenSaturationWriting(): Boolean

internal expect suspend fun hasOxygenSaturationPermission(): Boolean

/** Writes the given SpO2 readings to the platform. Returns true on success. */
internal expect suspend fun writeOxygenSaturationToPlatform(readings: List<Spo2ReadingEntity>): Boolean
