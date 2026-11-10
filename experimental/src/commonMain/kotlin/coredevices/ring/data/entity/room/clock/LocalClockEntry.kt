package coredevices.ring.data.entity.room.clock

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant

/**
 * A timer or alarm the Index agent set on this device. Device-local only: never synced or
 * backed up, since the notification it drives only exists on this phone.
 */
@Entity
data class LocalClockEntry(
    @PrimaryKey val id: String,
    val kind: Kind,
    val fireAt: Instant,
    val createdAt: Instant,
    val label: String? = null,
    /** Timers only: the duration originally asked for. */
    val durationMillis: Long? = null,
) {
    enum class Kind { Timer, Alarm }
}
