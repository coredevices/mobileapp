@file:OptIn(ExperimentalTime::class)

package coredevices.ring.data.entity.room.indexfeed

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import coredevices.indexai.data.entity.ListDocument
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Local Room mirror of [ListDocument]. [firestoreId] is the primary key
 * (matches Firestore doc id, e.g. "list_todos" for system lists).
 */
@Entity(
    indices = [
        Index(value = ["firestoreId"], unique = true),
        Index(value = ["seed"]),
        Index(value = ["updatedAt"]),
    ]
)
data class CachedList(
    @PrimaryKey val firestoreId: String,
    val createdAt: Instant = Clock.System.now(),
    val updatedAt: Instant = Clock.System.now(),
    val title: String = "",
    @ColumnInfo(defaultValue = "📝")
    val icon: String = "📝",
    @ColumnInfo(defaultValue = "note")
    val listKind: String = "note",
    val seed: String? = null,
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
) {
    fun toDocument(): ListDocument = ListDocument(
        createdAt = createdAt,
        updatedAt = updatedAt,
        title = title,
        icon = icon,
        listKind = listKind,
        seed = seed,
        deleted = deleted,
    )

    companion object {
        fun fromDocument(firestoreId: String, doc: ListDocument): CachedList = CachedList(
            firestoreId = firestoreId,
            createdAt = doc.createdAt,
            updatedAt = doc.updatedAt,
            title = doc.title,
            icon = doc.icon,
            listKind = doc.listKind,
            seed = doc.seed,
            deleted = doc.deleted,
        )
    }
}
