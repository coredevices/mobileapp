package coredevices.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AppResumed {
    private val _appResumed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val appResumed = _appResumed.asSharedFlow()

    fun onAppResumed() {
        _appResumed.tryEmit(Unit)
    }
}