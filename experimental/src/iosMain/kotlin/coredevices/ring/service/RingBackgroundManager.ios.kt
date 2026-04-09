package coredevices.ring.service

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

actual class RingBackgroundManager: KoinComponent {
    private val backgroundRingService: BackgroundRingService by inject()
    actual val isRunning = backgroundRingService.isRunning

    actual fun startBackground() {
        backgroundRingService.startRingSyncJob()
    }

    actual fun stopBackground() {
        backgroundRingService.stopRingSyncJob()
    }

    actual fun startBackgroundIfEnabled() {
        if (!isRunning.value) {
            startBackground()
        }
    }
}