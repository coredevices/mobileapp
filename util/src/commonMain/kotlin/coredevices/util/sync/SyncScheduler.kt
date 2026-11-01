package coredevices.util.sync

/**
 * Asks the platform to run [SyncManager.runPending] once the network is available, surviving the
 * app being backgrounded or killed. Calls are idempotent.
 */
interface SyncScheduler {
    fun schedule()
}
