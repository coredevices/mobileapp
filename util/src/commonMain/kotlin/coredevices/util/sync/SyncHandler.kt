package coredevices.util.sync

/**
 * Performs one kind of upload for [SyncManager]. Register implementations in Koin bound to
 * [SyncHandler] and enqueue work with [SyncManager.enqueue] using the handler's [type].
 */
interface SyncHandler {
    /** Stable key persisted with each task; must not change between app versions. */
    val type: String

    /**
     * Throw [coredevices.util.queue.RecoverableTaskException] for transient failures (no network,
     * not signed in yet); any other exception fails the task permanently. Must be idempotent, since
     * a task interrupted mid-flight is run again.
     */
    suspend fun sync(payload: String)

    /** Called once a task is permanently abandoned, e.g. to clean up staged local files. */
    suspend fun onFailed(payload: String) {}
}
