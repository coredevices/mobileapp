package io.rebble.libpebblecommon.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.rebble.libpebblecommon.database.MillisecondInstant
import io.rebble.libpebblecommon.notification.NotificationDecision

@Entity(indices = [
    Index(value = ["pkg", "channelId"], unique = false),
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
    @ColumnInfo(defaultValue = "")
	val vibrationPattern: List<UInt> = emptyList(),
)
