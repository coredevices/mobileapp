package coredevices.pebble.health

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import com.viktormykhailiv.kmp.health.HealthDataType
import com.viktormykhailiv.kmp.health.HealthManager
import kotlin.reflect.KClass
import kotlinx.coroutines.CompletableDeferred

/**
 * Requests every Health Connect write permission the app needs in a single system prompt.
 *
 * health-kmp 1.4.0 models its own type set and launches a separate permission activity for it; it
 * also doesn't model OxygenSaturation, which previously forced a second prompt just for SpO2. To
 * keep the UX to one prompt, this bypasses health-kmp's [requestAuthorization] on Android and asks
 * for the standard permissions together with SpO2 via a single permission contract. SpO2 is treated
 * as best-effort: the result reflects only whether the standard types were granted, so denying SpO2
 * doesn't disable sync for steps/heart rate/sleep/exercise.
 */
internal actual suspend fun requestPlatformHealthPermissions(
    healthManager: HealthManager,
    readTypes: List<HealthDataType>,
    writeTypes: List<HealthDataType>,
): Boolean {
    if (!healthManager.isAvailable().getOrDefault(false)) return false
    val ctx = context()
    val writePerms = writeTypes.flatMapTo(mutableSetOf()) { it.toHealthConnectWritePermissions() }
    val readPerms = readTypes.flatMapTo(mutableSetOf()) { it.toHealthConnectReadPermissions() }
    val standardPerms = writePerms + readPerms
    val allPerms = standardPerms + WRITE_OXYGEN_SATURATION
    val granted = HealthConnectPermissionBridge.request(ctx, allPerms)
    return standardPerms.all { it in granted }
}

private const val KEY_PERMISSIONS = "KEY_PERMISSIONS"

private fun HealthDataType.toHealthConnectWritePermissions(): Set<String> = when (this) {
    HealthDataType.Steps -> setOf(writePermission(StepsRecord::class))
    HealthDataType.HeartRate -> setOf(writePermission(HeartRateRecord::class))
    HealthDataType.Sleep -> setOf(writePermission(SleepSessionRecord::class))
    is HealthDataType.Exercise -> setOf(
        writePermission(ExerciseSessionRecord::class),
        HealthPermission.PERMISSION_WRITE_EXERCISE_ROUTE,
    )
    else -> emptySet()
}

private fun HealthDataType.toHealthConnectReadPermissions(): Set<String> = when (this) {
    HealthDataType.Steps -> setOf(readPermission(StepsRecord::class))
    HealthDataType.HeartRate -> setOf(readPermission(HeartRateRecord::class))
    HealthDataType.Sleep -> setOf(readPermission(SleepSessionRecord::class))
    is HealthDataType.Exercise -> setOf(readPermission(ExerciseSessionRecord::class))
    else -> emptySet()
}

private fun writePermission(recordType: KClass<out Record>): String =
    HealthPermission.getWritePermission(recordType)

private fun readPermission(recordType: KClass<out Record>): String =
    HealthPermission.getReadPermission(recordType)

internal object HealthConnectPermissionBridge {
    @Volatile
    private var pending: CompletableDeferred<Set<String>?>? = null

    suspend fun request(context: Context, permissions: Set<String>): Set<String> {
        val deferred = begin()
        return try {
            context.startActivity(
                Intent(context, HealthConnectWritePermissionActivity::class.java)
                    .putExtra(KEY_PERMISSIONS, permissions.toTypedArray())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            deferred.await() ?: emptySet()
        } catch (e: Exception) {
            logger.w(e) { "Failed launching Health Connect permission request" }
            complete(null)
            emptySet()
        }
    }

    private fun begin(): CompletableDeferred<Set<String>?> {
        pending?.cancel()
        val deferred = CompletableDeferred<Set<String>?>()
        pending = deferred
        return deferred
    }

    fun complete(result: Set<String>?) {
        pending?.complete(result)
        pending = null
    }
}

/**
 * Translucent, no-history host that requests a set of Health Connect permissions in one prompt.
 */
class HealthConnectWritePermissionActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted: Set<String> ->
        HealthConnectPermissionBridge.complete(granted)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permissions = intent.getStringArrayExtra(KEY_PERMISSIONS)?.toSet().orEmpty()
        try {
            requestPermissionLauncher.launch(permissions)
        } catch (e: Exception) {
            logger.w(e) { "Failed launching Health Connect permission contract" }
            HealthConnectPermissionBridge.complete(null)
            finish()
        }
    }
}
