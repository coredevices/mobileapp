package coredevices.coreapp.util

import PlatformUiContext
import kotlinx.coroutines.flow.StateFlow

sealed class AppUpdateState {
    data object NoUpdateAvailable : AppUpdateState()
    data class UpdateAvailable(
        val update: AppUpdatePlatformContent,
    ) : AppUpdateState()
}

expect class AppUpdatePlatformContent

interface AppUpdate {
    val updateAvailable: StateFlow<AppUpdateState>
    fun startUpdateFlow(uiContext: PlatformUiContext, update: AppUpdatePlatformContent)
}
