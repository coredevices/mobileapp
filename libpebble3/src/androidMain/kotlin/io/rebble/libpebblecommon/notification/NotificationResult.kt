package io.rebble.libpebblecommon.io.rebble.libpebblecommon.notification

import io.rebble.libpebblecommon.notification.NotificationDecision

sealed class NotificationResult {
    /**
     * Extracted. Maybe send to watch, depending on decision.
     */
    data class Extracted(
        val notification: LibPebbleNotification,
    ) : NotificationResult()

    /**
     * Not processed - try with another processor.
     */
    data object NotProcessed : NotificationResult()
}