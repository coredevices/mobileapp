package coredevices.util.queue

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

abstract class PersistentQueueScheduler<T : QueueTask>(
    private val repository: QueueTaskRepository<T>,
    private val scope: CoroutineScope,
    label: String,
    private val rescheduleDelay: Duration = 1.minutes,
    private val maxAttempts: Int = 3,
): AutoCloseable {
    private val logger = Logger.withTag("Queue-$label")
    private val queueActor = MutableSharedFlow<Long>(extraBufferCapacity = Int.MAX_VALUE)
    private var scheduledTaskBefore = false
    private val _currentTaskId = MutableStateFlow<Long?>(null)
    val currentTaskId = _currentTaskId.asStateFlow()

    private val job = queueActor.onEach {
        try {
            logger.i { "Processing task with id $it" }
            _currentTaskId.value = it
            val task = repository.getTaskById(it) ?: error("Task with id $it not found")
            if (task.status != TaskStatus.Pending) {
                logger.w { "Task with id $it is not pending (status: ${task.status}), skipping" }
                return@onEach
            }
            if (task.attempts >= maxAttempts) {
                logger.e { "Task with id $it has reached max attempts ($maxAttempts), marking as failed" }
                repository.updateStatus(it, TaskStatus.Failed)
                return@onEach
            }
            repository.incrementAttempts(it)
            processTask(task)
            logger.i { "Task with id $it processed successfully" }
            repository.updateStatus(it, TaskStatus.Success)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (e !is RecoverableTaskException) {
                // Give up on this task
                logger.e(e) { "Fatal error processing task: ${e.message}" }
                repository.updateStatus(it, TaskStatus.Failed)
            } else {
                logger.e(e) { "Error processing task, will retry later: ${e.message}" }
                scope.launch {
                    delay(rescheduleDelay)
                    scheduleTask(it)
                }
            }
        } finally {
            _currentTaskId.value = null
        }
    }.launchIn(scope)

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
        queueActor.tryEmit(id)
    }

    abstract suspend fun processTask(task: T)

    override fun close() {
        job.cancel()
    }
}