package coredevices.ring.external.indexwebhook

import com.russhwolf.settings.Settings
import coredevices.indexai.database.dao.LocalRecordingDao
import coredevices.ring.data.entity.room.IndexWebhookDeliveryEntity
import coredevices.ring.database.room.repository.IndexWebhookDeliveryRepository
import coredevices.ring.service.RecordingBackgroundScope
import coredevices.ring.storage.RecordingStorage
import coredevices.util.queue.PersistentQueueScheduler
import coredevices.util.queue.RecoverableTaskException
import kotlinx.io.buffered
import kotlinx.io.readShortLe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Sends recordings to their gesture's webhook, retrying network errors and 408/429/5xx
 * responses (including after an app restart) so a brief outage doesn't lose a recording.
 */
class IndexWebhookDeliveryQueue(
    private val repository: IndexWebhookDeliveryRepository,
    private val webhookApi: IndexWebhookApi,
    private val runRepository: IndexWebhookRunRepository,
    private val recordingStorage: RecordingStorage,
    private val localRecordingDao: LocalRecordingDao,
    scope: RecordingBackgroundScope,
    settings: Settings,
    rescheduleDelay: Duration = 1.minutes,
) : PersistentQueueScheduler<IndexWebhookDeliveryEntity>(
    repository = repository,
    scope = scope,
    settings = settings,
    label = "IndexWebhookDelivery",
    rescheduleDelay = rescheduleDelay,
    maxAttempts = MAX_ATTEMPTS,
) {
    companion object {
        const val MAX_ATTEMPTS = 10
    }

    /** Does nothing if a delivery with the same deliveryId was already queued. */
    suspend fun enqueue(delivery: IndexWebhookDeliveryEntity) {
        val id = repository.insertTask(delivery)
        if (id != -1L) scheduleTask(id)
    }

    /** Gives a delivery that failed for good another round of attempts. */
    suspend fun retry(deliveryId: String) {
        repository.resetFailed(deliveryId)?.let { scheduleTask(it) }
    }

    override suspend fun processTask(task: IndexWebhookDeliveryEntity) {
        val audio = task.fileId?.let { readAudio(it) }
        val recordedAt = localRecordingDao.getRecording(task.recordingId)?.localTimestamp ?: task.created
        val result = webhookApi.upload(
            samples = audio?.first,
            sampleRate = audio?.second ?: 16000,
            recordingId = task.deliveryId,
            transcription = task.transcription,
            recordedAt = recordedAt,
            gesture = task.gesture,
        ) ?: return
        // The scheduler counts this attempt before running it.
        val failedForGood = !result.ok && (!result.retryable || task.attempts + 1 >= MAX_ATTEMPTS)
        runRepository.record(
            gesture = task.gesture,
            ok = result.ok,
            status = result.status,
            detail = result.detail,
            byteSize = result.byteSize,
            durationMs = result.durationMs,
            retryDeliveryId = task.deliveryId.takeIf { failedForGood },
        )
        when {
            result.ok -> Unit
            result.retryable -> throw RecoverableTaskException("Webhook delivery failed: ${result.status}")
            else -> error("Webhook delivery failed: ${result.status} ${result.detail}")
        }
    }

    private suspend fun readAudio(fileId: String): Pair<ShortArray, Int> {
        val (source, meta) = recordingStorage.openRecordingSource(fileId)
        val samples = ShortArray((meta.size / 2).toInt())
        source.buffered().use {
            for (i in samples.indices) {
                samples[i] = it.readShortLe()
            }
        }
        return samples to meta.cachedMetadata.sampleRate
    }
}
