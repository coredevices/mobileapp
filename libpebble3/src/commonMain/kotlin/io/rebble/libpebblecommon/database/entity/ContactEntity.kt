package io.rebble.libpebblecommon.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon

@Entity
data class ContactEntity(
    @PrimaryKey val lookupKey: String,
    val name: String,
    val muteState: MuteState,
    @ColumnInfo(defaultValue = "null")
    val vibePatternName: String?,
)
