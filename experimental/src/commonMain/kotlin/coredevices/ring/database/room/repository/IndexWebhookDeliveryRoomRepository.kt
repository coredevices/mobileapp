package coredevices.ring.database.room.repository

import coredevices.ring.data.entity.room.IndexWebhookDeliveryEntity
import coredevices.ring.database.room.dao.IndexWebhookDeliveryDao
import coredevices.ring.external.indexwebhook.IndexWebhookDelivery
import coredevices.ring.external.indexwebhook.IndexWebhookDeliveryRepository
import coredevices.ring.external.indexwebhook.InvalidWebhookDeliveryException
import coredevices.ring.external.indexwebhook.IndexWebhookRunRepository
import coredevices.ring.service.button.RingGesture
import coredevices.util.queue.TaskStatus
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant

class IndexWebhookDeliveryRoomRepository(
    private val dao: IndexWebhookDeliveryDao,
) : IndexWebhookDeliveryRepository {
    private val headerSerializer = MapSerializer(String.serializer(), String.serializer())

    override suspend fun insert(delivery: IndexWebhookDelivery): Long {
        val inserted = dao.insert(delivery.toEntity())
        if (inserted != -1L) return inserted
        return requireNotNull(dao.getIdByDeliveryId(delivery.deliveryId))
    }

    override suspend fun getPendingIds(): List<Long> = dao.getPendingIds()

    override suspend fun getById(id: Long): IndexWebhookDelivery? {
        val entity = dao.getById(id) ?: return null
        return try {
            entity.toDomain()
        } catch (e: Exception) {
            throw InvalidWebhookDeliveryException(e)
        }
    }

    override suspend fun setPayload(id: Long, audioData: ByteArray?, recordedAt: Instant) {
        dao.setPayload(id, audioData, recordedAt)
    }

    override suspend fun setStatus(id: Long, status: TaskStatus) {
        when (status) {
            TaskStatus.Success -> dao.markSuccessAndClearPayload(id)
            TaskStatus.Failed -> {
                val gesture = dao.getGesture(id) ?: return
                dao.markFailed(id, Clock.System.now())
                dao.pruneFailed(gesture, IndexWebhookRunRepository.MAX_RUNS_PER_GESTURE)
            }
            else -> dao.setStatus(id, status)
        }
    }

    override suspend fun scheduleRetry(id: Long, nextAttemptAt: Instant) {
        dao.scheduleRetry(id, nextAttemptAt)
    }

    override suspend fun resetForRetry(deliveryId: String): Long? {
        val id = dao.getIdByDeliveryId(deliveryId) ?: return null
        if (dao.resetForRetry(deliveryId) == 0) return null
        return id
    }

    private fun IndexWebhookDelivery.toEntity() = IndexWebhookDeliveryEntity(
        id = id,
        created = created,
        recordedAt = recordedAt,
        status = status,
        attempts = attempts,
        nextAttemptAt = nextAttemptAt,
        deliveryId = deliveryId,
        gesture = gesture.name,
        url = url,
        headersJson = Json.encodeToString(headerSerializer, headers),
        fileId = fileId,
        audioData = audioData,
        transcription = transcription,
        recordingId = recordingId,
    )

    private fun IndexWebhookDeliveryEntity.toDomain() = IndexWebhookDelivery(
        id = id,
        created = created,
        recordedAt = recordedAt,
        status = status,
        attempts = attempts,
        nextAttemptAt = nextAttemptAt,
        deliveryId = deliveryId,
        gesture = RingGesture.valueOf(gesture),
        url = url,
        headers = Json.decodeFromString(headerSerializer, headersJson),
        fileId = fileId,
        audioData = audioData,
        transcription = transcription,
        recordingId = recordingId,
    )
}
