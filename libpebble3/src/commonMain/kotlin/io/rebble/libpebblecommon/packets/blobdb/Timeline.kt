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

class TimelineItem(
    itemId: Uuid = Uuid.NIL,
    parentId: Uuid = Uuid.NIL,
    timestampSecs: UInt = 0u,
    duration: UShort = 0u,
    type: Type = Type.Notification,
    flags: UShort = 0u,
    layout: Layout = Layout.GenericNotification,
    attributes: List<Attribute> = emptyList(),
    actions: List<Action> = emptyList()
) : StructMappable() {
    enum class Type(val value: UByte) {
        Notification(1u),
        Pin(2u),
        Reminder(3u);

        companion object {
            fun fromValue(value: UByte): Type {
                return entries.firstOrNull { it.value == value }
                    ?: error("Unknown timeline item type: $value")
            }
        }
    }

    enum class Layout(val value: UByte, val code: String) {
        GenericPin(1u, "genericPin"),
        CalendarPin(2u, "calendarPin"),
        GenericReminder(3u, "genericReminder"),
        GenericNotification(4u, "genericNotification"),
        CommNotification(5u, "commNotification"),
        WeatherPin(6u, "weatherPin"),
        SportsPin(7u, "sportsPin");

        companion object {
            fun fromValue(value: UByte): Layout {
                return entries.firstOrNull { it.value == value }
                    ?: error("Unknown timeline item layout: $value")
            }

            fun fromCode(code: String): Layout? {
                return entries.firstOrNull { it.code == code }
            }
        }
    }

    val itemId = SUUID(m, itemId)
    val parentId = SUUID(m, parentId)

    /**
     * Timeline pin timestamp in unix time
     */
    val timestampSecs = SUInt(m, timestampSecs, endianness = Endian.Little)

    /**
     * Duration of the pin in minutes
     */
    val duration = SUShort(m, duration, endianness = Endian.Little)

    /**
     * Serialization of [Type]. Use [Type.value].
     */
    val type = SUByte(m, type.value)

    /**
     * Serialization of [Flag] entries. Use [Flag.makeFlags].
     */
    val flags = SUShort(m, flags, endianness = Endian.Little)

    val layout = SUByte(m, layout.value)
    val dataLength = SUShort(m, endianness = Endian.Little)
    val attrCount = SUByte(m, attributes.size.toUByte())
    val actionCount = SUByte(m, actions.size.toUByte())
    val attributes = SFixedList(m, attrCount.get().toInt(), attributes) {
        Attribute(0u, ubyteArrayOf())
    }.apply {
        linkWithCount(attrCount)
    }
    val actions = SFixedList(m, actionCount.get().toInt(), actions) {
        Action(
            0u,
            Action.Type.Empty,
            emptyList()
        )
    }.apply {
        linkWithCount(actionCount)
    }

    init {
        dataLength.set((this.attributes.toBytes().size + this.actions.toBytes().size).toUShort())
    }

    class Action(actionID: UByte, type: Type, attributes: List<Attribute>) : Mappable() {
        val m = StructMapper()

        enum class Type(val value: UByte) {
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

            companion object {
                fun fromValue(value: UByte): Type {
                    return entries.firstOrNull { it.value == value }
                        ?: error("Unknown action type: $value")
                }
            }
        }

        val actionID = SUByte(m, actionID)
        val type = SUByte(m, type.value)
        val attributeCount = SUByte(m, attributes.size.toUByte())
        val attributes = SFixedList(m, attributeCount.get().toInt(), attributes) {
            Attribute(
                0u,
                ubyteArrayOf()
            )
        }.apply {
            linkWithCount(attributeCount)
        }

        override fun toBytes(): UByteArray = m.toBytes()

        override fun fromBytes(bytes: DataBuffer) = m.fromBytes(bytes)

        override val size: Int
            get() = m.size
    }

    class Attribute(contentEndianness: Endian = Endian.Unspecified) : StructMappable() {
        val attributeId = SUByte(m)
        val length = SUShort(m, endianness = Endian.Little)
        val content = SBytes(m, 0, endianness = contentEndianness)

        constructor(
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

    @Serializable
    enum class Flag(val value: Int) {
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
         * Marks a notification as read (dismisses if visible, and removes actions).
         */
        STATE_READ(12),
        ;

        companion object {
            fun makeFlags(flags: List<Flag>): UShort {
                var short: UShort = 0u

                for (flag in flags) {
                    short = (1u shl flag.value).toUShort() or short
                }

                return short
            }
        }
    }
}

open class TimelineAction(message: Message) : PebblePacket(endpoint) {
    val command = SUByte(m, message.value)

    enum class Message(val value: UByte) {
        InvokeAction(0x02u),
        ActionResponse(0x11u)
    }

    class InvokeAction(
        itemID: Uuid = Uuid.NIL,
        actionID: UByte = 0u,
        attributes: List<Attribute> = listOf()
    ) : TimelineAction(
        Message.InvokeAction
    ) {
        val itemID = SUUID(m, itemID)
        val actionID = SUByte(m, actionID)
        val numAttributes = SUByte(m, attributes.size.toUByte())
        val attributes = SFixedList(m, attributes.size, attributes, ::Attribute)

        init {
            this.attributes.linkWithCount(numAttributes)
        }
    }

    class ActionResponse(
        itemID: Uuid = Uuid.NIL,
        response: UByte = 0u,
        attributes: List<Attribute> = listOf()
    ) : TimelineAction(
        Message.ActionResponse
    ) {
        val itemID = SUUID(m, itemID)
        val response = SUByte(m, response)
        val numAttributes = SUByte(m, attributes.size.toUByte())
        val attributes = SFixedList(m, attributes.size, attributes, ::Attribute)
    }

    companion object {
        val endpoint = ProtocolEndpoint.TIMELINE_ACTIONS
    }
}

enum class TimelineAttribute(val id: UByte, val maxLength: Int = -1) {
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
    RankAway(0x0Fu),
    RankHome(0x10u),
    NameAway(0x11u),
    NameHome(0x12u),
    RecordAway(0x13u),
    RecordHome(0x14u),
    ScoreAway(0x15u),
    ScoreHome(0x16u),
    SportsGameState(0x17u),
    Broadcaster(0x18u),
    Headings(0x19u, 128),
    Paragraphs(0x1Au, 1024),
    ForegroundColor(0x1Bu),
    BackgroundColor(0x1Cu),
    SecondaryColor(0x1Du),
    AppName(30u),
    DisplayRecurring(0x1Fu),
    ShortSubtitle(0x24u),
    Timestamp(0x25u),
    DisplayTime(0x26u),
    MuteDayOfWeek(40u),
    MuteExpiration(50u),
    SubtitleTemplateString(0x2Fu, 150),
    Icon(0x30u),
    VibrationPattern(0x31u),
    ;

    companion object {
        fun fromByte(value: UByte): TimelineAttribute? = entries.firstOrNull { it.id == value }
    }
}

fun timelinePacketsRegister() {
    PacketRegistry.register(
        TimelineAction.endpoint,
        TimelineAction.Message.InvokeAction.value
    ) { TimelineAction.InvokeAction() }
    PacketRegistry.register(
        TimelineAction.endpoint,
        TimelineAction.Message.ActionResponse.value
    ) { TimelineAction.ActionResponse() }
}