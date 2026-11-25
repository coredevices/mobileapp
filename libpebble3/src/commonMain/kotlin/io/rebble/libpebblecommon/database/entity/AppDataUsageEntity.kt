package io.rebble.libpebblecommon.database.entity

import androidx.room.Entity
import kotlin.uuid.Uuid

/** One app's network traffic under one permission, on one local day, over one kind of connection. */
@Entity(
    tableName = "AppDataUsage",
    primaryKeys = ["appUuid", "permission", "day", "connection"],
)
public data class AppDataUsageEntity internal constructor(
    val appUuid: Uuid,
    /** `GlobalInternet` for a legacy app; otherwise `LocalNetwork`, or the `Internet/<domain>` key covering the host. */
    val permission: String,
    /** Local date, in days since the epoch. */
    val day: Int,
    /** One of `Connections`, or [UNKNOWN_CONNECTION]. */
    val connection: String,
    /** The app declares [permission] in its manifest. Always true when [permission] has no domain. */
    val declared: Boolean,
    val requests: Long,
    val sentBytes: Long,
    val receivedBytes: Long,
)

internal const val UNKNOWN_CONNECTION: String = "Unknown"
