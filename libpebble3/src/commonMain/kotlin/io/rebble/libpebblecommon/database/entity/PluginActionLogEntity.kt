package io.rebble.libpebblecommon.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlin.uuid.Uuid

/** An action an app had a plugin run. */
@Entity(tableName = "PluginActionLog", indices = [Index("atMs")])
data class PluginActionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMs: Long,
    val appUuid: Uuid,
    val pluginUuid: Uuid,
    val action: String,
    /** The arguments as JSON, cut short if long; empty except for Watch Settings. */
    val args: String,
    /** Null if it succeeded. */
    val error: String?,
)
