package coredevices.ring.data.entity.room.reminders

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant

/**
 * Per-device ledger of which reminder feed items currently have an OS alarm scheduled on this
 * device. Keyed by the feed item's global Firestore id, so the same reminder resolves to the
 * same alarm on every device it syncs to.
 */
@Entity
data class LocalReminderData(
    @PrimaryKey val itemId: String,
    val time: Instant?,
    val message: String,
    val notifyBeforeMillis: Long? = null,
)
