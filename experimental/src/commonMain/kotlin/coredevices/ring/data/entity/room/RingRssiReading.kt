package coredevices.ring.data.entity.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.time.Instant

/** One Rx RSSI measurement taken on the ring, paired with how the phone heard the ring at the time. */
@Entity(indices = [Index("timestamp"), Index("ringSerialHash")])
data class RingRssiReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Instant,
    /** First 8 hex chars of the SHA-256 of the ring serial the reading came from. */
    val ringSerialHash: String,
    /** Phone signal as measured by the ring, dBm. */
    val ringRxRssi: Float,
    /** Ring's last advertisement as measured by the phone, dBm. Null when no advertisement was cached. */
    val phoneRssi: Float?,
    /** `phoneRssi - ringRxRssi`. Positive means the ring hears the phone worse than the phone hears the ring. */
    val asymmetryDb: Float?,
) {
    companion object {
        fun of(timestamp: Instant, ringSerialHash: String, ringRxRssi: Float, phoneRssi: Float?) = RingRssiReading(
            timestamp = timestamp,
            ringSerialHash = ringSerialHash,
            ringRxRssi = ringRxRssi,
            phoneRssi = phoneRssi,
            asymmetryDb = phoneRssi?.let { it - ringRxRssi },
        )
    }
}
