package coredevices.ring.data.entity.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import coredevices.indexai.data.entity.GeofenceLocationDocument
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Local mirror of [GeofenceLocationDocument]; [id] is the Firestore doc id. */
@Entity
data class SavedGeofenceLocation (
    @PrimaryKey val id: Uuid = Uuid.random(),
    val name: String,
    val lat: Double,
    val long: Double,
    val radiusMeters: Float,
    val cachedAddress: String? = null,
    val updatedAt: Instant = Clock.System.now(),
    /** Tombstone, kept so the delete syncs to other devices. */
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
) {
    fun toDocument() = GeofenceLocationDocument(
        updatedAt = updatedAt,
        name = name,
        lat = lat,
        long = long,
        radiusMeters = radiusMeters.toDouble(),
        address = cachedAddress,
        deleted = deleted,
    )

    companion object {
        fun fromDocument(id: Uuid, doc: GeofenceLocationDocument) = SavedGeofenceLocation(
            id = id,
            name = doc.name,
            lat = doc.lat,
            long = doc.long,
            radiusMeters = doc.radiusMeters.toFloat(),
            cachedAddress = doc.address,
            updatedAt = doc.updatedAt,
            deleted = doc.deleted,
        )
    }
}
