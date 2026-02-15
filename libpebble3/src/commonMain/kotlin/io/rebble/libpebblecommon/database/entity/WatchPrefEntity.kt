package io.rebble.libpebblecommon.database.entity

import androidx.compose.runtime.Immutable
import co.touchlab.kermit.Logger
import coredev.BlobDatabase
import coredev.GenerateRoomEntity
import io.rebble.libpebblecommon.SystemAppIDs.HEALTH_APP_UUID
import io.rebble.libpebblecommon.SystemAppIDs.QUIET_TIME_TOGGLE_UUID
import io.rebble.libpebblecommon.SystemAppIDs.TIMELINE_FUTURE_UUID
import io.rebble.libpebblecommon.database.MillisecondInstant
import io.rebble.libpebblecommon.database.asMillisecond
import io.rebble.libpebblecommon.database.dao.BlobDbItem
import io.rebble.libpebblecommon.database.dao.ValueParams
import io.rebble.libpebblecommon.database.dao.WatchPreference
import io.rebble.libpebblecommon.database.entity.QuickLaunchSetting.Companion.toJson
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.packets.blobdb.TimelineAttribute
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem.Attribute
import io.rebble.libpebblecommon.services.blobdb.DbWrite
import io.rebble.libpebblecommon.structmapper.SBoolean
import io.rebble.libpebblecommon.structmapper.SFixedList
import io.rebble.libpebblecommon.structmapper.SFixedString
import io.rebble.libpebblecommon.structmapper.SNullTerminatedString
import io.rebble.libpebblecommon.structmapper.SUByte
import io.rebble.libpebblecommon.structmapper.SUInt
import io.rebble.libpebblecommon.structmapper.SUShort
import io.rebble.libpebblecommon.structmapper.SUUID
import io.rebble.libpebblecommon.structmapper.StructMappable
import io.rebble.libpebblecommon.structmapper.StructMapper
import io.rebble.libpebblecommon.timeline.TimelineColor
import io.rebble.libpebblecommon.timeline.toPebbleColor
import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.Endian
import io.rebble.libpebblecommon.util.asTimelineColor
import io.rebble.libpebblecommon.util.toPebbleColor
import io.rebble.libpebblecommon.util.toProtocolNumber
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Immutable
@GenerateRoomEntity(
    primaryKey = "id",
    databaseId = BlobDatabase.WatchPrefs,
    windowBeforeSecs = -1,
    windowAfterSecs = -1,
    onlyInsertAfter = false,
    sendDeletions = true,
)
data class WatchPrefItem(
    val id: String,
    val value: String,
    val timestamp: MillisecondInstant,
) : BlobDbItem {
    override fun key(): UByteArray =
        SNullTerminatedString(StructMapper(), id).toBytes()

    override fun value(params: ValueParams): UByteArray? {
        val type = WatchPref.from(id)
        if (type == null) {
            logger.w { "Don't know how to encode watch pref key: $id" }
            return null
        }
        logger.v { "trying to insert watch pref to watch blobdb: $id / $value" }
        val bytes = try {
            when (type.type) {
                WatchPrefType.TypeString -> SNullTerminatedString(StructMapper(), value)
                    .toBytes()

                WatchPrefType.TypeUuid -> SFixedString(
                    StructMapper(),
                    value.length,
                    value
                ).toBytes()

                WatchPrefType.TypeUInt8, WatchPrefType.TypeBoolean -> SUByte(
                    StructMapper(),
                    value.toUByte().applyOffsetForSendToWatch(id, params.platform)
                ).toBytes()

                WatchPrefType.TypeUInt16 -> SUShort(
                    StructMapper(),
                    value.toUShort(),
                    endianness = Endian.Little
                ).toBytes()

                WatchPrefType.TypeUInt32 -> SUInt(
                    StructMapper(),
                    value.toUInt(),
                    endianness = Endian.Little
                ).toBytes()

                WatchPrefType.TypeQuickLaunch -> {
                    val setting = QuickLaunchSetting.fromJson(value)
                    val struct = QLMappable(
                        enabled = setting.enabled,
                        uuid = setting.uuid,
                    )
                    struct.toBytes()
                }

                WatchPrefType.TypeColor -> {
                    val color = TimelineColor.findByName(value)
                    if (color == null) {
                        logger.w { "Unknown color: $value" }
                        null
                    } else {
                        SUByte(StructMapper(), color.toPebbleColor().toProtocolNumber()).toBytes()
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Error encoding watch pref record $id" }
            return null
        }
        return bytes
    }

    override fun recordHashCode(): Int = hashCode()

    override fun timestamp(): Instant {
        return timestamp.instant
    }
}

enum class WatchPrefType {
    TypeString,
    TypeUuid,
    TypeBoolean,
    TypeUInt8,
    TypeUInt16,
    TypeUInt32,
    TypeQuickLaunch,
    TypeColor,
}

private val json = Json { ignoreUnknownKeys = true }
private val NULL_UUID = Uuid.parse("ffffffff-ffff-ffff-ffff-ffffffffffff")

@Serializable
data class QuickLaunchSetting(
    val enabled: Boolean,
    val uuid: Uuid?,
) {
    companion object {
        fun fromJson(str: String): QuickLaunchSetting = json.decodeFromString(str)
        fun QuickLaunchSetting.toJson() = json.encodeToString(this)
    }
}

class QLMappable(
    enabled: Boolean = true,
    uuid: Uuid? = null,
) : StructMappable() {
    val enabled = SBoolean(m, enabled)
    val uuid = SUUID(m, uuid ?: NULL_UUID)
}


sealed interface WatchPref<T> {
    val id: String
    val displayName: String
    val type: WatchPrefType
    val defaultValue: T
    fun decodeValue(value: String): T
    fun encodeValue(value: T): String
    fun castParent(parent: WatchPreference<*>): WatchPreference<T> = parent as WatchPreference<T>
    val isDebugSetting: Boolean

    companion object {
        fun enumeratePrefs(): List<WatchPref<*>> = BoolWatchPref.entries
            .plus(QuicklaunchWatchPref.entries)
            .plus(EnumWatchPref.entries)
            .plus(NumberWatchPref.entries)
            .plus(ColorWatchPref.entries)

        fun from(id: String): WatchPref<*>? = enumeratePrefs().find { it.id == id }
    }
}

enum class BoolWatchPref(
    override val id: String,
    override val displayName: String,
    override val defaultValue: Boolean,
    override val isDebugSetting: Boolean = false,
) : WatchPref<Boolean> {
    TimezoneSourceIsManual("timezoneSource", "Timezone configured manually", false),
    Clock24h("clock24h", "24h clock", false),
    StandbyMode("stationaryMode", "Standby Mode", true),
    LeftHandedMode("displayOrientationLeftHanded", "Left-handed Mode", false),
    Backlight("lightEnabled", "Backlight", true),
    AmbientLightSensor("lightAmbientSensorEnabled", "Ambient Light Sensor", true),
    BacklightMotion("lightMotion", "Backlight Motion", true),
    DynamicBacklightIntensity("lightDynamicIntensity", "Dynamic Backlight Intensity", true),
    LanguageEnglish("langEnglish", "Language: English", false),
    TimelineQuickViewEnabled("timelineQuickViewEnabled", "Timeline Quick View", true),
    QuietTimeManuallyEnabled("dndManuallyEnabled", "Quiet Time - Manual", false),
    CalendarAwareQuietTime("dndSmartEnabled", "Quiet Time - Calendar Aware", false),
    AlternativeNotificationStyle("notifDesignStyle", "Alternative notification banner Style (B/W watches)", false),
    NotificationVibeDelay("notifVibeDelay", "Delay Notification Vibration", true),
    NotificationBacklight("notifBacklight", "Notifications - Backlight", true),
    ;

    override val type = WatchPrefType.TypeBoolean
    override fun decodeValue(value: String): Boolean = value == "1"
    override fun encodeValue(value: Boolean): String = if (value) "1" else "0"
}

enum class QuicklaunchWatchPref(
    override val id: String,
    override val displayName: String,
    override val defaultValue: QuickLaunchSetting,
    override val isDebugSetting: Boolean = false,
) : WatchPref<QuickLaunchSetting> {
    QlUp("qlUp", "Quick Launch: Hold Up", QuickLaunchSetting(false, null)),
    QlDown("qlDown", "Quick Launch: Hold Down", QuickLaunchSetting(false, null)),
    QlSelect("qlSelect", "Quick Launch: Hold Select", QuickLaunchSetting(false, null)),
    QlBack("qlBack", "Quick Launch: Hold Back", QuickLaunchSetting(true, QUIET_TIME_TOGGLE_UUID)),
    QlSingleClickUp(
        "qlSingleClickUp",
        "Quick Launch: Tap Up",
        QuickLaunchSetting(true, HEALTH_APP_UUID)
    ),
    QlSingleClickDown(
        "qlSingleClickDown",
        "Quick Launch: Tap Down",
        QuickLaunchSetting(true, TIMELINE_FUTURE_UUID)
    ),
    ;

    override val type = WatchPrefType.TypeQuickLaunch
    override fun decodeValue(value: String): QuickLaunchSetting = QuickLaunchSetting.fromJson(value)
    override fun encodeValue(value: QuickLaunchSetting): String = value.toJson()
}

sealed interface WatchPrefEnum {
    val code: UByte
    val displayName: String
}

fun UByte.applyOffsetForSendToWatch(prefId: String, watchType: WatchType): UByte = when (prefId) {
    // Apply offset for obelix/etc so we can keep the same base enum in the app
    EnumWatchPref.TextSize.id -> (this + watchType.textSizeOffset()).toUByte()
    else -> this
}

fun UByte.applyOffsetForReceiveFromWatch(prefId: String, watchType: WatchType): UByte = when (prefId) {
    // Apply offset for obelix/etc so we can keep the same base enum in the app
    EnumWatchPref.TextSize.id -> (this - watchType.textSizeOffset()).toUByte().let {
        // Also add check so that we don't write an invalid enum value, e.g. for a new watch type
        if (it > ContentSize.Larger.code) {
            ContentSize.Larger.code
        } else if (it < ContentSize.Smaller.code) {
            ContentSize.Smaller.code
        } else {
            it
        }
    }
    else -> this
}

// These watch types use a different set of enum values (e.g. 1,2,3 instead of 0,1,2). So that we
// can use the same enum and map to all watch models, we offset them when writing to/from the watch.
fun WatchType.textSizeOffset(): UByte = when (this) {
    WatchType.EMERY -> 1u
    WatchType.GABBRO -> 1u
    else -> 0u
}

enum class ContentSize(override val code: UByte, override val displayName: String) : WatchPrefEnum {
    // Note: obelix (and in future other platforms) offset this enum. See textSizeOffset()
    Smaller(0u, "Smaller"),
    Default(1u, "Default"),
    Larger(2u, "Larger"),
}

enum class AlertMask(override val code: UByte, override val displayName: String) : WatchPrefEnum {
    AllOff(0u, "All Off"),
    PhoneCalls(2u, "Phone Calls"),
    Other(4u, "Other"),
    AllOnLegacy(7u, "All On (Legacy)"),
    AllOn(15u, "All On"),
}

enum class QuietTimeShowNotificationsMode(override val code: UByte, override val displayName: String) : WatchPrefEnum {
    Hide(0u, "Hide"),
    Show(1u, "Show"),
}

enum class LegacyVibeIntensityValue(override val code: UByte, override val displayName: String) : WatchPrefEnum {
    Low(0u, "Low"),
    Medium(1u, "Medium"),
    High(2u, "High"),
}

enum class VibeScore(override val code: UByte, override val displayName: String) : WatchPrefEnum {
    Disabled(1u, "Disabled"),
    StandardShortPulseLow(2u, "Standard - Low"),
    StandardLongPulseLow(3u, "Standard - Low"),
    StandardShortPulseHigh(4u, "Standard - High"),
    StandardLongPulseHigh(5u, "Standard - High"),
    Pulse(8u, "Pulse"),
    NudgeNudge(9u, "Nudge Nudge"),
    Jackhammer(10u, "Jackhammer"),
    Reveille(11u, "Reveille"),
    Mario(12u, "Mario"),
    AlarmsLPM(13u, "ALARMS LPM"),
}

enum class EnumWatchPref(
    override val id: String,
    override val displayName: String,
    override val defaultValue: WatchPrefEnum,
    val options: List<WatchPrefEnum>,
    override val isDebugSetting: Boolean = false,
) : WatchPref<WatchPrefEnum> {
    TextSize("textStyle", "Text Size", ContentSize.Default, ContentSize.entries),
    NotificationFilter(
        "mask",
        "Notification Filter",
        AlertMask.AllOn,
        listOf(AlertMask.AllOn, AlertMask.PhoneCalls, AlertMask.AllOff)
    ),
    QuietTimeInterruptions(
        "dndInterruptionsMask",
        "Quiet Time Interruptions",
        AlertMask.AllOff,
        listOf(AlertMask.AllOff, AlertMask.PhoneCalls)
    ),
    QuietTimeShowNotifications(
        "dndShowNotifications",
        "Quiet Time - Show Notifications",
        QuietTimeShowNotificationsMode.Show,
        QuietTimeShowNotificationsMode.entries
    ),
    LegacyVibeIntensity(
        "vibeIntensity",
        "System Vibration Intensity",
        LegacyVibeIntensityValue.High,
        LegacyVibeIntensityValue.entries
    ),
    VibeScoreNotifications(
        "vibeScoreNotifications",
        "Vibration - Notifications",
        VibeScore.NudgeNudge,
        listOf(
            VibeScore.Disabled,
            VibeScore.StandardShortPulseLow,
            VibeScore.StandardShortPulseHigh,
            VibeScore.Pulse,
            VibeScore.NudgeNudge,
            VibeScore.Jackhammer,
            VibeScore.Mario,
        ),
    ),
    VibeScoreCalls(
        "vibeScoreIncomingCalls",
        "Vibration - Incoming Calls",
        VibeScore.Pulse,
        listOf(
            VibeScore.Disabled,
            VibeScore.StandardLongPulseLow,
            VibeScore.StandardLongPulseHigh,
            VibeScore.Pulse,
            VibeScore.NudgeNudge,
            VibeScore.Jackhammer,
            VibeScore.Mario,
        ),
    ),
    VibeScoreAlarms(
        "vibeScoreAlarms", "Vibration - Alarms", VibeScore.Reveille,
        listOf(
            VibeScore.StandardLongPulseLow,
            VibeScore.StandardLongPulseHigh,
            VibeScore.Pulse,
            VibeScore.NudgeNudge,
            VibeScore.Jackhammer,
            VibeScore.Reveille,
            VibeScore.Mario,
        ),
    ),
    ;

    override val type = WatchPrefType.TypeUInt8
    override fun decodeValue(value: String): WatchPrefEnum =
        options.first { it.code == value.toUByte() }

    override fun encodeValue(value: WatchPrefEnum): String = value.code.toString()
}

enum class NumberWatchPref(
    override val id: String,
    override val displayName: String,
    override val defaultValue: Long,
    override val type: WatchPrefType,
    val min: Int,
    val max: Int,
    override val isDebugSetting: Boolean = false,
) : WatchPref<Long> {
    MotionSensitivity(
        id = "motionSensitivity",
        displayName = "Motion Sensitivity",
        defaultValue = 85,
        type = WatchPrefType.TypeUInt8,
        min = 0,
        max = 100,
        isDebugSetting = true,
    ),
    BacklightTimeoutMs(
        id = "lightTimeoutMs",
        displayName = "Backlight Timeout (ms)",
        defaultValue = 3000,
        type = WatchPrefType.TypeUInt32,
        min = 1,
        max = 10000,
    ),
    AmbientLightThreshold(
        id = "lightAmbientThreshold",
        displayName = "Ambient Light Threshold",
        defaultValue = 1,
        type = WatchPrefType.TypeUInt32,
        min = 1,
        max = 4096,
        isDebugSetting = true,
    ),
    DynamicBacklightMinThreshold(
        id = "dynBacklightMinThreshold",
        displayName = "Dynamic Backlight Min Threshold",
        defaultValue = 0,
        type = WatchPrefType.TypeUInt32,
        min = 0,
        max = 4096,
        isDebugSetting = true,
    ),
    DynamicBacklightMaxThreshold(
        id = "dynBacklightMaxThreshold",
        displayName = "Dynamic Backlight Max Threshold",
        defaultValue = 1,
        type = WatchPrefType.TypeUInt32,
        min = 0,
        max = 4096,
        isDebugSetting = true,
    ),
    TimelineQuickViewMinsBefore(
        id = "timelineQuickViewBeforeTimeMin",
        displayName = "Timeline Quick View - minutes before event",
        defaultValue = 10,
        type = WatchPrefType.TypeUInt16,
        min = 0,
        max = 30,
    ),
    NotificationTimeoutMs(
        id = "notifWindowTimeout",
        displayName = "Notification Timeout (ms)",
        defaultValue = 3.minutes.inWholeMilliseconds,
        type = WatchPrefType.TypeUInt32,
        min = 0,
        max = 10.minutes.inWholeMilliseconds.toInt(),
    ),
    ;

    override fun decodeValue(value: String): Long = value.toLong()
    override fun encodeValue(value: Long): String = value.toString()
}

enum class ColorWatchPref(
    override val id: String,
    override val displayName: String,
    override val defaultValue: TimelineColor,
    val availableColors: List<TimelineColor>?,
    override val isDebugSetting: Boolean = false,
) : WatchPref<TimelineColor> {
    // Choosing a color not in the seelction list crashes the watch - don't sync yet
    SettingsMenuHighlightColor(
        "settingsMenuHighlightColor",
        "Settings Menu Highlight Color",
        TimelineColor.CobaltBlue,
        availableColors = listOf(
            TimelineColor.DarkCandyAppleRed,
            TimelineColor.WindsorTan,
            TimelineColor.ArmyGreen,
            TimelineColor.DarkGreen,
            TimelineColor.MidnightGreen,
            TimelineColor.CobaltBlue,
            TimelineColor.DukeBlue,
            TimelineColor.Indigo,
            TimelineColor.Purple,
            TimelineColor.JazzberryJam,
        ),
    ),
    AppMenuHighlightColor(
        "appsMenuHighlightColor",
        "App Menu Highlight Color",
        TimelineColor.VividCerulean,
        availableColors = listOf(
            TimelineColor.SunsetOrange,
            TimelineColor.ChromeYellow,
            TimelineColor.Yellow,
            TimelineColor.Green,
            TimelineColor.Cyan,
            TimelineColor.VividCerulean,
            TimelineColor.VeryLightBlue,
            TimelineColor.LavenderIndigo,
            TimelineColor.Magenta,
            TimelineColor.BrilliantRose,
        ),
    ),
    ;

    override val type = WatchPrefType.TypeColor
    override fun decodeValue(value: String): TimelineColor = TimelineColor.findByName(value)!!
    override fun encodeValue(value: TimelineColor): String = value.identifier
}

/**
 * Not syncing right now:
- watchface (we already know what the default watchface is... or should)
- automaticTimezoneID (we set this every time we send a time message)
- qlSetupOpened
- timelineSettingsOpened
- unitsDistance (see HealthSettingsEntry)
- activityPreferences (see HealthSettingsEntry)
- activityHealthAppOpened
- activityWorkoutAppOpened
- alarmsAppOpened
- hrmPreferences (see HealthSettingsEntry)
- heartRatePreferences (see HealthSettingsEntry)
- workerId (need UI to figure out which apps are eligible)
- dndWeekdaySchedule (need to figure out how to do this)
- dndWeekdayScheduleEnabled
- dndWeekendSchedule (need to figure out how to do this)
- dndWeekendScheduleEnabled
 */

private val logger = Logger.withTag("WatchPrefItem")

fun DbWrite.asWatchPrefItem(params: ValueParams): WatchPrefItem? {
    try {
        val id = key.asByteArray().decodeToString().trimEnd('\u0000')
        val type = WatchPref.from(id)
        if (type == null) {
            logger.w("Unknown watch pref type from blobdb: $id")
            return null
        }
        val strValue = try {
            when (type.type) {
                WatchPrefType.TypeString -> value.asByteArray().decodeToString().trimEnd('\u0000')
                WatchPrefType.TypeUuid -> Uuid.fromByteArray(value.asByteArray()).toString()
                WatchPrefType.TypeUInt8, WatchPrefType.TypeBoolean -> value[0].applyOffsetForReceiveFromWatch(id, params.platform)
                    .toString()

                WatchPrefType.TypeUInt16 -> SUShort(
                    StructMapper(),
                    endianness = Endian.Little
                ).apply {
                    fromBytes(
                        DataBuffer(value)
                    )
                }.get().toString()

                WatchPrefType.TypeUInt32 -> SUInt(
                    StructMapper(),
                    endianness = Endian.Little
                ).apply {
                    fromBytes(
                        DataBuffer(value)
                    )
                }.get().toString()

                WatchPrefType.TypeQuickLaunch -> {
                    val struct = QLMappable().apply { fromBytes(DataBuffer(value)) }
                    val uuid = when (val u = struct.uuid.get()) {
                        NULL_UUID -> null
                        else -> u
                    }
                    val setting = QuickLaunchSetting(
                        enabled = struct.enabled.get(),
                        uuid = uuid,
                    )
                    setting.toJson()
                }

                WatchPrefType.TypeColor -> value.asByteArray()[0].toUByte().toPebbleColor()
                    .asTimelineColor().identifier
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Error decoding watch pref record $id" }
            return null
        }
        return WatchPrefItem(
            id = id,
            value = strValue,
            timestamp = Instant.fromEpochSeconds(timestamp.toLong()).asMillisecond(),
        )
    } catch (e: Exception) {
        logger.d("decoding watch pref record ${e.message}", e)
        return null
    }
}

private fun SFixedList<Attribute>.get(attribute: TimelineAttribute): UByteArray? =
    list.find { it.attributeId.get() == attribute.id }?.content?.get()
