package coredevices.util.queue

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private const val IN_PROGRESS_KEY = "queue_test_in_progress"

private data class FakeTask(
    override val id: Long = 0,
    override val created: Instant = Clock.System.now(),
    override val lastAttempt: Instant? = null,
    override val attempts: Int = 0,
    override val status: TaskStatus = TaskStatus.Pending,
) : QueueTask

private class FakeRepository : QueueTaskRepository<FakeTask> {
    val tasks = mutableMapOf<Long, FakeTask>()
    private var nextId = 1L

    override suspend fun insertTask(task: FakeTask): Long {
        val id = nextId++
        tasks[id] = task.copy(id = id)
        return id
    }

    override suspend fun getPendingTasks() =
        tasks.values.filter { it.status == TaskStatus.Pending }.sortedBy { it.id }

    override suspend fun updateLastSuccessfulStage(taskId: Long, stage: String) {}

    override suspend fun updateStatus(taskId: Long, status: TaskStatus) {
        tasks[taskId] = tasks.getValue(taskId).copy(status = status)
    }

    override suspend fun deleteTask(taskId: Long) {
        tasks.remove(taskId)
    }

    override suspend fun deleteCompletedTasksAttemptedBefore(before: Instant) {}

    override suspend fun getTaskById(taskId: Long) = tasks[taskId]

    override suspend fun incrementAttempts(taskId: Long, currentTime: Instant) {
        tasks[taskId] = tasks.getValue(taskId).let {
            it.copy(attempts = it.attempts + 1, lastAttempt = currentTime)
        }
    }
}

private class TestScheduler(
    repository: FakeRepository,
    scope: CoroutineScope,
    settings: MapSettings,
    private val onProcess: suspend (FakeTask) -> Unit = {},
) : PersistentQueueScheduler<FakeTask>(repository, scope, settings, "test", rescheduleDelay = 1.minutes) {
    val processed = mutableListOf<Long>()

    override suspend fun processTask(task: FakeTask) {
        processed += task.id
        onProcess(task)
    }

    fun schedule(id: Long) = scheduleTask(id)
}

class PersistentQueueSchedulerTest {
    private val repository = FakeRepository()
    private val settings = MapSettings()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @AfterTest
    fun tearDown() = scope.cancel()

    private suspend fun awaitTask(id: Long, predicate: (FakeTask) -> Boolean) =
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) {
                while (!predicate(repository.tasks.getValue(id))) delay(10)
            }
        }

    @Test
    fun interruptedTaskIsFailedInsteadOfResumed() = runTest {
        val interrupted = repository.insertTask(FakeTask(attempts = 1))
        val resumable = repository.insertTask(FakeTask(attempts = 1))
        settings.putString(IN_PROGRESS_KEY, "$interrupted")
        val scheduler = TestScheduler(repository, scope, settings)

        scheduler.resumePendingTasks()
        awaitTask(resumable) { it.status == TaskStatus.Success }
        awaitTask(interrupted) { it.status == TaskStatus.Failed }

        assertEquals(listOf(resumable), scheduler.processed)
        assertEquals(1, repository.tasks.getValue(interrupted).attempts)
        assertFalse(settings.hasKey(IN_PROGRESS_KEY))
    }

    @Test
    fun inProgressClearedAfterRecoverableFailure() = runTest {
        val id = repository.insertTask(FakeTask())
        val scheduler = TestScheduler(repository, scope, settings) { throw RecoverableTaskException("boom") }

        scheduler.schedule(id)
        awaitTask(id) { it.attempts == 1 && !settings.hasKey(IN_PROGRESS_KEY) }

        assertEquals(TaskStatus.Pending, repository.tasks.getValue(id).status)
    }

    @Test
    fun inProgressClearedAfterFatalFailure() = runTest {
        val id = repository.insertTask(FakeTask())
        val scheduler = TestScheduler(repository, scope, settings) { throw IllegalStateException("boom") }

        scheduler.schedule(id)
        awaitTask(id) { it.status == TaskStatus.Failed }

        assertFalse(settings.hasKey(IN_PROGRESS_KEY))
    }

    @Test
    fun inProgressRecordedWhileProcessing() = runTest {
        val id = repository.insertTask(FakeTask())
        val scheduler = TestScheduler(repository, scope, settings) { delay(1.minutes) }

        scheduler.schedule(id)
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) { while (settings.getStringOrNull(IN_PROGRESS_KEY) != "$id") delay(10) }
        }
    }
}
