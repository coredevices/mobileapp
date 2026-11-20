package io.rebble.libpebblecommon.database

import androidx.room.TypeConverter
import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.database.entity.BaseAction
import io.rebble.libpebblecommon.database.entity.BaseAttribute
import io.rebble.libpebblecommon.database.entity.ChannelGroup
import io.rebble.libpebblecommon.database.entity.CompanionApp
import io.rebble.libpebblecommon.database.entity.LockerEntryPlatform
import io.rebble.libpebblecommon.metadata.WatchColor
import io.rebble.libpebblecommon.metadata.WatchColor.Companion.fromProtocolNumber
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.packets.blobdb.TimelineItemFlag
import io.rebble.libpebblecommon.weather.WeatherDailyForecast
import io.rebble.libpebblecommon.weather.WeatherHourlyForecast
import kotlinx.serialization.SerializationException
import kotlin.time.Instant
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

private val json = Json { ignoreUnknownKeys = true }
private val logger = Logger.withTag("RoomTypeConverters")

// Hashcode on Duration can vary (because nanoseconds) before/after serialization (which is only milliseconds)
public data class MillisecondDuration internal constructor(val duration: Duration) {
    override fun hashCode(): Int = duration.inWholeMilliseconds.hashCode()
    override fun equals(other: Any?): Boolean = (other as? MillisecondDuration)?.duration?.inWholeMilliseconds == duration.inWholeMilliseconds
}

internal fun Duration.asMillisecond(): MillisecondDuration = MillisecondDuration(this)

// Hashcode on Instant can vary (because nanoseconds) before/after serialization (which is only milliseconds)
public data class MillisecondInstant internal constructor(val instant: Instant) {
    override fun hashCode(): Int = instant.toEpochMilliseconds().hashCode()
    override fun equals(other: Any?): Boolean = (other as? MillisecondInstant)?.instant?.toEpochMilliseconds() == instant.toEpochMilliseconds()
}

internal fun Instant.asMillisecond(): MillisecondInstant = MillisecondInstant(this)

/**
 * Compare this MillisecondInstant with an Instant to check if this is after the given instant.
 * Useful for checking if a mute expiration has passed.
 */
public fun MillisecondInstant.isAfter(other: Instant): Boolean = instant > other

/**
 * Get the epoch milliseconds from a MillisecondInstant.
 * Useful for comparisons when the instant property is not accessible from other modules.
 */
internal fun MillisecondInstant.toEpochMilliseconds(): Long = instant.toEpochMilliseconds()

public class RoomTypeConverters {
    @TypeConverter
    internal fun ListUIntToString(value: List<UInt>): String = value.joinToString(",");

    @TypeConverter
    internal fun StringToListUInt(value: String): List<UInt> = value.split(",").mapNotNull { it.toUIntOrNull() };

    @TypeConverter
    public fun StringToUuid(string: String?): Uuid? = string?.let { Uuid.parse(it) }

    @TypeConverter
    public fun UuidToString(uuid: Uuid?): String? = uuid?.toString()

    @TypeConverter
    internal fun StringToChannelGroupList(value: String): List<ChannelGroup> {
        return json.decodeFromString(value)
    }

    @TypeConverter
    internal fun ChannelGroupListToString(list: List<ChannelGroup>): String {
        return json.encodeToString(list)
    }

    @TypeConverter
    internal fun StringToWeatherDailyForecastList(value: String?): List<WeatherDailyForecast>? =
        value?.let { json.decodeFromString(it) }

    @TypeConverter
    internal fun WeatherDailyForecastListToString(list: List<WeatherDailyForecast>?): String? =
        list?.let { json.encodeToString(it) }

    @TypeConverter
    internal fun StringToWeatherHourlyForecastList(value: String?): List<WeatherHourlyForecast>? =
        value?.let { json.decodeFromString(it) }

    @TypeConverter
    internal fun WeatherHourlyForecastListToString(list: List<WeatherHourlyForecast>?): String? =
        list?.let { json.encodeToString(it) }

    @TypeConverter
    internal fun LongToMillisecondInstant(value: Long): MillisecondInstant = MillisecondInstant(Instant.fromEpochMilliseconds(value))

    @TypeConverter
    internal fun MillisecondInstantToLong(instant: MillisecondInstant): Long = instant.instant.toEpochMilliseconds()

    @TypeConverter
    internal fun LongToMillisecondDuration(value: Long): MillisecondDuration = MillisecondDuration(value.milliseconds)

    @TypeConverter
    internal fun MillisecondDurationToLong(duration: MillisecondDuration): Long = duration.duration.inWholeMilliseconds

    @TypeConverter
    internal fun StringToLockerPlatformList(value: String): List<LockerEntryPlatform> {
        return json.decodeFromString(value)
    }

    @TypeConverter
    internal fun LockerPlatformListToString(list: List<LockerEntryPlatform>): String {
        return json.encodeToString(list)
    }

    @TypeConverter
    internal fun StringToLockerCompanion(value: String?): CompanionApp? {
        return value?.let { json.decodeFromString(it) }
    }

    @TypeConverter
    internal fun LockerCompanionListToString(app: CompanionApp?): String? {
        return app?.let { json.encodeToString(it) }
    }

    @TypeConverter
    internal fun StringToTimelineAttributeList(value: String): List<BaseAttribute> {
        return try {
            json.decodeFromString(value)
        } catch (e: SerializationException) {
            logger.e(e) { "Failed to decode timeline attributes, returning empty list. JSON: ${value.take(200)}" }
            emptyList()
        }
    }

    @TypeConverter
    internal fun TimelineAttributeListToString(list: List<BaseAttribute>): String {
        return json.encodeToString(list)
    }

    @TypeConverter
    internal fun StringToTimelineActionList(value: String): List<BaseAction> {
        return try {
            json.decodeFromString(value)
        } catch (e: SerializationException) {
            logger.e(e) { "Failed to decode timeline actions, returning empty list. JSON: ${value.take(200)}" }
            emptyList()
        }
    }

    @TypeConverter
    internal fun TimelineActionListToString(list: List<BaseAction>): String {
        return json.encodeToString(list)
    }

    @TypeConverter
    internal fun StringToTimelineFlagList(value: String): List<TimelineItemFlag> {
        return json.decodeFromString(value)
    }

    @TypeConverter
    internal fun TimelineFlagListToString(list: List<TimelineItemFlag>): String {
        return json.encodeToString(list)
    }

    @TypeConverter
    internal fun IntToWatchColor(code: Int?): WatchColor? = fromProtocolNumber(code)

    @TypeConverter
    internal fun WatchColorToInt(color: WatchColor): Int? = color.protocolNumber

    @TypeConverter
    internal fun StringToStringList(value: String?): List<String> {
        return value?.let {
            json.decodeFromString(it)
        } ?: emptyList()
    }

    @TypeConverter
    internal fun StringListToString(list: List<String>): String {
        return json.encodeToString(list)
    }

    @TypeConverter
    internal fun StringToCapabilitySet(value: String): Set<ProtocolCapsFlag> {
        // Names stored by a newer build are dropped, so going back to an older one doesn't crash.
        return json.decodeFromString<List<String>>(value)
            .mapNotNull { name -> ProtocolCapsFlag.entries.firstOrNull { it.name == name } }
            .toSet()
    }

    @TypeConverter
    internal fun CapabilitySetToString(list: Set<ProtocolCapsFlag>): String {
        return json.encodeToString(list)
    }
}
