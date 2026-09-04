package coredevices.ring.database.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import coredevices.ring.data.entity.room.IndexWebhookDeliveryEntity
import coredevices.util.queue.TaskStatus
import kotlin.time.Instant

@Dao
interface IndexWebhookDeliveryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(delivery: IndexWebhookDeliveryEntity): Long

    @Query("SELECT * FROM IndexWebhookDeliveryEntity WHERE deliveryId = :deliveryId LIMIT 1")
    suspend fun getByDeliveryId(deliveryId: String): IndexWebhookDeliveryEntity?

    @Query("SELECT * FROM IndexWebhookDeliveryEntity WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): IndexWebhookDeliveryEntity?

    @Query("SELECT gesture FROM IndexWebhookDeliveryEntity WHERE id = :id")
    suspend fun getGesture(id: Long): String?

    @Query("SELECT id FROM IndexWebhookDeliveryEntity WHERE status = 'Pending' ORDER BY created ASC")
    suspend fun getPendingIds(): List<Long>

    @Query("UPDATE IndexWebhookDeliveryEntity SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: TaskStatus)

    @Query(
        "UPDATE IndexWebhookDeliveryEntity " +
            "SET attempts = attempts + 1, nextAttemptAt = :nextAttemptAt WHERE id = :id",
    )
    suspend fun scheduleRetry(id: Long, nextAttemptAt: Instant)

    @Query(
        "UPDATE IndexWebhookDeliveryEntity SET status = 'Success', nextAttemptAt = NULL, " +
            "url = '', headersJson = '{}', fileId = NULL, transcription = NULL " +
            "WHERE id = :id",
    )
    suspend fun markSuccessAndClearPayload(id: Long)

    @Query(
        "UPDATE IndexWebhookDeliveryEntity " +
            "SET status = 'Pending', attempts = 0, nextAttemptAt = NULL " +
            "WHERE deliveryId = :deliveryId AND status = 'Failed'",
    )
    suspend fun resetForRetry(deliveryId: String): Int

    @Query(
        "DELETE FROM IndexWebhookDeliveryEntity WHERE status = 'Failed' AND gesture = :gesture " +
            "AND id NOT IN (SELECT id FROM IndexWebhookDeliveryEntity " +
            "WHERE status = 'Failed' AND gesture = :gesture " +
            "ORDER BY created DESC, id DESC LIMIT :keep)",
    )
    suspend fun pruneFailed(gesture: String, keep: Int)
}
