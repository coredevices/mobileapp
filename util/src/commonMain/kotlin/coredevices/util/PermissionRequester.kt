package coredevices.util

import PlatformUiContext
import androidx.compose.runtime.Composable
import coreapp.util.generated.resources.Res
import coreapp.util.generated.resources.permission_description_alarm_kit
import coreapp.util.generated.resources.permission_description_background_location
import coreapp.util.generated.resources.permission_description_battery_optimization
import coreapp.util.generated.resources.permission_description_beeper
import coreapp.util.generated.resources.permission_description_bluetooth
import coreapp.util.generated.resources.permission_description_calendar
import coreapp.util.generated.resources.permission_description_contacts
import coreapp.util.generated.resources.permission_description_external_storage
import coreapp.util.generated.resources.permission_description_location
import coreapp.util.generated.resources.permission_description_post_notifications
import coreapp.util.generated.resources.permission_description_read_call_log
import coreapp.util.generated.resources.permission_description_read_notifications
import coreapp.util.generated.resources.permission_description_read_phone_state
import coreapp.util.generated.resources.permission_description_record_audio
import coreapp.util.generated.resources.permission_description_reminders
import coreapp.util.generated.resources.permission_description_set_alarms
import coreapp.util.generated.resources.permission_description_speech_recognizer
import coreapp.util.generated.resources.permission_name_alarm_kit
import coreapp.util.generated.resources.permission_name_background_location
import coreapp.util.generated.resources.permission_name_battery_optimization
import coreapp.util.generated.resources.permission_name_beeper
import coreapp.util.generated.resources.permission_name_bluetooth
import coreapp.util.generated.resources.permission_name_calendar
import coreapp.util.generated.resources.permission_name_contacts
import coreapp.util.generated.resources.permission_name_external_storage
import coreapp.util.generated.resources.permission_name_location
import coreapp.util.generated.resources.permission_name_post_notifications
import coreapp.util.generated.resources.permission_name_read_call_log
import coreapp.util.generated.resources.permission_name_read_notifications
import coreapp.util.generated.resources.permission_name_read_phone_state
import coreapp.util.generated.resources.permission_name_record_audio
import coreapp.util.generated.resources.permission_name_reminders
import coreapp.util.generated.resources.permission_name_set_alarms
import coreapp.util.generated.resources.permission_name_speech_recognizer
import org.jetbrains.compose.resources.stringResource
import co.touchlab.kermit.Logger
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class PermissionResult {
    Granted,
    Rejected,
    RejectedForever,
    Error,
    Unknown,
}

@Composable
fun Permission.name(): String = when (this) {
    Permission.Location -> stringResource(Res.string.permission_name_location)
    Permission.BackgroundLocation -> stringResource(Res.string.permission_name_background_location)
    Permission.PostNotifications -> stringResource(Res.string.permission_name_post_notifications)
    Permission.Bluetooth -> stringResource(Res.string.permission_name_bluetooth)
    Permission.ReadCallLog -> stringResource(Res.string.permission_name_read_call_log)
    Permission.Calendar -> stringResource(Res.string.permission_name_calendar)
    Permission.Contacts -> stringResource(Res.string.permission_name_contacts)
    Permission.ReadPhoneState -> stringResource(Res.string.permission_name_read_phone_state)
    Permission.ReadNotifications -> stringResource(Res.string.permission_name_read_notifications)
    Permission.RecordAudio -> stringResource(Res.string.permission_name_record_audio)
    Permission.SpeechRecognizer -> stringResource(Res.string.permission_name_speech_recognizer)
    Permission.ExternalStorage -> stringResource(Res.string.permission_name_external_storage)
    Permission.SetAlarms -> stringResource(Res.string.permission_name_set_alarms)
    Permission.BatteryOptimization -> stringResource(Res.string.permission_name_battery_optimization)
    Permission.Beeper -> stringResource(Res.string.permission_name_beeper)
    Permission.Reminders -> stringResource(Res.string.permission_name_reminders)
    Permission.AlarmKit -> stringResource(Res.string.permission_name_alarm_kit)
}

@Composable
fun Permission.description(): String = when (this) {
    Permission.Location -> stringResource(Res.string.permission_description_location)
    Permission.BackgroundLocation -> stringResource(Res.string.permission_description_background_location)
    Permission.PostNotifications -> stringResource(Res.string.permission_description_post_notifications)
    Permission.Bluetooth -> stringResource(Res.string.permission_description_bluetooth)
    Permission.ReadCallLog -> stringResource(Res.string.permission_description_read_call_log)
    Permission.Calendar -> stringResource(Res.string.permission_description_calendar)
    Permission.Contacts -> stringResource(Res.string.permission_description_contacts)
    Permission.ReadPhoneState -> stringResource(Res.string.permission_description_read_phone_state)
    Permission.ReadNotifications -> stringResource(Res.string.permission_description_read_notifications)
    Permission.RecordAudio -> stringResource(Res.string.permission_description_record_audio)
    Permission.SpeechRecognizer -> stringResource(Res.string.permission_description_speech_recognizer)
    Permission.ExternalStorage -> stringResource(Res.string.permission_description_external_storage)
    Permission.SetAlarms -> stringResource(Res.string.permission_description_set_alarms)
    Permission.BatteryOptimization -> stringResource(Res.string.permission_description_battery_optimization)
    Permission.Beeper -> stringResource(Res.string.permission_description_beeper)
    Permission.Reminders -> stringResource(Res.string.permission_description_reminders)
    Permission.AlarmKit -> stringResource(Res.string.permission_description_alarm_kit)
}

abstract class PermissionRequester(
    private val requiredPermissions: RequiredPermissions,
    protected val appResumed: AppResumed,
) {
    protected val logger = Logger.withTag("PermissionRequester")
    private val _missingPermissions = MutableStateFlow<Set<Permission>>(emptySet())
    val missingPermissions: StateFlow<Set<Permission>> = _missingPermissions.asStateFlow()
    private val _grantedPermissions = MutableStateFlow<Set<Permission>>(emptySet())
    val grantedPermissions: StateFlow<Set<Permission>> = _grantedPermissions
    private val permissionRefreshRequests = MutableSharedFlow<Unit>(replay = 1)

    fun init() {
        logger.v { "init()" }
        GlobalScope.launch {
            logger.v { "globalscope" }
            requiredPermissions.requiredPermissions
                .distinctUntilChanged()
                .combine(permissionRefreshRequests) { requiredPermissions, _ ->
                    logger.d { "refreshing permissions..." }
                    val allGrantedPermissions = Permission.entries.filter {
                        try { hasPermission(it) } catch (_: Exception) { false }
                    }
                    _grantedPermissions.value = allGrantedPermissions.toSet()
                    _missingPermissions.value = requiredPermissions.filter { permission -> !allGrantedPermissions.contains(permission) }.toSet()
                    logger.d { "missingPermissions: ${_missingPermissions.value}" }
                }.collect { }
        }
        GlobalScope.launch {
            appResumed.appResumed.collect {
                refreshPermissions()
            }
        }
        refreshPermissions()
    }

    suspend fun requestPermission(
        permission: Permission,
        uiContext: PlatformUiContext
    ): PermissionResult {
        val result = requestPlatformPermission(permission, uiContext)
        logger.d { "requestPermission($permission) = $result" }
        if (result == PermissionResult.Granted) {
            refreshPermissions()
        }
        return result
    }

    protected abstract suspend fun requestPlatformPermission(
        permission: Permission,
        uiContext: PlatformUiContext
    ): PermissionResult

    abstract suspend fun hasPermission(permission: Permission): Boolean
    abstract fun openPermissionsScreen(uiContext: PlatformUiContext)

    private fun refreshPermissions() {
        permissionRefreshRequests.tryEmit(Unit)
    }
}

fun PermissionRequester.granted(permission: Permission): Flow<Boolean> = grantedPermissions.map { it.contains(permission) }

expect fun Permission.requestIsFullScreen(): Boolean

data class RequiredPermissions(
    val requiredPermissions: Flow<Set<Permission>>
)

fun Boolean.asPermissionResult(): PermissionResult = if (this) {
    PermissionResult.Granted
} else {
    PermissionResult.Rejected
}