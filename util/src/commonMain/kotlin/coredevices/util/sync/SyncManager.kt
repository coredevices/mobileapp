package coredevices.util.sync

import co.touchlab.kermit.Logger
import coredevices.database.SyncTaskEntity
import coredevices.util.queue.RecoverableTaskException
import coredevices.util.queue.TaskStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/**
 * Offline-tolerant upload queue. Tasks are persisted, then the platform [SyncScheduler] runs
 * [runPending] when connectivity allows. Handlers are resolved lazily so they may depend on
 * services that themselves enqueue work.
 */
class SyncManager(
    private val store: SyncTaskStore,
    private val handlers: () -> List<SyncHandler>,
    private val scheduler: SyncScheduler,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val clock: Clock = Clock.System,
) {
    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 20
        private val FAILED_RETENTION = 7.days
    }

    private val logger = Logger.withTag("SyncManager")
    private val runLock = Mutex()
    private val handlersByType by lazy { handlers().associateBy { it.type } }

    /**
     * @param dedupeKey when given, the task is skipped if one of the same type and key is still pending.
     * @return the task id, or null when deduplicated.
     */
    suspend fun enqueue(type: String, payload: String, dedupeKey: String? = null): Long? {
        require(type in handlersByType) { "No SyncHandler registered for type '$type'" }
        if (dedupeKey != null && store.hasPending(type, dedupeKey)) {
            logger.d { "Skipping $type task for '$dedupeKey', already pending" }
            return null
        }
        val id = store.insert(
            SyncTaskEntity(
                type = type,
                payload = payload,
                createdAt = clock.now().toEpochMilliseconds(),
                dedupeKey = dedupeKey,
            )
        )
        logger.d { "Enqueued $type task $id" }
        scheduler.schedule()
        return id
    }

    suspend fun hasPending(type: String, dedupeKey: String): Boolean = store.hasPending(type, dedupeKey)

    /** Re-arms platform scheduling for work left over by a previous process. */
    suspend fun resume() {
        val pending = store.getPending()
        if (pending.isNotEmpty()) {
            logger.i { "Resuming ${pending.size} pending sync task(s)" }
            scheduler.schedule()
        }
    }

    /**
     * Runs every pending task once, including tasks enqueued while running.
     * @return true when tasks failed transiently and the caller should schedule another run.
     */
    suspend fun runPending(): Boolean = runLock.withLock {
        store.deleteFinishedBefore((clock.now() - FAILED_RETENTION).toEpochMilliseconds())
        val attempted = mutableSetOf<Long>()
        var retryLater = false
        while (true) {
            val batch = store.getPending().filter { it.id !in attempted }
            if (batch.isEmpty()) break
            for (task in batch) {
                attempted += task.id
                if (!runTask(task)) retryLater = true
            }
        }
        retryLater
    }

    /** @return false when the task should be retried later. */
    private suspend fun runTask(task: SyncTaskEntity): Boolean {
        val handler = handlersByType[task.type]
        if (handler == null) {
            fail(task, null, "No handler for type '${task.type}'")
            return true
        }
        store.markAttempt(task.id, clock.now().toEpochMilliseconds())
        return try {
            handler.sync(task.payload)
            store.delete(task.id)
            logger.d { "Sync task ${task.id} (${task.type}) completed" }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: RecoverableTaskException) {
            val attempts = task.attempts + 1
            if (attempts >= maxAttempts) {
                fail(task, handler, "Gave up after $attempts attempts: ${e.message}")
                true
            } else {
                logger.w(e) { "Sync task ${task.id} (${task.type}) failed on attempt $attempts, will retry" }
                store.setStatus(task.id, TaskStatus.Pending, e.message)
                false
            }
        } catch (e: Throwable) {
            fail(task, handler, e.message ?: e::class.simpleName ?: "Unknown error")
            true
        }
    }

    private suspend fun fail(task: SyncTaskEntity, handler: SyncHandler?, reason: String) {
        logger.e { "Sync task ${task.id} (${task.type}) failed permanently: $reason" }
        store.setStatus(task.id, TaskStatus.Failed, reason)
        try {
            handler?.onFailed(task.payload)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.w(e) { "onFailed threw for task ${task.id}" }
        }
    }
}
