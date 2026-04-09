package coredevices.ring.data.entity.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class CachedRecordingMetadata(
    @PrimaryKey val id: String,
    val sampleRate: Int,
    val mimeType: String
)