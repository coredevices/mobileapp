package coredevices.ring.data.entity.room.battery

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.time.Instant

/**
 * On-phone usage history of one ring, keyed by serial, for the battery-life estimate. Device-local
 * only. The sample fields are null until the first collection count arrives (voltage can come first).
 */
@Entity
data class RingBatteryHistory(
    @PrimaryKey val serial: String,
    val firstAt: Instant? = null,
    val firstCount: Int? = null,
    val lastAt: Instant? = null,
    val lastCount: Int? = null,
    val voltageMv: Int? = null,
)
