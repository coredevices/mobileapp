package coredevices.ring.data.entity.room.reminders

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant

@Entity
data class LocalReminderData(
    @PrimaryKey(autoGenerate = true) val id: Int,
    val time: Instant?,
    val message: String,
    /** Firestore id of the recording this reminder was created from, when known.
     *  Used to locate the reminder's feed item so its notification can deep link
     *  to it. Null for reminders created before this column existed or outside a
     *  recording (e.g. the share sheet). */
    @ColumnInfo(defaultValue = "NULL") val recordingId: String? = null,
    /** Lead time (milliseconds) before [time] at which to fire an early heads-up notification,
     *  or null for none. Persisted so the early alarm can be re-scheduled after a device reboot. */
    @ColumnInfo(defaultValue = "NULL") val notifyBeforeMillis: Long? = null,
)
