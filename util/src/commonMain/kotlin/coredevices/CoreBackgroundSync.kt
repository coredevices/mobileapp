package coredevices

import kotlin.time.Duration

interface CoreBackgroundSync {
    suspend fun doBackgroundSync(force: Boolean)
    suspend fun timeSinceLastSync(): Duration
    fun updateFullSyncPeriod(interval: Duration)
    fun updateWeatherSyncPeriod(interval: Duration)
}