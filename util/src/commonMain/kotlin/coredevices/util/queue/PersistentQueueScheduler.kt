package coredevices.util.queue

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

abstract class PersistentQueueScheduler<T : QueueTask>(
    private val repository: QueueTaskRepository<T>,
    private val scope: CoroutineScope,
    private val settings: Settings,
    label: String,
    private val rescheduleDelay: Duration = 1.minutes,
    private val maxAttempts: Int = 3,
    private val maxConcurrency: Int = 1,
): AutoCloseable {
    private val logger = Logger.withTag("Queue-$label")
    private val queueActor = Channel<Long>(Channel.UNLIMITED)
    private var scheduledTaskBefore = false
    private val _activeTaskIds = MutableStateFlow<Set<Long>>(emptySet())
    val activeTaskIds = _activeTaskIds.asStateFlow()

    private val inProgressKey = "queue_${label}_in_progress"
    private val inProgressLock = Mutex()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val job = queueActor.receiveAsFlow().flatMapMerge(concurrency = maxConcurrency) { id ->
        flow<Unit> {
            try {
                logger.i { "Processing task with id $id" }
                _activeTaskIds.update { it + id }
                val task = repository.getTaskById(id) ?: error("Task with id $id not found")
                if (task.status != TaskStatus.Pending) {
                    logger.w { "Task with id $id is not pending (status: ${task.status}), skipping" }
                    return@flow
                }
                if (task.attempts >= maxAttempts) {
                    logger.e { "Task with id $id has reached max attempts ($maxAttempts), marking as failed" }
                    repository.updateStatus(id, TaskStatus.Failed)
                    return@flow
                }
                repository.incrementAttempts(id)
                setInProgress(id, true)
                processTask(task)
                logger.i { "Task with id $id processed successfully" }
                setInProgress(id, false)
                repository.updateStatus(id, TaskStatus.Success)
            } catch (e: CancellationException) {
                withContext(NonCancellable) { setInProgress(id, false) }
                throw e
            } catch (e: Throwable) {
                setInProgress(id, false)
                if (e !is RecoverableTaskException) {
                    // Give up on this task
                    logger.e(e) { "Fatal error processing task: ${e.message}" }
                    repository.updateStatus(id, TaskStatus.Failed)
                } else {
                    logger.e(e) { "Error processing task, will retry later: ${e.message}" }
                    scope.launch {
                        delay(rescheduleDelay)
                        scheduleTask(id)
                    }
                }
            } finally {
                _activeTaskIds.update { it - id }
            }
        }
    }.flowOn(Dispatchers.IO).launchIn(scope)

    private fun readInProgressIds(): Set<Long> =
        settings.getStringOrNull(inProgressKey)
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()

    private suspend fun setInProgress(id: Long, inProgress: Boolean) = inProgressLock.withLock {
        val ids = readInProgressIds()
        val updated = if (inProgress) ids + id else ids - id
        if (updated.isEmpty()) {
            settings.remove(inProgressKey)
        } else {
            settings.putString(inProgressKey, updated.joinToString(","))
        }
    }

    /**
     * Call this on app startup to resume any pending tasks that were not completed in the previous session.
     * A task still recorded as in-progress was mid-attempt when the process died (possibly the cause of a
     * crash), so it is failed rather than resumed to avoid a crash loop.
     */
    fun resumePendingTasks() {
        if (scheduledTaskBefore) {
            logger.w { "resumePendingTasks already called, skipping" }
            return
        }
        scheduledTaskBefore = true
        val interruptedIds = readInProgressIds()
        settings.remove(inProgressKey)
        scope.launch {
            val (interrupted, resumable) = repository.getPendingTasks().partition { it.id in interruptedIds }
            interrupted.forEach {
                logger.e { "Task with id ${it.id} was in progress when the app last stopped, marking as failed" }
                repository.updateStatus(it.id, TaskStatus.Failed)
            }
            logger.i { "Rescheduling ${resumable.size} pending task(s)" }
            resumable.forEach { scheduleTask(it.id) }
        }
    }

    protected fun scheduleTask(id: Long) {
        scheduledTaskBefore = true
        logger.d { "Scheduling task with id $id" }
        queueActor.trySend(id)
    }

    abstract suspend fun processTask(task: T)

    override fun close() {
        job.cancel()
    }
}