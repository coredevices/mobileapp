package coredevices.coreapp

import coredevices.util.AndroidPlatform
import android.Manifest
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class PermissionRequestHandler(val requestPermission: suspend (Array<String>) -> Map<String, Boolean>) {
    private val scope = CoroutineScope(Dispatchers.Default)

    fun startPermissionTriggerHandlers() {
        AndroidPlatform.notificationPermTrigger.onEach {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                AndroidPlatform.notificationPermResults.emit(
                    requestPermission(arrayOf(Manifest.permission.POST_NOTIFICATIONS))[Manifest.permission.POST_NOTIFICATIONS]!!
                )
            } else {
                AndroidPlatform.notificationPermResults.emit(true)
            }
        }.launchIn(scope)
        AndroidPlatform.alarmPermTrigger.onEach {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AndroidPlatform.alarmPermResults.emit(
                    requestPermission(arrayOf(Manifest.permission.SCHEDULE_EXACT_ALARM))[Manifest.permission.SCHEDULE_EXACT_ALARM]!!
                )
            } else {
                AndroidPlatform.alarmPermResults.emit(true)
            }
        }.launchIn(scope)
    }
}