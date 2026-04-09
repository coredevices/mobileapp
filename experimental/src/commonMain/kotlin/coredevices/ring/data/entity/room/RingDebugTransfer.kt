package coredevices.ring.data.entity.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant

@Entity
data class RingDebugTransfer(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val satelliteName: String?,
    val satelliteId: String,
    val satelliteFirmwareVersion: String,
    val satelliteLastAdvertisementTimestamp: Instant,
    val collectionIndex: Int,
    @ColumnInfo(defaultValue = "-1")
    val collectionStartCount: Long,
    val buttonSequence: String?,
    val sampleCount: Int,
    val sampleRate: Long,
    val buttonReleaseTimestamp: Instant?,
    val transferCompleteTimestamp: Instant,
    val storedPath: String
)