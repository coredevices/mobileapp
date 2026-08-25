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
    val status: TaskStatus = TaskStatus.Pending,
    val deliveryId: String,
    val gesture: String,
    val url: String,
    val headersJson: String,
    val audioData: ByteArray?,
    val filename: String?,
    val transcription: String?,
    val recordedAt: Instant,
)
