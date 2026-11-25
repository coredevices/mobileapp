package io.rebble.libpebblecommon.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@Entity
public data class VibePatternEntity internal constructor(
    @PrimaryKey val name: String,
    val pattern: List<UInt>,
    val bundled: Boolean,
)