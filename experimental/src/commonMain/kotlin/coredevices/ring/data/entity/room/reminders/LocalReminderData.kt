package coredevices.ring.data.entity.room.reminders

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import coredevices.ring.data.entity.room.SavedGeofenceLocation
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Per-device ledger of which reminder feed items currently have an OS alarm scheduled on this
 * device. Keyed by the feed item's global Firestore id, so the same reminder resolves to the
 * same alarm on every device it syncs to.
 */
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = SavedGeofenceLocation::class,
            parentColumns = ["id"],
            childColumns = ["geofenceLocationId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index("geofenceLocationId")],
)
data class LocalReminderData(
    @PrimaryKey val itemId: String,
    val time: Instant?,
    val message: String,
    val notifyBeforeMillis: Long? = null,
    /** Set for reminders armed on arrival at a saved location rather than at [time]. */
    val geofenceLocationId: Uuid? = null,
)
