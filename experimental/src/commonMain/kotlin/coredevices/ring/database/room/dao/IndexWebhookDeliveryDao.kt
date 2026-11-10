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
    /** Returns -1 when a delivery with the same deliveryId already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTask(task: IndexWebhookDeliveryEntity): Long

    @Query("SELECT * FROM IndexWebhookDeliveryEntity WHERE status = 'Pending' ORDER BY created ASC")
    suspend fun getPendingTasks(): List<IndexWebhookDeliveryEntity>

    @Query("UPDATE IndexWebhookDeliveryEntity SET status = :status WHERE id = :taskId")
    suspend fun updateTaskStatus(taskId: Long, status: TaskStatus)

    @Query("DELETE FROM IndexWebhookDeliveryEntity WHERE id = :taskId")
    suspend fun deleteTask(taskId: Long)

    @Query("DELETE FROM IndexWebhookDeliveryEntity WHERE status != 'Pending' AND lastAttempt IS NOT NULL and lastAttempt < :before")
    suspend fun deleteCompletedTasksAttemptedBefore(before: Instant)

    @Query("SELECT * FROM IndexWebhookDeliveryEntity WHERE id = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: Long): IndexWebhookDeliveryEntity?

    @Query("UPDATE IndexWebhookDeliveryEntity SET attempts = attempts + 1, lastAttempt = :currentTime WHERE id = :taskId")
    suspend fun incrementAttempts(taskId: Long, currentTime: Instant)

    @Query("UPDATE IndexWebhookDeliveryEntity SET status = 'Pending', attempts = 0 WHERE deliveryId = :deliveryId AND status = 'Failed'")
    suspend fun resetFailed(deliveryId: String): Int

    @Query("SELECT id FROM IndexWebhookDeliveryEntity WHERE deliveryId = :deliveryId")
    suspend fun getIdByDeliveryId(deliveryId: String): Long?
}
