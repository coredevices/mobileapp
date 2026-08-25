package coredevices.ring.external.indexwebhook

import coredevices.ring.service.button.RingGesture
import coredevices.util.queue.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class IndexWebhookDeliveryQueueTest {

    @Test
    fun transientFailureRemainsPendingAndRetries() = runTest {
        val repository = InMemoryDeliveryRepository()
        val sender = FakeSender(
            IndexWebhookRunResult(
                ok = false,
                status = "FAILED",
                detail = "offline",
                byteSize = 12,
                durationMs = 1,
                retryable = true,
            ),
            successResult(),
        )
        val queueScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val queue = IndexWebhookDeliveryQueue(
            repository = repository,
            sender = sender,
            scope = queueScope,
            rescheduleDelay = 10.milliseconds,
        )

        runCurrent()
        queue.enqueue(delivery())
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) {
                sender.sentCount.first { it == 2 }
                repository.status.first { it == TaskStatus.Success }
            }
        }

        assertEquals(2, sender.sentCount.value)
        assertEquals(TaskStatus.Success, repository.single().status)
        queueScope.cancel()
    }

    @Test
    fun manualRetryReusesAPermanentlyFailedDelivery() = runTest {
        val repository = InMemoryDeliveryRepository()
        val sender = FakeSender(
            IndexWebhookRunResult(
                ok = false,
                status = "401 ERROR",
                detail = "unauthorized",
                byteSize = 12,
                durationMs = 1,
                retryable = false,
            ),
            successResult(),
        )
        val queueScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val queue = IndexWebhookDeliveryQueue(repository, sender, queueScope, 10.milliseconds)

        queue.enqueue(delivery())
        awaitStatus(repository, TaskStatus.Failed)

        queue.retry("recording-1")
        awaitStatus(repository, TaskStatus.Success)

        assertEquals(listOf("recording-1", "recording-1"), sender.sentDeliveryIds.value)
        queueScope.cancel()
    }

    @Test
    fun pendingDeliveryResumesAfterQueueRestart() = runTest {
        val repository = InMemoryDeliveryRepository()
        val firstSender = FakeSender(
            IndexWebhookRunResult(
                ok = false,
                status = "FAILED",
                detail = "offline",
                byteSize = 12,
                durationMs = 1,
                retryable = true,
            ),
        )
        val firstScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val firstQueue = IndexWebhookDeliveryQueue(repository, firstSender, firstScope, 1.hours)
        firstQueue.enqueue(delivery())
        withContext(Dispatchers.Default) {
            withTimeout(5.seconds) { firstSender.sentCount.first { it == 1 } }
        }
        firstScope.cancel()

        val secondSender = FakeSender(successResult())
        val secondScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val secondQueue = IndexWebhookDeliveryQueue(repository, secondSender, secondScope)
        secondQueue.resumePendingDeliveries()
        awaitStatus(repository, TaskStatus.Success)

        assertEquals(1, secondSender.sentCount.value)
        secondScope.cancel()
    }

    @Test
    fun duplicateEnqueueSendsOneSuccessfulDelivery() = runTest {
        val repository = InMemoryDeliveryRepository()
        val sender = FakeSender(successResult())
        val queueScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val queue = IndexWebhookDeliveryQueue(repository, sender, queueScope)

        queue.enqueue(delivery())
        awaitStatus(repository, TaskStatus.Success)
        queue.enqueue(delivery())

        delay(100.milliseconds)
        assertEquals(1, sender.sentCount.value)
        queueScope.cancel()
    }

    private suspend fun awaitStatus(
        repository: InMemoryDeliveryRepository,
        status: TaskStatus,
    ) = withContext(Dispatchers.Default) {
        withTimeout(5.seconds) { repository.status.first { it == status } }
    }

    private fun delivery() = IndexWebhookDelivery(
        deliveryId = "recording-1",
        gesture = RingGesture.Hold,
        url = "https://example.com/hook",
        headers = mapOf("Authorization" to "Bearer test"),
        audioData = byteArrayOf(1, 2, 3),
        filename = "recording-1.m4a",
        transcription = "hello",
        recordedAt = Instant.fromEpochMilliseconds(1_000),
    )

    private fun successResult() = IndexWebhookRunResult(
        ok = true,
        status = "200 OK",
        detail = "recording + transcription",
        byteSize = 12,
        durationMs = 1,
        retryable = false,
    )
}

private class FakeSender(vararg results: IndexWebhookRunResult) : IndexWebhookSender {
    private val results = ArrayDeque(results.toList())
    val sentCount = MutableStateFlow(0)
    val sentDeliveryIds = MutableStateFlow<List<String>>(emptyList())

    override suspend fun send(delivery: IndexWebhookDelivery): IndexWebhookRunResult {
        sentCount.value += 1
        sentDeliveryIds.value += delivery.deliveryId
        return results.removeFirst()
    }
}

private class InMemoryDeliveryRepository : IndexWebhookDeliveryRepository {
    private val deliveries = mutableMapOf<Long, IndexWebhookDelivery>()
    private var nextId = 1L
    val status = MutableStateFlow(TaskStatus.Pending)

    fun single(): IndexWebhookDelivery = deliveries.values.single()

    override suspend fun insert(delivery: IndexWebhookDelivery): Long {
        val task = delivery
        val existing = deliveries.values.firstOrNull { it.deliveryId == task.deliveryId }
        if (existing != null) return existing.id
        val id = nextId++
        deliveries[id] = task.copy(id = id)
        return id
    }

    override suspend fun getPending() = deliveries.values.filter { it.status == TaskStatus.Pending }

    override suspend fun setStatus(id: Long, status: TaskStatus) {
        deliveries.computeIfPresent(id) { _, task -> task.copy(status = status) }
        this.status.value = status
    }

    override suspend fun getById(id: Long): IndexWebhookDelivery? = deliveries[id]

    override suspend fun markAttempt(id: Long) {
        deliveries.computeIfPresent(id) { _, task ->
            task.copy(attempts = task.attempts + 1, lastAttempt = kotlin.time.Clock.System.now())
        }
    }

    override suspend fun resetForRetry(deliveryId: String): Long? {
        val task = deliveries.values.firstOrNull { it.deliveryId == deliveryId } ?: return null
        deliveries[task.id] = task.copy(
            attempts = 0,
            lastAttempt = null,
            status = TaskStatus.Pending,
        )
        status.value = TaskStatus.Pending
        return task.id
    }
}
