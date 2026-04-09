package coredevices.ring.data.entity.room.reminders

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant

@Entity
data class LocalReminderData(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val time: Instant?,
    val message: String
)
