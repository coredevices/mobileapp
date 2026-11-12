package io.rebble.libpebblecommon.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.rebble.libpebblecommon.database.MillisecondInstant
import io.rebble.libpebblecommon.notification.NotificationDecision
import kotlin.uuid.Uuid


@Entity(indices = [
    Index(value = ["pkg", "channelId"], unique = false),
    Index(value = ["key"], unique = false),
])
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pkg: String,
    val key: String,
    val groupKey: String?,
    val channelId: String?,
    val timestamp: MillisecondInstant,
    val title: String?,
    val body: String?,
    val decision: NotificationDecision,
    @ColumnInfo(defaultValue = "NULL")
    val people: List<String>? = emptyList(),
    /** Timeline item id on the watch; only set when sent. */
    @ColumnInfo(defaultValue = "NULL")
    val itemId: Uuid? = null,
    @ColumnInfo(defaultValue = "NULL")
    val messageKey: String? = null,
    @ColumnInfo(defaultValue = "NULL")
    val imageAspect: Int? = null,
)
