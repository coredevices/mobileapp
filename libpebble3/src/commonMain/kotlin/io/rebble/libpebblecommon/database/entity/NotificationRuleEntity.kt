package io.rebble.libpebblecommon.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index(value = ["targetType", "target"])])
data class NotificationRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetType: TargetType,
    val target: String?,
    val matchType: MatchType,
    val matchField: MatchField,
    val pattern: String,
    val caseSensitive: Boolean = false,
)

enum class TargetType {
    App,
}

enum class MatchType {
    Text,
    Regex,
}

enum class MatchField {
    Both,
    Title,
    Body,
}
