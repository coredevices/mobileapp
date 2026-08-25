package coredevices.ring.external.indexwebhook

import coredevices.ring.service.button.RingGesture
import coredevices.util.queue.QueueTask
import coredevices.util.queue.TaskStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

data class IndexWebhookDelivery(
    override val id: Long = 0,
    override val created: Instant = Clock.System.now(),
    override val lastAttempt: Instant? = null,
    override val attempts: Int = 0,
    override val status: TaskStatus = TaskStatus.Pending,
    val deliveryId: String,
    val gesture: RingGesture,
    val url: String,
    val headers: Map<String, String>,
    val audioData: ByteArray?,
    val filename: String?,
    val transcription: String?,
    val recordedAt: Instant,
) : QueueTask

fun interface IndexWebhookSender {
    suspend fun send(delivery: IndexWebhookDelivery): IndexWebhookRunResult
}

interface IndexWebhookDeliveryRepository {
    suspend fun insert(delivery: IndexWebhookDelivery): Long
    suspend fun getPending(): List<IndexWebhookDelivery>
    suspend fun getById(id: Long): IndexWebhookDelivery?
    suspend fun markAttempt(id: Long)
    suspend fun setStatus(id: Long, status: TaskStatus)
    suspend fun resetForRetry(deliveryId: String): Long?
}

class IndexWebhookDeliveryQueue(
    private val repository: IndexWebhookDeliveryRepository,
    private val sender: IndexWebhookSender,
    private val scope: CoroutineScope,
    private val rescheduleDelay: Duration = 1.minutes,
) : AutoCloseable {
    private val tasks = Channel<Long>(Channel.UNLIMITED)
    private val worker = scope.launch(Dispatchers.IO) {
        for (id in tasks) process(id)
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
            repository.getPending().forEach { tasks.send(it.id) }
        }
    }

    private suspend fun process(id: Long) {
        val delivery = repository.getById(id) ?: return
        if (delivery.status != TaskStatus.Pending) return
        repository.markAttempt(id)
        try {
            val result = sender.send(delivery)
            when {
                result.ok -> repository.setStatus(id, TaskStatus.Success)
                result.retryable -> scope.launch {
                    delay(rescheduleDelay)
                    tasks.send(id)
                }
                else -> repository.setStatus(id, TaskStatus.Failed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            scope.launch {
                delay(rescheduleDelay)
                tasks.send(id)
            }
        }
    }

    override fun close() {
        worker.cancel()
        tasks.close()
    }
}
