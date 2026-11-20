package io.rebble.libpebblecommon.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index(value = ["targetType", "target"])])
public data class NotificationRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetType: TargetType,
    val target: String?,
    val matchType: MatchType,
    val matchField: MatchField,
    val pattern: String,
    val caseSensitive: Boolean = false,
)

public enum class TargetType {
    App,
}

public enum class MatchType {
    Text,
    Regex,
}

public enum class MatchField {
    Both,
    Title,
    Body,
}
