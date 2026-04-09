package coredevices.ring.service

import kotlinx.coroutines.flow.StateFlow

expect class RingBackgroundManager() {
    fun startBackground()
    fun stopBackground()
    fun startBackgroundIfEnabled()
    val isRunning: StateFlow<Boolean>
}