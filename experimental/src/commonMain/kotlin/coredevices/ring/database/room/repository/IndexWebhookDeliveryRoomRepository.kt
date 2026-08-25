package coredevices.ring.database.room.repository

import coredevices.ring.data.entity.room.IndexWebhookDeliveryEntity
import coredevices.ring.database.room.dao.IndexWebhookDeliveryDao
import coredevices.ring.external.indexwebhook.IndexWebhookDelivery
import coredevices.ring.external.indexwebhook.IndexWebhookDeliveryRepository
import coredevices.ring.service.button.RingGesture
import coredevices.util.queue.TaskStatus
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class IndexWebhookDeliveryRoomRepository(
    private val dao: IndexWebhookDeliveryDao,
) : IndexWebhookDeliveryRepository {
    private val headerSerializer = MapSerializer(String.serializer(), String.serializer())

    override suspend fun insert(delivery: IndexWebhookDelivery): Long {
        val inserted = dao.insert(delivery.toEntity())
        if (inserted != -1L) return inserted
        return requireNotNull(dao.getByDeliveryId(delivery.deliveryId)).id
    }

    override suspend fun getPending(): List<IndexWebhookDelivery> =
        dao.getPending().map { it.toDomain() }

    override suspend fun getById(id: Long): IndexWebhookDelivery? = dao.getById(id)?.toDomain()

    override suspend fun setStatus(id: Long, status: TaskStatus) {
        if (status == TaskStatus.Success) {
            dao.markSuccessAndClearPayload(id)
        } else {
            dao.setStatus(id, status)
        }
    }

    override suspend fun resetForRetry(deliveryId: String): Long? {
        if (dao.resetForRetry(deliveryId) == 0) return null
        return requireNotNull(dao.getByDeliveryId(deliveryId)).id
    }

    private fun IndexWebhookDelivery.toEntity() = IndexWebhookDeliveryEntity(
        id = id,
        created = created,
        status = status,
        deliveryId = deliveryId,
        gesture = gesture.name,
        url = url,
        headersJson = Json.encodeToString(headerSerializer, headers),
        audioData = audioData,
        filename = filename,
        transcription = transcription,
        recordedAt = recordedAt,
    )

    private fun IndexWebhookDeliveryEntity.toDomain() = IndexWebhookDelivery(
        id = id,
        created = created,
        status = status,
        deliveryId = deliveryId,
        gesture = RingGesture.valueOf(gesture),
        url = url,
        headers = Json.decodeFromString(headerSerializer, headersJson),
        audioData = audioData,
        filename = filename,
        transcription = transcription,
        recordedAt = recordedAt,
    )
}
