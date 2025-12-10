package coredevices.coreapp.util

import PlatformUiContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual data object AppUpdatePlatformContent

class IosAppUpdate : AppUpdate {
    private val _updateAvailable = MutableStateFlow<AppUpdateState>(AppUpdateState.NoUpdateAvailable)
    override val updateAvailable: StateFlow<AppUpdateState> = _updateAvailable.asStateFlow()
    override fun startUpdateFlow(uiContext: PlatformUiContext, update: AppUpdatePlatformContent) {
    }
}