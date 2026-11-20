package io.rebble.libpebblecommon.packets.blobdb

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem.Attribute
import io.rebble.libpebblecommon.protocolhelpers.PacketRegistry
import io.rebble.libpebblecommon.protocolhelpers.PebblePacket
import io.rebble.libpebblecommon.protocolhelpers.ProtocolEndpoint
import io.rebble.libpebblecommon.structmapper.Mappable
import io.rebble.libpebblecommon.structmapper.SBytes
import io.rebble.libpebblecommon.structmapper.SFixedList
import io.rebble.libpebblecommon.structmapper.SUByte
import io.rebble.libpebblecommon.structmapper.SUInt
import io.rebble.libpebblecommon.structmapper.SUShort
import io.rebble.libpebblecommon.structmapper.SUUID
import io.rebble.libpebblecommon.structmapper.StructMappable
import io.rebble.libpebblecommon.structmapper.StructMapper
import io.rebble.libpebblecommon.util.DataBuffer
import io.rebble.libpebblecommon.util.Endian
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

public class TimelineItem internal constructor(
    itemId: Uuid = Uuid.NIL,
    parentId: Uuid = Uuid.NIL,
    timestampSecs: UInt = 0u,
    duration: UShort = 0u,
    type: TimelineItemType = TimelineItemType.Notification,
    flags: UShort = 0u,
    layout: TimelineItemLayout = TimelineItemLayout.GenericNotification,
    attributes: List<Attribute> = emptyList(),
    actions: List<Action> = emptyList()
) : StructMappable() {
    internal val itemId: SUUID = SUUID(m, itemId)
    internal val parentId: SUUID = SUUID(m, parentId)

    /**
     * Timeline pin timestamp in unix time
     */
    internal val timestampSecs: SUInt = SUInt(m, timestampSecs, endianness = Endian.Little)

    /**
     * Duration of the pin in minutes
     */
    internal val duration: SUShort = SUShort(m, duration, endianness = Endian.Little)

    /**
     * Serialization of [TimelineItemType]. Use [TimelineItemType.value].
     */
    internal val type: SUByte = SUByte(m, type.value)

    /**
     * Serialization of [TimelineItemFlag] entries. Use [TimelineItemFlag.makeFlags].
     */
    internal val flags: SUShort = SUShort(m, flags, endianness = Endian.Little)

    internal val layout: SUByte = SUByte(m, layout.value)
    internal val dataLength: SUShort = SUShort(m, endianness = Endian.Little)
    internal val attrCount: SUByte = SUByte(m, attributes.size.toUByte())
    internal val actionCount: SUByte = SUByte(m, actions.size.toUByte())
    internal val attributes: SFixedList<TimelineItem.Attribute> = SFixedList(m, attrCount.get().toInt(), attributes) {
        Attribute(0u, ubyteArrayOf())
    }.apply {
        linkWithCount(attrCount)
    }
    internal val actions: SFixedList<TimelineItem.Action> = SFixedList(m, actionCount.get().toInt(), actions) {
        Action(
            0u,
            TimelineActionType.Empty,
            emptyList()
        )
    }.apply {
        linkWithCount(actionCount)
    }

    init {
        dataLength.set((this.attributes.toBytes().size + this.actions.toBytes().size).toUShort())
    }

    internal class Action internal constructor(actionID: UByte, type: TimelineActionType, attributes: List<Attribute>) : Mappable() {
        internal val m: StructMapper = StructMapper()

        internal val actionID: SUByte = SUByte(m, actionID)
        internal val type: SUByte = SUByte(m, type.value)
        internal val attributeCount: SUByte = SUByte(m, attributes.size.toUByte())
        internal val attributes: SFixedList<TimelineItem.Attribute> = SFixedList(m, attributeCount.get().toInt(), attributes) {
            Attribute(
                0u,
                ubyteArrayOf()
            )
        }.apply {
            linkWithCount(attributeCount)
        }

        override fun toBytes(): UByteArray = m.toBytes()

        override fun fromBytes(bytes: DataBuffer): Unit = m.fromBytes(bytes)

        override val size: Int
            get() = m.size
    }

    public class Attribute internal constructor(contentEndianness: Endian = Endian.Unspecified) : StructMappable() {
        internal val attributeId: SUByte = SUByte(m)
        internal val length: SUShort = SUShort(m, endianness = Endian.Little)
        internal val content: SBytes = SBytes(m, 0, endianness = contentEndianness)

        internal constructor(
            attributeId: UByte,
            content: UByteArray,
            contentEndianness: Endian = Endian.Unspecified
        ) : this(contentEndianness) {
            this.attributeId.set(attributeId)

            this.length.set(content.size.toUShort())

            this.content.set(content)
        }

        init {
            content.linkWithSize(length)
        }
    }
}

public enum class TimelineItemType(public val value: UByte) {
    Notification(1u),
    Pin(2u),
    Reminder(3u);

    public companion object {
        public fun fromValue(value: UByte): TimelineItemType {
            return entries.firstOrNull { it.value == value }
                ?: error("Unknown timeline item type: $value")
        }
    }
}

public enum class TimelineItemLayout(public val value: UByte, public val code: String) {
    GenericPin(1u, "genericPin"),
    CalendarPin(2u, "calendarPin"),
    GenericReminder(3u, "genericReminder"),
    GenericNotification(4u, "genericNotification"),
    CommNotification(5u, "commNotification"),
    WeatherPin(6u, "weatherPin"),
    SportsPin(7u, "sportsPin");

    public companion object {
        public fun fromValue(value: UByte): TimelineItemLayout {
            return entries.firstOrNull { it.value == value }
                ?: error("Unknown timeline item layout: $value")
        }

        public fun fromCode(code: String): TimelineItemLayout? {
            return entries.firstOrNull { it.code == code }
        }
    }
}

public enum class TimelineActionType(public val value: UByte) {
    AncsDismiss(0x01u),
    Generic(0x02u),
    Response(0x03u),
    Dismiss(0x04u),
    HTTP(0x05u),
    Snooze(0x06u),
    OpenWatchapp(0x07u),
    Empty(0x08u),
    Remove(0x09u),
    OpenPin(0x0au);

    public companion object {
        public fun fromValue(value: UByte): TimelineActionType {
            return entries.firstOrNull { it.value == value }
                ?: error("Unknown action type: $value")
        }
    }
}

@Serializable
public enum class TimelineItemFlag(public val value: Int) {
    /**
     * ???
     *
     * Name suggests that setting this to false would hide the pin on the watch,
     * but it does not seem to do anything
     */
    IS_VISIBLE(0),

    /**
     * When set, pin is always displayed in UTC timezone on the watch
     */
    IS_FLOATING(1),

    /**
     * Whether pin spans throughout the whole day
     */
    IS_ALL_DAY(2),

    FROM_WATCH(3),
    FROM_ANCS(4),

    /**
     * When set, quick view will be displayed on the watchface when event in progress.
     */
    PERSIST_QUICK_VIEW(5),

    /**
     * Notification was silenced on the phone (e.g. Do Not Disturb). The watch delivers it
     * quietly when the notifRespectPhoneSilence pref is on.
     */
    SILENT(6),

    /**
     * Marks a notification as read (dismisses if visible, and removes actions).
     */
    STATE_READ(12),
    ;

    public companion object {
        internal fun makeFlags(flags: List<TimelineItemFlag>): UShort {
            var short: UShort = 0u

            for (flag in flags) {
                short = (1u shl flag.value).toUShort() or short
            }

            return short
        }
    }
}

internal open class TimelineAction internal constructor(message: Message) : PebblePacket(endpoint) {
    internal val command: SUByte = SUByte(m, message.value)

    enum class Message(val value: UByte) {
        InvokeAction(0x02u),
        DisplayedItem(0x04u),
        ActionResponse(0x11u)
    }

    internal class InvokeAction internal constructor(
        itemID: Uuid = Uuid.NIL,
        actionID: UByte = 0u,
        attributes: List<Attribute> = listOf()
    ) : TimelineAction(
        Message.InvokeAction
    ) {
        internal val itemID: SUUID = SUUID(m, itemID)
        internal val actionID: SUByte = SUByte(m, actionID)
        internal val numAttributes: SUByte = SUByte(m, attributes.size.toUByte())
        internal val attributes: SFixedList<TimelineItem.Attribute> = SFixedList(m, attributes.size, attributes, ::Attribute)

        init {
            this.attributes.linkWithCount(numAttributes)
        }
    }

    internal class DisplayedItem internal constructor(itemID: Uuid = NOTHING_DISPLAYED) : TimelineAction(Message.DisplayedItem) {
        internal val itemID: SUUID = SUUID(m, itemID)
    }

    internal class ActionResponse internal constructor(
        itemID: Uuid = Uuid.NIL,
        response: UByte = 0u,
        attributes: List<Attribute> = listOf()
    ) : TimelineAction(
        Message.ActionResponse
    ) {
        internal val itemID: SUUID = SUUID(m, itemID)
        internal val response: SUByte = SUByte(m, response)
        internal val numAttributes: SUByte = SUByte(m, attributes.size.toUByte())
        internal val attributes: SFixedList<TimelineItem.Attribute> = SFixedList(m, attributes.size, attributes, ::Attribute)
    }

    companion object {
        internal val endpoint: ProtocolEndpoint = ProtocolEndpoint.TIMELINE_ACTIONS
        internal val NOTHING_DISPLAYED: Uuid = Uuid.fromByteArray(ByteArray(16) { 0xFF.toByte() })
    }
}

public enum class TimelineAttribute(public val id: UByte, public val maxLength: Int = -1) {
    Title(0x01u, 64),
    Subtitle(0x02u, 64),
    Body(0x03u, 512),
    TinyIcon(0x04u),
    SmallIcon(0x05u),
    LargeIcon(0x06u),
    ANCSAction(0x07u),
    CannedResponse(0x08u, 512),
    ShortTitle(0x09u, 64),
    LocationName(0x0Bu, 64),
    Sender(0x0Cu, 64),
    LaunchCode(0x0Du),
    LastUpdated(0x0Eu),
    RankAway(0x0Fu, 64),
    RankHome(0x10u, 64),
    NameAway(0x11u, 64),
    NameHome(0x12u, 64),
    RecordAway(0x13u, 64),
    RecordHome(0x14u, 64),
    ScoreAway(0x15u, 64),
    ScoreHome(0x16u, 64),
    SportsGameState(0x17u),
    Broadcaster(0x18u, 64),
    Headings(0x19u, 128),
    Paragraphs(0x1Au, 1024),
    ForegroundColor(0x1Bu),
    BackgroundColor(0x1Cu),
    SecondaryColor(0x1Du),
    AppName(30u, 40),
    DisplayRecurring(0x1Fu),
    ShortSubtitle(0x24u, 64),
    Timestamp(0x25u),
    DisplayTime(0x26u),
    MuteDayOfWeek(40u),
    MuteExpiration(50u),
    SubtitleTemplateString(0x2Fu, 150),
    Icon(0x30u),
    VibrationPattern(0x31u),
    NotificationFilteringRules(0x33u, 1024),

    /** Height/width of the image the phone holds for this item, in sixteenths. */
    ImageAspectRatio(0x34u),

    /** Kind of weather pin, so the watch can show a translated title: 1 = sunrise, 2 = sunset. */
    WeatherPinKind(0x35u),
    ;

    public companion object {
        internal fun fromByte(value: UByte): TimelineAttribute? = entries.firstOrNull { it.id == value }
    }
}

internal fun timelinePacketsRegister() {
    PacketRegistry.register(
        TimelineAction.endpoint,
        TimelineAction.Message.InvokeAction.value
    ) { TimelineAction.InvokeAction() }
    PacketRegistry.register(
        TimelineAction.endpoint,
        TimelineAction.Message.DisplayedItem.value
    ) { TimelineAction.DisplayedItem() }
    PacketRegistry.register(
        TimelineAction.endpoint,
        TimelineAction.Message.ActionResponse.value
    ) { TimelineAction.ActionResponse() }
}