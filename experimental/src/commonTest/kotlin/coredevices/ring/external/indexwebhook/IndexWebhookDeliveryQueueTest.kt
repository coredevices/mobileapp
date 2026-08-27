package coredevices.ring.external.indexwebhook

import coredevices.ring.service.button.RingGesture
import coredevices.util.queue.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class IndexWebhookDeliveryQueueTest {

    @Test
    fun networkStateCountsOnlyOutageStarts() {
        val offline = NetworkState(true).withAvailability(false)
        val stillOffline = offline.withAvailability(false)
        val restored = stillOffline.withAvailability(true)

        assertEquals(1, offline.outageCount)
        assertEquals(1, stillOffline.outageCount)
        assertEquals(1, restored.outageCount)
    }

    @Test
    fun waitsForNetworkBeforeSending() = runTest {
        val repository = InMemoryDeliveryRepository()
        val sender = FakeSender(successResult())
        val networkState = MutableStateFlow(NetworkState(false))
        val queueScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val queue = IndexWebhookDeliveryQueue(
            repository,
            sender::send,
            queueScope,
            networkState = networkState,
        )

        queue.enqueue(delivery())
        runCurrent()
        assertEquals(0, sender.sentCount)

        networkState.value = NetworkState(true)
        runCurrent()
        assertEquals(1, sender.sentCount)
        assertEquals(TaskStatus.Success, repository.single().status)
        queueScope.cancel()
    }

    @Test
    fun briefNetworkLossDoesNotConsumeABackoffAttempt() = runTest {
        val repository = InMemoryDeliveryRepository()
        val networkState = MutableStateFlow(NetworkState(true))
        var sentCount = 0
        val queueScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val queue = IndexWebhookDeliveryQueue(
            repository,
            send = {
                sentCount += 1
                if (sentCount == 1) {
                    networkState.value = NetworkState(false, outageCount = 1)
                    networkState.value = NetworkState(true, outageCount = 1)
                    IndexWebhookRunResult(
                        false,
                        "FAILED",
                        "offline",
                        12,
                        1,
                        retryable = true,
                        transportFailure = true,
                    )
                } else {
                    successResult()
                }
            },
            scope = queueScope,
            networkState = networkState,
        )

        queue.enqueue(delivery())
        runCurrent()
        assertEquals(2, sentCount)
        assertEquals(0, repository.single().attempts)
        assertEquals(TaskStatus.Success, repository.single().status)
        queueScope.cancel()
    }

    @Test
    fun networkLossDoesNotTurnAPermanentHttpFailureIntoARetry() = runTest {
        val repository = InMemoryDeliveryRepository()
        val networkState = MutableStateFlow(NetworkState(true))
        val queueScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val queue = IndexWebhookDeliveryQueue(
            repository,
            send = {
                networkState.value = NetworkState(false, outageCount = 1)
                IndexWebhookRunResult(false, "401 ERROR", "unauthorized", 12, 1)
            },
            scope = queueScope,
            networkState = networkState,
        )

        queue.enqueue(delivery())
        runCurrent()

        assertEquals(TaskStatus.Failed, repository.single().status)
        queueScope.cancel()
    }

    @Test
    fun exceptionAfterSendingStillUsesBackoff() = runTest {
        val repository = InMemoryDeliveryRepository()
        val networkState = MutableStateFlow(NetworkState(true))
        val queueScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val queue = IndexWebhookDeliveryQueue(
            repository,
            send = {
                networkState.value = NetworkState(false, outageCount = 1)
                error("recording the result failed")
            },
            scope = queueScope,
            networkState = networkState,
        )

        queue.enqueue(delivery())
        runCurrent()

        assertEquals(1, repository.single().attempts)
        queueScope.cancel()
    }

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
            now = { Instant.fromEpochMilliseconds(testScheduler.currentTime) },
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
        val queue = IndexWebhookDeliveryQueue(repository, sender::send, queueScope)

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
        val firstQueue = IndexWebhookDeliveryQueue(
            repository,
            firstSender::send,
            firstScope,
            now = { Instant.fromEpochMilliseconds(testScheduler.currentTime) },
        )
        firstQueue.enqueue(delivery())
        runCurrent()
        assertEquals(1, firstSender.sentCount)
        firstScope.cancel()

        val secondSender = FakeSender(successResult())
        val secondScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val secondQueue = IndexWebhookDeliveryQueue(
            repository,
            secondSender::send,
            secondScope,
            now = { Instant.fromEpochMilliseconds(testScheduler.currentTime) },
        )
        secondQueue.resumePendingDeliveries()
        advanceUntilIdle()

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

    @Test
    fun retryAfterOverridesBackoff() = runTest {
        val repository = InMemoryDeliveryRepository()
        val sender = FakeSender(
            IndexWebhookRunResult(
                ok = false,
                status = "429 ERROR",
                detail = "slow down",
                byteSize = 12,
                durationMs = 1,
                retryable = true,
                retryAfter = 1.hours,
            ),
            successResult(),
        )
        val queueScope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val queue = IndexWebhookDeliveryQueue(
            repository,
            sender::send,
            queueScope,
            now = { Instant.fromEpochMilliseconds(testScheduler.currentTime) },
        )

        queue.enqueue(delivery())
        runCurrent()
        advanceTimeBy(59.minutes)
        runCurrent()
        assertEquals(1, sender.sentCount)

        advanceTimeBy(1.minutes)
        runCurrent()
        assertEquals(2, sender.sentCount)
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

    override suspend fun scheduleRetry(id: Long, nextAttemptAt: Instant) {
        deliveries.computeIfPresent(id) { _, task ->
            task.copy(attempts = task.attempts + 1, nextAttemptAt = nextAttemptAt)
        }
    }

    override suspend fun getById(id: Long): IndexWebhookDelivery? = deliveries[id]

    override suspend fun resetForRetry(deliveryId: String): Long? {
        val task = deliveries.values.firstOrNull { it.deliveryId == deliveryId } ?: return null
        if (task.status != TaskStatus.Failed) return null
        deliveries[task.id] = task.copy(
            status = TaskStatus.Pending,
            attempts = 0,
            nextAttemptAt = null,
        )
        return task.id
    }
}
