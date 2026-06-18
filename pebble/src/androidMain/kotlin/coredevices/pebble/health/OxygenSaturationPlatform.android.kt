package coredevices.pebble.health

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Percentage
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.database.entity.Spo2ReadingEntity
import kotlinx.coroutines.CompletableDeferred
import org.koin.mp.KoinPlatform
import java.time.Instant
import java.time.ZoneOffset

private val logger = Logger.withTag("OxygenSaturationHC")

internal actual fun supportsOxygenSaturationWriting(): Boolean = true

private fun context(): Context = KoinPlatform.getKoin().get<Context>()

private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context())

private const val WRITE_OXYGEN_SATURATION = "android.permission.health.WRITE_OXYGEN_SATURATION"

internal actual suspend fun hasOxygenSaturationPermission(): Boolean {
    return try {
        if (HealthConnectClient.getSdkStatus(context()) != HealthConnectClient.SDK_AVAILABLE) return false
        client().permissionController.getGrantedPermissions().contains(WRITE_OXYGEN_SATURATION)
    } catch (e: Exception) {
        logger.w(e) { "Failed checking oxygen saturation permission" }
        false
    }
}

internal actual suspend fun requestOxygenSaturationPermission(): Boolean {
    if (hasOxygenSaturationPermission()) return true
    val ctx = context()
    if (HealthConnectClient.getSdkStatus(ctx) != HealthConnectClient.SDK_AVAILABLE) return false
    val deferred = OxygenSaturationPermissionBridge.begin()
    return try {
        ctx.startActivity(
            Intent(ctx, OxygenSaturationPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        deferred.await()
    } catch (e: Exception) {
        logger.w(e) { "Failed to launch oxygen saturation permission request" }
        OxygenSaturationPermissionBridge.complete(false)
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

internal object OxygenSaturationPermissionBridge {
    @Volatile
    private var pending: CompletableDeferred<Boolean>? = null

    fun begin(): CompletableDeferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()
        pending = deferred
        return deferred
    }

    fun complete(result: Boolean) {
        pending?.complete(result)
        pending = null
    }
}

/**
 * Translucent, no-history host for the Health Connect blood-oxygen permission contract. health-kmp's
 * [HealthConnectPermissionActivity] only requests its own type set, so SpO2 needs its own launcher.
 */
class OxygenSaturationPermissionActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted: Set<String> ->
        OxygenSaturationPermissionBridge.complete(WRITE_OXYGEN_SATURATION in granted)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            requestPermissionLauncher.launch(setOf(WRITE_OXYGEN_SATURATION))
        } catch (e: Exception) {
            logger.w(e) { "Failed launching SpO2 permission contract" }
            OxygenSaturationPermissionBridge.complete(false)
            finish()
        }
    }
}
