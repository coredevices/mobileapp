package coredevices.util.sync

import coredevices.database.SyncTaskEntity
import coredevices.util.queue.RecoverableTaskException
import coredevices.util.queue.TaskStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

private class FakeStore : SyncTaskStore {
    val tasks = mutableMapOf<Long, SyncTaskEntity>()
    private var nextId = 1L

    override suspend fun insert(task: SyncTaskEntity): Long {
        val id = nextId++
        tasks[id] = task.copy(id = id)
        return id
    }

    override suspend fun getPending() =
        tasks.values.filter { it.status == TaskStatus.Pending }.sortedBy { it.id }

    override suspend fun hasPending(type: String, dedupeKey: String) =
        getPending().any { it.type == type && it.dedupeKey == dedupeKey }

    override suspend fun markAttempt(id: Long, at: Long) {
        tasks[id] = tasks.getValue(id).let { it.copy(attempts = it.attempts + 1, lastAttemptAt = at) }
    }

    override suspend fun setStatus(id: Long, status: TaskStatus, error: String?) {
        tasks[id] = tasks.getValue(id).copy(status = status, lastError = error)
    }

    override suspend fun delete(id: Long) {
        tasks.remove(id)
    }

    override suspend fun deleteFinishedBefore(before: Long) {
        tasks.values.filter { it.status != TaskStatus.Pending && (it.lastAttemptAt ?: 0) < before }
            .forEach { tasks.remove(it.id) }
    }
}

private class FakeScheduler : SyncScheduler {
    var scheduled = 0
    override fun schedule() {
        scheduled++
    }
}

private class FakeHandler(
    override val type: String,
    private val onSync: suspend (String) -> Unit = {},
) : SyncHandler {
    val synced = mutableListOf<String>()
    val failed = mutableListOf<String>()

    override suspend fun sync(payload: String) {
        synced += payload
        onSync(payload)
    }

    override suspend fun onFailed(payload: String) {
        failed += payload
    }
}

class SyncManagerTest {
    private val store = FakeStore()
    private val scheduler = FakeScheduler()
    private val fixedNow = Instant.fromEpochMilliseconds(1_000_000_000_000)
    private val clock = object : Clock {
        override fun now() = fixedNow
    }

    private fun manager(vararg handlers: SyncHandler, maxAttempts: Int = 3) =
        SyncManager(store, { handlers.toList() }, scheduler, maxAttempts, clock)

    @Test
    fun enqueuePersistsAndSchedules() = runTest {
        val handler = FakeHandler("upload")
        val id = assertNotNull(manager(handler).enqueue("upload", "payload"))

        assertEquals("payload", store.tasks.getValue(id).payload)
        assertEquals(TaskStatus.Pending, store.tasks.getValue(id).status)
        assertEquals(1, scheduler.scheduled)
    }

    @Test
    fun enqueueRejectsUnknownType() = runTest {
        assertFailsWith<IllegalArgumentException> {
            manager(FakeHandler("upload")).enqueue("other", "payload")
        }
        assertEquals(0, scheduler.scheduled)
    }

    @Test
    fun successfulTaskIsRemoved() = runTest {
        val handler = FakeHandler("upload")
        val manager = manager(handler)
        manager.enqueue("upload", "a")

        assertFalse(manager.runPending())
        assertEquals(listOf("a"), handler.synced)
        assertTrue(store.tasks.isEmpty())
    }

    @Test
    fun recoverableFailureStaysPendingAndRequestsRetry() = runTest {
        val handler = FakeHandler("upload") { throw RecoverableTaskException("offline") }
        val manager = manager(handler)
        val id = assertNotNull(manager.enqueue("upload", "a"))

        assertTrue(manager.runPending())
        val task = store.tasks.getValue(id)
        assertEquals(TaskStatus.Pending, task.status)
        assertEquals(1, task.attempts)
        assertEquals("offline", task.lastError)
        assertTrue(handler.failed.isEmpty())
    }

    @Test
    fun permanentFailureMarksFailedAndNotifiesHandler() = runTest {
        val handler = FakeHandler("upload") { throw IllegalStateException("corrupt") }
        val manager = manager(handler)
        val id = assertNotNull(manager.enqueue("upload", "a"))

        assertFalse(manager.runPending())
        assertEquals(TaskStatus.Failed, store.tasks.getValue(id).status)
        assertEquals("corrupt", store.tasks.getValue(id).lastError)
        assertEquals(listOf("a"), handler.failed)
    }

    @Test
    fun givesUpAfterMaxAttempts() = runTest {
        val handler = FakeHandler("upload") { throw RecoverableTaskException("offline") }
        val manager = manager(handler, maxAttempts = 2)
        val id = assertNotNull(manager.enqueue("upload", "a"))

        assertTrue(manager.runPending())
        assertFalse(manager.runPending())
        assertEquals(TaskStatus.Failed, store.tasks.getValue(id).status)
        assertEquals(2, store.tasks.getValue(id).attempts)
        assertEquals(listOf("a"), handler.failed)
    }

    @Test
    fun oneFailingTaskDoesNotBlockOthers() = runTest {
        val flaky = FakeHandler("flaky") { throw RecoverableTaskException("offline") }
        val fine = FakeHandler("fine")
        val manager = manager(flaky, fine)
        manager.enqueue("flaky", "a")
        manager.enqueue("fine", "b")

        assertTrue(manager.runPending())
        assertEquals(listOf("b"), fine.synced)
        assertEquals(1, store.tasks.size)
    }

    @Test
    fun taskWithoutHandlerFailsPermanently() = runTest {
        val id = store.insert(SyncTaskEntity(type = "legacy", payload = "x", createdAt = 0))

        assertFalse(manager(FakeHandler("upload")).runPending())
        assertEquals(TaskStatus.Failed, store.tasks.getValue(id).status)
    }

    @Test
    fun tasksEnqueuedDuringRunArePickedUp() = runTest {
        lateinit var syncManager: SyncManager
        var enqueuedInner = false
        val handler = FakeHandler("upload") {
            if (!enqueuedInner) {
                enqueuedInner = true
                syncManager.enqueue("upload", "inner")
            }
        }
        syncManager = manager(handler)
        syncManager.enqueue("upload", "outer")

        assertFalse(syncManager.runPending())
        assertEquals(listOf("outer", "inner"), handler.synced)
        assertTrue(store.tasks.isEmpty())
    }

    @Test
    fun dedupeKeySkipsWhileSameTaskIsPending() = runTest {
        val handler = FakeHandler("upload")
        val manager = manager(handler)

        assertNotNull(manager.enqueue("upload", "a", dedupeKey = "rec-1"))
        assertNull(manager.enqueue("upload", "a-again", dedupeKey = "rec-1"))
        assertNotNull(manager.enqueue("upload", "b", dedupeKey = "rec-2"))
        assertTrue(manager.hasPending("upload", "rec-1"))
        assertEquals(2, store.tasks.size)
        assertEquals(2, scheduler.scheduled)

        manager.runPending()
        assertEquals(listOf("a", "b"), handler.synced)
        assertFalse(manager.hasPending("upload", "rec-1"))
        assertNotNull(manager.enqueue("upload", "a-later", dedupeKey = "rec-1"))
    }

    @Test
    fun resumeSchedulesOnlyWhenWorkIsPending() = runTest {
        val manager = manager(FakeHandler("upload"))
        manager.resume()
        assertEquals(0, scheduler.scheduled)

        store.insert(SyncTaskEntity(type = "upload", payload = "x", createdAt = 0))
        manager.resume()
        assertEquals(1, scheduler.scheduled)
    }

    @Test
    fun oldFailedTasksArePruned() = runTest {
        val stale = store.insert(
            SyncTaskEntity(
                type = "upload", payload = "x", createdAt = 0,
                status = TaskStatus.Failed, lastAttemptAt = (fixedNow - 8.days).toEpochMilliseconds(),
            )
        )
        val recent = store.insert(
            SyncTaskEntity(
                type = "upload", payload = "y", createdAt = 0,
                status = TaskStatus.Failed, lastAttemptAt = (fixedNow - 1.days).toEpochMilliseconds(),
            )
        )

        manager(FakeHandler("upload")).runPending()
        assertNull(store.tasks[stale])
        assertEquals(TaskStatus.Failed, store.tasks.getValue(recent).status)
    }
}
