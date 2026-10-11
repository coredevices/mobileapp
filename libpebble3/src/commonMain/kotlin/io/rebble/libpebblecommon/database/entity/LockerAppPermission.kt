package io.rebble.libpebblecommon.database.entity

import androidx.room.Entity
import kotlin.uuid.Uuid

@Entity(
    tableName = "AppPermission",
    primaryKeys = ["appUuid", "permission"],
)
data class LockerAppPermission(
    val appUuid: Uuid,
    /** A grant key: see `permissionOf`. */
    val permission: String,
    /** Null until the user decides. */
    val granted: Boolean?,
    val requestedAtMs: Long,
    val decidedAtMs: Long? = null,
    val lastUsedAtMs: Long? = null,
    /** The user has looked at this request without deciding: hides its locker card. */
    val seenAtMs: Long? = null,
)
