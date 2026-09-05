package coredevices.ring.external.indexwebhook

import co.touchlab.kermit.Logger
import coredevices.ring.service.button.RingGesture
import coredevices.util.queue.TaskStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
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
    val recordedAt: Instant? = null,
    val status: TaskStatus = TaskStatus.Pending,
    val attempts: Int = 0,
    val nextAttemptAt: Instant? = null,
    val deliveryId: String,
    val gesture: RingGesture,
    val url: String,
    val headers: Map<String, String>,
    val fileId: String?,
    val audioData: ByteArray? = null,
    val transcription: String?,
    val recordingId: Long,
)

internal const val MAX_WEBHOOK_DELIVERY_ATTEMPTS = 10

interface IndexWebhookDeliveryRepository {
    suspend fun insert(delivery: IndexWebhookDelivery): Long
    suspend fun getPendingIds(): List<Long>
    suspend fun getById(id: Long): IndexWebhookDelivery?
    suspend fun setPayload(id: Long, audioData: ByteArray?, recordedAt: Instant)
    suspend fun setStatus(id: Long, status: TaskStatus)
    suspend fun scheduleRetry(id: Long, nextAttemptAt: Instant)
    suspend fun resetForRetry(deliveryId: String): Long?
}

internal class InvalidWebhookDeliveryException(cause: Exception) : Exception(cause)

class IndexWebhookDeliveryQueue(
    private val repository: IndexWebhookDeliveryRepository,
    private val send: suspend (IndexWebhookDelivery) -> IndexWebhookRunResult,
    private val scope: CoroutineScope,
    private val persistPayload: suspend (IndexWebhookDelivery) -> Unit,
    private val now: () -> Instant = Clock.System::now,
) {
    companion object {
        private val logger = Logger.withTag("IndexWebhookDeliveryQueue")
    }

    private val tasks = Channel<Long>(Channel.UNLIMITED)
    private val workerFailures = mutableMapOf<Long, Int>()
    private val worker = scope.launch(start = CoroutineStart.LAZY) {
        for (id in tasks) {
            try {
                process(id)
                workerFailures.remove(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failures = workerFailures[id] ?: 0
                workerFailures[id] = failures + 1
                logger.e(e) { "Webhook delivery $id failed; retrying" }
                schedule(id, webhookRetryDelay(failures, null))
            }
        }
    }

    private fun startWorker() {
        worker.start()
    }

    suspend fun enqueue(delivery: IndexWebhookDelivery) {
        startWorker()
        val id = repository.insert(delivery)
        try {
            val stored = repository.getById(id) ?: return
            if (stored.status != TaskStatus.Pending) return
            persistPayload(stored)
        } finally {
            tasks.send(id)
        }
    }

    suspend fun retry(deliveryId: String): Boolean {
        startWorker()
        val taskId = repository.resetForRetry(deliveryId) ?: return false
        tasks.send(taskId)
        return true
    }

    fun resumePendingDeliveries() {
        startWorker()
        scope.launch {
            var failures = 0
            while (true) {
                try {
                    repository.getPendingIds().forEach { tasks.send(it) }
                    return@launch
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.e(e) { "Failed to resume webhook deliveries; retrying" }
                    delay(webhookRetryDelay(failures++, null))
                }
            }
        }
    }

    private suspend fun process(id: Long) {
        val delivery = try {
            repository.getById(id)
        } catch (e: InvalidWebhookDeliveryException) {
            logger.e(e) { "Webhook delivery $id is invalid" }
            repository.setStatus(id, TaskStatus.Failed)
            return
        } ?: return
        if (delivery.status != TaskStatus.Pending) return
        delivery.nextAttemptAt?.let {
            val remaining = it - now()
            if (remaining > Duration.ZERO) {
                schedule(id, remaining)
                return
            }
        }
        val result = send(delivery)
        when {
            result.ok -> repository.setStatus(id, TaskStatus.Success)
            result.retryable -> retryOrFail(delivery, result.retryAfter)
            else -> repository.setStatus(id, TaskStatus.Failed)
        }
    }

    private suspend fun reschedule(delivery: IndexWebhookDelivery, retryAfter: Duration?) {
        val after = webhookRetryDelay(delivery.attempts, retryAfter)
        repository.scheduleRetry(delivery.id, now() + after)
        schedule(delivery.id, after)
    }

    private suspend fun retryOrFail(delivery: IndexWebhookDelivery, retryAfter: Duration?) {
        if (delivery.attempts + 1 >= MAX_WEBHOOK_DELIVERY_ATTEMPTS) {
            repository.setStatus(delivery.id, TaskStatus.Failed)
        } else {
            reschedule(delivery, retryAfter)
        }
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
    return maxOf(backoff + jitter, retryAfter?.coerceAtMost(1.hours) ?: Duration.ZERO)
}
