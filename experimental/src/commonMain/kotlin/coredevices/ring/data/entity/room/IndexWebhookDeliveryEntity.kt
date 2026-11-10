package coredevices.ring.data.entity.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import coredevices.ring.service.button.RingGesture
import coredevices.util.queue.QueueTask
import coredevices.util.queue.TaskStatus
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    indices = [
        Index(value = ["deliveryId"], unique = true),
        Index("status"),
    ]
)
data class IndexWebhookDeliveryEntity(
    @PrimaryKey(autoGenerate = true) override val id: Long = 0,
    override val created: Instant = Clock.System.now(),
    override val lastAttempt: Instant? = null,
    override val attempts: Int = 0,
    override val status: TaskStatus = TaskStatus.Pending,

    val deliveryId: String,
    val gesture: RingGesture,
    val recordingId: Long,
    val fileId: String?,
    val transcription: String?,
) : QueueTask
