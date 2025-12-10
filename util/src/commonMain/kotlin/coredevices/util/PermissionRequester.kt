package coredevices.util

import PlatformUiContext
import co.touchlab.kermit.Logger
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class PermissionResult {
    Granted,
    Rejected,
    RejectedForever,
    Error,
    Unknown,
}

fun Permission.name(): String = when (this) {
    Permission.Location -> "Location"
    Permission.BackgroundLocation -> "Background Location"
    Permission.PostNotifications -> "Post Notifications"
    Permission.Bluetooth -> "Bluetooth"
    Permission.ReadCallLog -> "Missed Calls"
    Permission.Calendar -> "Calendar"
    Permission.Contacts -> "Contacts"
    Permission.ReadPhoneState -> "Phone Calls"
    Permission.ReadNotifications -> "Read Notifications"
    Permission.RecordAudio -> "Record Audio"
    Permission.SpeechRecognizer -> "Speech Recognizer"
    Permission.ExternalStorage -> "External Storage"
    Permission.SetAlarms -> "Set Alarms"
    Permission.BatteryOptimization -> "Exempt from Battery Optimizations"
}

fun Permission.description(): String = when (this) {
    Permission.Location -> "To power watchfaces using location for e.g. weather"
    Permission.BackgroundLocation -> "To power watchfaces using location for e.g. weather, while the Pebble app is running in the background"
    Permission.PostNotifications -> "To create notifications when an update is available, and to show the current connection status to the watch"
    Permission.Bluetooth -> "To connect over bluetooth"
    Permission.ReadCallLog -> "To access the call log - to show missed calls on the watch"
    Permission.Calendar -> "To show calendar events on the watch, and respond to invitations"
    Permission.Contacts -> "To show who is calling, and filter notifications by contact"
    Permission.ReadPhoneState -> "To show phone calls on the watch"
    Permission.ReadNotifications -> "To display notifications on the watch"
    Permission.RecordAudio -> "To record manual local notes"
    Permission.SpeechRecognizer -> "To record voice notes"
    Permission.ExternalStorage -> "To store recordings"
    Permission.SetAlarms -> "To notify when a reminder is triggered"
    Permission.BatteryOptimization -> "To handle recordings in the background"
}

abstract class PermissionRequester(
    private val requiredPermissions: RequiredPermissions,
    protected val appResumed: AppResumed,
) {
    protected val logger = Logger.withTag("PermissionRequester")
    private val _missingPermissions = MutableStateFlow<Set<Permission>>(emptySet())
    val missingPermissions: StateFlow<Set<Permission>> = _missingPermissions.asStateFlow()
    private val permissionRefreshRequests = MutableSharedFlow<Unit>(replay = 1)

    fun init() {
        logger.v { "init()" }
        GlobalScope.launch {
            logger.v { "globalscope" }
            requiredPermissions.requiredPermissions
                .distinctUntilChanged()
                .combine(permissionRefreshRequests) { requiredPermissions, _ ->
                    logger.d { "refreshing permissions..." }
                    requiredPermissions.filter { permission -> !hasPermission(permission) }
                }.collect {
                    logger.d { "missingPermissions: $it" }
                    _missingPermissions.value = it.toSet()
                }
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

expect fun Permission.requestIsFullScreen(): Boolean

data class RequiredPermissions(
    val requiredPermissions: Flow<Set<Permission>>
)

fun Boolean.asPermissionResult(): PermissionResult = if (this) {
    PermissionResult.Granted
} else {
    PermissionResult.Rejected
}