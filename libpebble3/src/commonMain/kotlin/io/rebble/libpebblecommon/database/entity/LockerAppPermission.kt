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
    /**
     * The app asked for it: by using it, or by declaring it required. Only then does an undecided one
     * prompt (notification, card); an optional one waits for the app to use it.
     */
    val askedAtMs: Long? = null,
    /** From the installed version's declaration of it: why the app wants it. */
    val reason: String? = null,
    /** From the same declaration; null when the app doesn't say which permissions are required. */
    val required: Boolean? = null,
)

data class PermissionDeclaration(val reason: String?, val required: Boolean?)
