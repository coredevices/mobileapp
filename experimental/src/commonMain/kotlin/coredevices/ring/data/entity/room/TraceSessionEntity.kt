package coredevices.ring.data.entity.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant

@Entity
data class TraceSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val started: Instant,
)
