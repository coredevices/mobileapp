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

enum class TargetType(val value: Int) {
    App(0);

    companion object {
        fun fromValue(value: Int): TargetType = entries.first { it.value == value }
    }
}

enum class MatchType(val value: Int) {
    Text(0),
    Regex(1);

    companion object {
        fun fromValue(value: Int): MatchType = entries.first { it.value == value }
    }
}

enum class MatchField(val value: Int) {
    Both(0),
    Title(1),
    Body(2);

    companion object {
        fun fromValue(value: Int): MatchField = entries.first { it.value == value }
    }
}
