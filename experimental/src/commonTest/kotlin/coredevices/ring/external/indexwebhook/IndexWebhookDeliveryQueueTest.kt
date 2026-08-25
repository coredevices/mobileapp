package coredevices.ring.external.indexwebhook

import coredevices.ring.service.button.RingGesture
import coredevices.util.queue.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
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
        val queueScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val queue = IndexWebhookDeliveryQueue(
            repository = repository,
            send = sender::send,
            scope = queueScope,
            rescheduleDelay = 10.milliseconds,
        )

        queue.enqueue(delivery())
        advanceUntilIdle()

        assertEquals(2, sender.sentCount)
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
        val queueScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val queue = IndexWebhookDeliveryQueue(repository, sender::send, queueScope, 10.milliseconds)

        queue.enqueue(delivery())
        runCurrent()
        assertEquals(TaskStatus.Failed, repository.single().status)

        assertTrue(queue.retry("recording-1"))
        assertFalse(queue.retry("recording-1"))
        runCurrent()

        assertEquals(TaskStatus.Success, repository.single().status)
        assertEquals(listOf("recording-1", "recording-1"), sender.sentDeliveryIds)
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
        val firstScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val firstQueue = IndexWebhookDeliveryQueue(repository, firstSender::send, firstScope, 1.hours)
        firstQueue.enqueue(delivery())
        runCurrent()
        assertEquals(1, firstSender.sentCount)
        firstScope.cancel()

        val secondSender = FakeSender(successResult())
        val secondScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val secondQueue = IndexWebhookDeliveryQueue(repository, secondSender::send, secondScope)
        secondQueue.resumePendingDeliveries()
        runCurrent()

        assertEquals(TaskStatus.Success, repository.single().status)
        assertEquals(1, secondSender.sentCount)
        secondScope.cancel()
    }

    @Test
    fun duplicateEnqueueSendsOneSuccessfulDelivery() = runTest {
        val repository = InMemoryDeliveryRepository()
        val sender = FakeSender(successResult())
        val queueScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val queue = IndexWebhookDeliveryQueue(repository, sender::send, queueScope)

        queue.enqueue(delivery())
        runCurrent()
        queue.enqueue(delivery())
        runCurrent()

        assertEquals(1, sender.sentCount)
        queueScope.cancel()
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

private class FakeSender(vararg results: IndexWebhookRunResult) {
    private val results = ArrayDeque(results.toList())
    var sentCount = 0
    val sentDeliveryIds = mutableListOf<String>()

    suspend fun send(delivery: IndexWebhookDelivery): IndexWebhookRunResult {
        sentCount += 1
        sentDeliveryIds += delivery.deliveryId
        return results.removeFirst()
    }
}

private class InMemoryDeliveryRepository : IndexWebhookDeliveryRepository {
    private val deliveries = mutableMapOf<Long, IndexWebhookDelivery>()
    private var nextId = 1L

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
    }

    override suspend fun getById(id: Long): IndexWebhookDelivery? = deliveries[id]

    override suspend fun resetForRetry(deliveryId: String): Long? {
        val task = deliveries.values.firstOrNull { it.deliveryId == deliveryId } ?: return null
        if (task.status != TaskStatus.Failed) return null
        deliveries[task.id] = task.copy(
            status = TaskStatus.Pending,
        )
        return task.id
    }
}
