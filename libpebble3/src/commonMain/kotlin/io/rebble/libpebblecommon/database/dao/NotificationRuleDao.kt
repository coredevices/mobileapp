package io.rebble.libpebblecommon.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import io.rebble.libpebblecommon.database.entity.NotificationRuleEntity
import io.rebble.libpebblecommon.database.entity.TargetType
import kotlinx.coroutines.flow.Flow

@Dao
public interface NotificationRuleDao {
    @Query("SELECT * FROM NotificationRuleEntity WHERE targetType = :targetType AND target = :packageName")
    public fun getRulesForApp(targetType: TargetType = TargetType.App, packageName: String): Flow<List<NotificationRuleEntity>>

    @Query("SELECT * FROM NotificationRuleEntity WHERE targetType = :targetType AND target = :packageName")
    public suspend fun getRulesForAppOnce(targetType: TargetType = TargetType.App, packageName: String): List<NotificationRuleEntity>

    @Upsert
    public suspend fun upsert(rule: NotificationRuleEntity): Long

    @Query("DELETE FROM NotificationRuleEntity WHERE id = :id")
    public suspend fun deleteById(id: Long)
}
