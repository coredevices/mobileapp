@file:OptIn(ExperimentalTime::class)

package coredevices.ring.data.entity.room.indexfeed

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import coredevices.indexai.data.entity.ItemDocument
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Local Room mirror of [ItemDocument]. Same pattern as the existing Room
 * mirror of `RecordingDocument` (`LocalRecording`) — Room is the read path
 * for the UI flows so we get fast, indexed, offline-capable reads without
 * relying on Firestore's offline cache. Writes flow through Firestore via
 * the manual Sync-now pipeline (see `SettingsViewModel.downloadFeedHistory`).
 *
 * Shape matches `ItemDocument` exactly with [firestoreId] as the primary
 * key. [parentListIdsCsv] flattens the multi-list array into a
 * comma-separated string because Room doesn't natively support
 * `List<String>` columns without a TypeConverter; queries use LIKE
 * matching for membership.
 */
@Entity(
    indices = [
        Index(value = ["firestoreId"], unique = true),
        Index(value = ["sourceRecordingId"]),
        Index(value = ["updatedAt"]),
    ]
)
data class CachedItem(
    @PrimaryKey val firestoreId: String,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
    val title: String = "",
    val body: String = "",
    @ColumnInfo(defaultValue = "0") val done: Boolean = false,
    val dueAt: Instant? = null,
    /** Comma-separated list IDs this item belongs to. Empty string = no parent. */
    @ColumnInfo(defaultValue = "")
    val parentListIdsCsv: String = "",
    val sourceRecordingId: String? = null,
    val sourceToolCallId: String? = null,
    @ColumnInfo(defaultValue = "{\"type\":\"note\"}")
    val metadata: ItemDocument.ItemMetadata = ItemDocument.ItemMetadata.Note,
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
) {
    fun parentListIds(): List<String> =
        if (parentListIdsCsv.isBlank()) emptyList() else parentListIdsCsv.split(",")

    fun toDocument(): ItemDocument = ItemDocument(
        createdAt = createdAt,
        updatedAt = updatedAt,
        title = title,
        body = body,
        done = done,
        dueAt = dueAt,
        parentListIds = parentListIds(),
        sourceRecordingId = sourceRecordingId,
        sourceToolCallId = sourceToolCallId,
        metadata = metadata,
        deleted = deleted,
    )

    companion object {
        fun fromDocument(firestoreId: String, doc: ItemDocument): CachedItem = CachedItem(
            firestoreId = firestoreId,
            createdAt = doc.createdAt,
            updatedAt = doc.updatedAt,
            title = doc.title,
            body = doc.body,
            done = doc.done,
            dueAt = doc.dueAt,
            parentListIdsCsv = doc.parentListIds.joinToString(","),
            sourceRecordingId = doc.sourceRecordingId,
            sourceToolCallId = doc.sourceToolCallId,
            metadata = doc.metadata,
            deleted = doc.deleted,
        )
    }
}
