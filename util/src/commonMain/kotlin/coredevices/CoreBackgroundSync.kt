package coredevices

import kotlinx.coroutines.CoroutineScope
import kotlin.time.Duration

interface CoreBackgroundSync {
    suspend fun doBackgroundSync(scope: CoroutineScope, force: Boolean)
    suspend fun timeSinceLastSync(): Duration
    fun updateFullSyncPeriod(interval: Duration)
    fun updateWeatherSyncPeriod(interval: Duration)
}