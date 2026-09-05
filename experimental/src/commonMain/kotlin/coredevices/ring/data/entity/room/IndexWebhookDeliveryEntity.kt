package coredevices.ring.data.entity.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import coredevices.util.queue.TaskStatus
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    indices = [
        Index(value = ["deliveryId"], unique = true),
        Index("status"),
    ],
)
data class IndexWebhookDeliveryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val created: Instant = Clock.System.now(),
    val recordedAt: Instant? = null,
    val status: TaskStatus = TaskStatus.Pending,
    val attempts: Int = 0,
    val nextAttemptAt: Instant? = null,
    val failedAt: Instant? = null,
    val deliveryId: String,
    val gesture: String,
    val url: String,
    val headersJson: String,
    val fileId: String?,
    val audioData: ByteArray?,
    val transcription: String?,
    val recordingId: Long,
)
