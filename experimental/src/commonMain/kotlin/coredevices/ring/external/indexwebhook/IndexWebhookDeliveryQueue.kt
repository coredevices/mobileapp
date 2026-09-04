package coredevices.ring.external.indexwebhook

import coredevices.ring.service.button.RingGesture
import coredevices.util.queue.TaskStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

data class IndexWebhookDelivery(
    val id: Long = 0,
    val created: Instant = Clock.System.now(),
    val status: TaskStatus = TaskStatus.Pending,
    val attempts: Int = 0,
    val nextAttemptAt: Instant? = null,
    val deliveryId: String,
    val gesture: RingGesture,
    val url: String,
    val headers: Map<String, String>,
    val audioData: ByteArray?,
    val filename: String?,
    val transcription: String?,
    val recordedAt: Instant,
)

interface IndexWebhookDeliveryRepository {
    suspend fun insert(delivery: IndexWebhookDelivery): Long
    suspend fun getPendingIds(): List<Long>
    suspend fun getById(id: Long): IndexWebhookDelivery?
    suspend fun setStatus(id: Long, status: TaskStatus)
    suspend fun scheduleRetry(id: Long, nextAttemptAt: Instant)
    suspend fun resetForRetry(deliveryId: String): Long?
}

class IndexWebhookDeliveryQueue(
    private val repository: IndexWebhookDeliveryRepository,
    private val send: suspend (IndexWebhookDelivery) -> IndexWebhookRunResult,
    private val scope: CoroutineScope,
    private val now: () -> Instant = Clock.System::now,
    private val networkState: StateFlow<NetworkState> = MutableStateFlow(NetworkState(true)),
) {
    private val tasks = Channel<Long>(Channel.UNLIMITED)
    init {
        scope.launch {
            for (id in tasks) process(id)
        }
    }

    suspend fun enqueue(delivery: IndexWebhookDelivery) {
        tasks.send(repository.insert(delivery))
    }

    suspend fun retry(deliveryId: String): Boolean {
        val taskId = repository.resetForRetry(deliveryId) ?: return false
        tasks.send(taskId)
        return true
    }

    fun resumePendingDeliveries() {
        scope.launch {
            repository.getPendingIds().forEach { tasks.send(it) }
        }
    }

    private suspend fun process(id: Long) {
        val delivery = repository.getById(id) ?: return
        if (delivery.status != TaskStatus.Pending) return
        delivery.nextAttemptAt?.let {
            val remaining = it - now()
            if (remaining > Duration.ZERO) {
                schedule(id, remaining)
                return
            }
        }
        networkState.first { it.available }
        val outageCount = networkState.value.outageCount
        try {
            val result = send(delivery)
            when {
                result.ok -> repository.setStatus(id, TaskStatus.Success)
                result.transportFailure && networkState.value.outageCount != outageCount -> retryWhenNetworkReturns(id)
                result.retryable -> reschedule(delivery, result.retryAfter)
                else -> repository.setStatus(id, TaskStatus.Failed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            reschedule(delivery, null)
        }
    }

    private suspend fun retryWhenNetworkReturns(id: Long) {
        networkState.first { it.available }
        tasks.send(id)
    }

    private suspend fun reschedule(delivery: IndexWebhookDelivery, retryAfter: Duration?) {
        val after = webhookRetryDelay(delivery.attempts, retryAfter)
        repository.scheduleRetry(delivery.id, now() + after)
        schedule(delivery.id, after)
    }

    private fun schedule(id: Long, after: Duration) {
        scope.launch {
            delay(after)
            tasks.send(id)
        }
    }
}

internal fun webhookRetryDelay(
    attempts: Int,
    retryAfter: Duration?,
    jitter: Duration = Random.nextLong(30_001).milliseconds,
): Duration {
    val backoff = (1.minutes * (1 shl attempts.coerceIn(0, 6))).coerceAtMost(1.hours)
    return maxOf(backoff + jitter, retryAfter ?: Duration.ZERO)
}
