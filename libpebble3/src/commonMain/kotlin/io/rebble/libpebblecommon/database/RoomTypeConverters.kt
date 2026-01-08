package io.rebble.libpebblecommon.database

import androidx.room.TypeConverter
import io.rebble.libpebblecommon.database.entity.BaseAction
import io.rebble.libpebblecommon.database.entity.BaseAttribute
import io.rebble.libpebblecommon.database.entity.ChannelGroup
import io.rebble.libpebblecommon.database.entity.CompanionApp
import io.rebble.libpebblecommon.database.entity.LockerEntryPlatform
import io.rebble.libpebblecommon.metadata.WatchColor
import io.rebble.libpebblecommon.metadata.WatchColor.Companion.fromProtocolNumber
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

private val json = Json { ignoreUnknownKeys = true }

// Hashcode on Duration can vary (because nanoseconds) before/after serialization (which is only milliseconds)
data class MillisecondDuration(val duration: Duration) {
    override fun hashCode(): Int = duration.inWholeMilliseconds.hashCode()
    override fun equals(other: Any?): Boolean = (other as? MillisecondDuration)?.duration?.inWholeMilliseconds == duration.inWholeMilliseconds
}

fun Duration.asMillisecond(): MillisecondDuration = MillisecondDuration(this)

// Hashcode on Instant can vary (because nanoseconds) before/after serialization (which is only milliseconds)
data class MillisecondInstant(val instant: Instant) {
    override fun hashCode(): Int = instant.toEpochMilliseconds().hashCode()
    override fun equals(other: Any?): Boolean = (other as? MillisecondInstant)?.instant?.toEpochMilliseconds() == instant.toEpochMilliseconds()
}

fun Instant.asMillisecond(): MillisecondInstant = MillisecondInstant(this)

/**
 * Compare this MillisecondInstant with an Instant to check if this is after the given instant.
 * Useful for checking if a mute expiration has passed.
 */
fun MillisecondInstant.isAfter(other: Instant): Boolean = instant > other

/**
 * Get the epoch milliseconds from a MillisecondInstant.
 * Useful for comparisons when the instant property is not accessible from other modules.
 */
fun MillisecondInstant.toEpochMilliseconds(): Long = instant.toEpochMilliseconds()

class RoomTypeConverters {
    @TypeConverter
    fun ListUIntToString(value: List<UInt>): String = value.joinToString(",");

    @TypeConverter
    fun StringToListUInt(value: String): List<UInt> = value.split(",").mapNotNull { it.toUIntOrNull() };

    @TypeConverter
    fun StringToUuid(string: String?): Uuid? = string?.let { Uuid.parse(it) }

    @TypeConverter
    fun UuidToString(uuid: Uuid?): String? = uuid?.toString()

    @TypeConverter
    fun StringToChannelGroupList(value: String): List<ChannelGroup> {
        return json.decodeFromString(value)
    }

    @TypeConverter
    fun ChannelGroupListToString(list: List<ChannelGroup>): String {
        return json.encodeToString(list)
    }

    @TypeConverter
    fun LongToMillisecondInstant(value: Long): MillisecondInstant = MillisecondInstant(Instant.fromEpochMilliseconds(value))

    @TypeConverter
    fun MillisecondInstantToLong(instant: MillisecondInstant): Long = instant.instant.toEpochMilliseconds()

    @TypeConverter
    fun LongToMillisecondDuration(value: Long): MillisecondDuration = MillisecondDuration(value.milliseconds)

    @TypeConverter
    fun MillisecondDurationToLong(duration: MillisecondDuration): Long = duration.duration.inWholeMilliseconds

    @TypeConverter
    fun StringToLockerPlatformList(value: String): List<LockerEntryPlatform> {
        return json.decodeFromString(value)
    }

    @TypeConverter
    fun LockerPlatformListToString(list: List<LockerEntryPlatform>): String {
        return json.encodeToString(list)
    }

    @TypeConverter
    fun StringToLockerCompanion(value: String?): CompanionApp? {
        return value?.let { json.decodeFromString(it) }
    }

    @TypeConverter
    fun LockerCompanionListToString(app: CompanionApp?): String? {
        return app?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun StringToTimelineAttributeList(value: String): List<BaseAttribute> {
        return json.decodeFromString(value)
    }

    @TypeConverter
    fun TimelineAttributeListToString(list: List<BaseAttribute>): String {
        return json.encodeToString(list)
    }

    @TypeConverter
    fun StringToTimelineActionList(value: String): List<BaseAction> {
        return json.decodeFromString(value)
    }

    @TypeConverter
    fun TimelineActionListToString(list: List<BaseAction>): String {
        return json.encodeToString(list)
    }

    @TypeConverter
    fun StringToTimelineFlagList(value: String): List<TimelineItem.Flag> {
        return json.decodeFromString(value)
    }

    @TypeConverter
    fun TimelineFlagListToString(list: List<TimelineItem.Flag>): String {
        return json.encodeToString(list)
    }

    @TypeConverter
    fun IntToWatchColor(code: Int?): WatchColor? = fromProtocolNumber(code)

    @TypeConverter
    fun WatchColorToInt(color: WatchColor): Int? = color.protocolNumber

    @TypeConverter
    fun StringToStringList(value: String?): List<String> {
        return value?.let {
            json.decodeFromString(it)
        } ?: emptyList()
    }

    @TypeConverter
    fun StringListToString(list: List<String>): String {
        return json.encodeToString(list)
    }
}
