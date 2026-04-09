package coredevices.ring.data.entity.room

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import coredevices.indexai.data.entity.LocalRecording
import coredevices.indexai.data.entity.RecordingEntryEntity
import coredevices.indexai.data.entity.RingTransferInfo
import kotlin.time.Clock
import kotlin.time.Instant

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = LocalRecording::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = RecordingEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordingEntryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["recordingId"]),
        Index(value = ["recordingEntryId"]),
        Index(value = ["isCurrentIndexIteration", "transferInfo_collectionStartIndex"]),
        Index(value = ["createdAt"])
    ]
)
data class RingTransfer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordingId: Long?,
    val recordingEntryId: Long?,
    val isCurrentIndexIteration: Boolean,
    @Embedded("transferInfo_")
    val transferInfo: RingTransferInfo?,
    val status: RingTransferStatus,
    val fileId: String? = null,
    val createdAt: Instant = Clock.System.now()
)

enum class RingTransferStatus {
    Started,
    Discarded,
    Failed,
    Completed
}
