package coredevices.pebble.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Percentage
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.database.entity.Spo2ReadingEntity
import org.koin.mp.KoinPlatform
import java.time.Instant
import java.time.ZoneOffset

internal val logger = Logger.withTag("OxygenSaturationHC")

internal actual fun supportsOxygenSaturationWriting(): Boolean = true

internal fun context(): Context = KoinPlatform.getKoin().get<Context>()

private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context())

internal const val WRITE_OXYGEN_SATURATION = "android.permission.health.WRITE_OXYGEN_SATURATION"

internal actual suspend fun hasOxygenSaturationPermission(): Boolean {
    return try {
        if (HealthConnectClient.getSdkStatus(context()) != HealthConnectClient.SDK_AVAILABLE) return false
        client().permissionController.getGrantedPermissions().contains(WRITE_OXYGEN_SATURATION)
    } catch (e: Exception) {
        logger.w(e) { "Failed checking oxygen saturation permission" }
        false
    }
}

internal actual suspend fun writeOxygenSaturationToPlatform(readings: List<Spo2ReadingEntity>): Boolean {
    if (readings.isEmpty()) return true
    return try {
        if (!hasOxygenSaturationPermission()) {
            logger.w { "Skipping SpO2 write: permission not granted" }
            return false
        }
        val device = Device(
            type = Device.TYPE_WATCH,
            manufacturer = "Pebble",
            model = "Watch",
        )
        val records = readings.map { reading ->
            OxygenSaturationRecord(
                time = Instant.ofEpochSecond(reading.timestamp),
                zoneOffset = ZoneOffset.UTC,
                percentage = Percentage(reading.spo2Percent.toDouble()),
                metadata = Metadata.autoRecorded(device = device),
            )
        }
        client().insertRecords(records)
        logger.d { "Wrote ${records.size} SpO2 readings to Health Connect" }
        true
    } catch (e: Exception) {
        logger.e(e) { "Failed writing SpO2 readings to Health Connect" }
        false
    }
}
