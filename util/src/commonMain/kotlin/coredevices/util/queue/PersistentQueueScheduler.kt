package coredevices.util.queue

import co.touchlab.kermit.Logger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

abstract class PersistentQueueScheduler<T : QueueTask>(
    private val repository: QueueTaskRepository<T>,
    private val scope: CoroutineScope,
    label: String,
    private val rescheduleDelay: Duration = 1.minutes,
    private val maxAttempts: Int = 3,
    private val maxConcurrency: Int = 1,
): AutoCloseable {
    private val logger = Logger.withTag("Queue-$label")
    private val queueActor = MutableSharedFlow<Long>(extraBufferCapacity = Int.MAX_VALUE)
    private var scheduledTaskBefore = false
    private val _activeTaskIds = MutableStateFlow<Set<Long>>(emptySet())
    val activeTaskIds = _activeTaskIds.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val job = queueActor.flatMapMerge(concurrency = maxConcurrency) { id ->
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
                processTask(task)
                logger.i { "Task with id $id processed successfully" }
                repository.updateStatus(id, TaskStatus.Success)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
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

    /**
     * Call this on app startup to resume any pending tasks that were not completed in the previous session.
     */
    fun resumePendingTasks() {
        check(!scheduledTaskBefore) { "resumePendingTasks should only be called once, and before any tasks are scheduled" }
        scope.launch {
            val pending = repository.getPendingTasks()
            logger.i { "Rescheduling ${pending.size} pending task(s)" }
            pending.forEach { scheduleTask(it.id) }
        }
    }

    protected fun scheduleTask(id: Long) {
        scheduledTaskBefore = true
        logger.d { "Scheduling task with id $id" }
        queueActor.tryEmit(id)
    }

    abstract suspend fun processTask(task: T)

    override fun close() {
        job.cancel()
    }
}