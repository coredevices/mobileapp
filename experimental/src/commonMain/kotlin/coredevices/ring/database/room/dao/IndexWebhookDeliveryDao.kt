package coredevices.ring.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import coredevices.ring.data.entity.room.IndexWebhookDeliveryEntity
import coredevices.util.queue.TaskStatus

@Dao
interface IndexWebhookDeliveryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(delivery: IndexWebhookDeliveryEntity): Long

    @Query("SELECT * FROM IndexWebhookDeliveryEntity WHERE deliveryId = :deliveryId LIMIT 1")
    suspend fun getByDeliveryId(deliveryId: String): IndexWebhookDeliveryEntity?

    @Query("SELECT * FROM IndexWebhookDeliveryEntity WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): IndexWebhookDeliveryEntity?

    @Query("SELECT * FROM IndexWebhookDeliveryEntity WHERE status = 'Pending' ORDER BY created ASC")
    suspend fun getPending(): List<IndexWebhookDeliveryEntity>

    @Query("UPDATE IndexWebhookDeliveryEntity SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: TaskStatus)

    @Query(
        "UPDATE IndexWebhookDeliveryEntity SET status = 'Success', " +
            "url = '', headersJson = '{}', audioData = NULL, filename = NULL, transcription = NULL " +
            "WHERE id = :id",
    )
    suspend fun markSuccessAndClearPayload(id: Long)

    @Query(
        "UPDATE IndexWebhookDeliveryEntity " +
            "SET status = 'Pending' " +
            "WHERE deliveryId = :deliveryId AND status = 'Failed'",
    )
    suspend fun resetForRetry(deliveryId: String): Int
}
