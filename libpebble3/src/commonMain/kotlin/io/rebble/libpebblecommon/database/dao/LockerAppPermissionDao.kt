package io.rebble.libpebblecommon.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.rebble.libpebblecommon.database.entity.LockerAppPermission
import io.rebble.libpebblecommon.database.entity.PermissionDeclaration
import kotlinx.coroutines.flow.Flow
import kotlin.uuid.Uuid

@Dao
interface LockerAppPermissionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(permission: LockerAppPermission)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceAll(permissions: List<LockerAppPermission>)

    /** Never overwrites an existing row. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(permissions: List<LockerAppPermission>)

    @Query("UPDATE AppPermission SET lastUsedAtMs = :atMs WHERE appUuid = :appUuid AND permission = :permission")
    suspend fun markUsed(appUuid: Uuid, permission: String, atMs: Long)

    @Query("UPDATE AppPermission SET seenAtMs = :atMs WHERE appUuid = :appUuid AND permission IN (:permissions) AND seenAtMs IS NULL")
    suspend fun markSeen(appUuid: Uuid, permissions: List<String>, atMs: Long)

    @Query("UPDATE AppPermission SET askedAtMs = :atMs WHERE appUuid = :appUuid AND permission IN (:permissions) AND askedAtMs IS NULL")
    suspend fun markAsked(appUuid: Uuid, permissions: List<String>, atMs: Long)

    @Query("UPDATE AppPermission SET reason = NULL, required = NULL WHERE appUuid = :appUuid")
    suspend fun clearDeclarations(appUuid: Uuid)

    @Query("UPDATE AppPermission SET reason = :reason, required = :required WHERE appUuid = :appUuid AND permission = :permission")
    suspend fun setDeclaration(appUuid: Uuid, permission: String, reason: String?, required: Boolean?)

    @Transaction
    suspend fun replaceDeclarations(appUuid: Uuid, declarations: Map<String, PermissionDeclaration>) {
        clearDeclarations(appUuid)
        declarations.forEach { (key, declaration) -> setDeclaration(appUuid, key, declaration.reason, declaration.required) }
    }

    @Query("DELETE FROM AppPermission WHERE appUuid = :appUuid")
    suspend fun deleteByAppUuid(appUuid: Uuid)

    @Query("DELETE FROM AppPermission WHERE appUuid = :appUuid AND permission IN (:permissions)")
    suspend fun deleteByAppUuidAndPermissions(appUuid: Uuid, permissions: List<String>)

    @Query("DELETE FROM AppPermission WHERE appUuid NOT IN (SELECT id FROM LockerEntryEntity WHERE deleted = 0)")
    suspend fun deleteForRemovedApps()

    @Query("SELECT * FROM AppPermission WHERE appUuid = :appUuid")
    suspend fun getByAppUuid(appUuid: Uuid): List<LockerAppPermission>

    // A decision replaces its row, so without an order it would move to the end.
    @Query("SELECT * FROM AppPermission WHERE appUuid = :appUuid ORDER BY requestedAtMs, permission")
    fun flowByAppUuid(appUuid: Uuid): Flow<List<LockerAppPermission>>

    @Query("SELECT * FROM AppPermission ORDER BY requestedAtMs")
    fun allFlow(): Flow<List<LockerAppPermission>>
}
