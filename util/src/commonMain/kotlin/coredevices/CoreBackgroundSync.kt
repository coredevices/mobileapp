package coredevices

interface CoreBackgroundSync {
    suspend fun doBackgroundSync()
}