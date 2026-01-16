package io.rebble.libpebblecommon.database.entity

import androidx.room.Embedded
import coredev.BlobDatabase
import coredev.GenerateRoomEntity
import io.rebble.libpebblecommon.database.MillisecondDuration
import io.rebble.libpebblecommon.database.MillisecondInstant
import io.rebble.libpebblecommon.database.dao.BlobDbItem
import io.rebble.libpebblecommon.database.dao.ValueParams
import io.rebble.libpebblecommon.packets.ProtocolCapsFlag
import io.rebble.libpebblecommon.packets.blobdb.TimelineAttribute
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem.Action.Type
import io.rebble.libpebblecommon.structmapper.SUUID
import io.rebble.libpebblecommon.structmapper.StructMapper
import io.rebble.libpebblecommon.util.PebbleColor
import io.rebble.libpebblecommon.util.TimelineAttributeFactory.createStringListAttribute
import io.rebble.libpebblecommon.util.TimelineAttributeFactory.createTextAttribute
import io.rebble.libpebblecommon.util.TimelineAttributeFactory.createUByteAttribute
import io.rebble.libpebblecommon.util.TimelineAttributeFactory.createUIntAttribute
import io.rebble.libpebblecommon.util.TimelineAttributeFactory.createUIntListAttribute
import io.rebble.libpebblecommon.util.toProtocolNumber
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@GenerateRoomEntity(
    primaryKey = "itemId",
    databaseId = BlobDatabase.Notification,
    onlyInsertAfter = true,
    windowAfterSecs = 1 * 60 * 60 * 24, // 1 day
    windowBeforeSecs = -1,
    /** Watch cleans these up itself - never send deletions */
    sendDeletions = false,
)
data class TimelineNotification(
    override val itemId: Uuid,
    @Embedded
    override val content: TimelineItemFields,

    ) : DbTimelineItem {
    override fun type() = TimelineItem.Type.Notification
}

@GenerateRoomEntity(
    primaryKey = "itemId",
    databaseId = BlobDatabase.Pin,
    windowBeforeSecs = 5 * 60 * 60 * 24, // 3 days
    windowAfterSecs = 1 * 60 * 60 * 24, // 1 day
    onlyInsertAfter = false,
    sendDeletions = true,
)
data class TimelinePin(
    override val itemId: Uuid,
    @Embedded
    override val content: TimelineItemFields,
    val backingId: String?,
) : DbTimelineItem {
    override fun type() = TimelineItem.Type.Pin
}

@GenerateRoomEntity(
    primaryKey = "itemId",
    databaseId = BlobDatabase.Reminder,
    windowBeforeSecs = 5 * 60 * 60 * 24, // 3 days
    windowAfterSecs = 1 * 60 * 60 * 24, // 1 day
    onlyInsertAfter = false,
    sendDeletions = true,
)
data class TimelineReminder(
    override val itemId: Uuid,
    @Embedded
    override val content: TimelineItemFields,
) : DbTimelineItem {
    override fun type() = TimelineItem.Type.Reminder
}

@Serializable
sealed class BaseAttribute {
    abstract val attribute: TimelineAttribute

    abstract fun asAttribute(): TimelineItem.Attribute

    @Serializable
    data class TextAttribute(
        override val attribute: TimelineAttribute,
        val text: String,
    ) : BaseAttribute() {
        override fun asAttribute(): TimelineItem.Attribute = createTextAttribute(attribute, text)
    }

    @Serializable
    data class TextListAttribute(
        override val attribute: TimelineAttribute,
        val text: List<String>,
    ) : BaseAttribute() {
        override fun asAttribute(): TimelineItem.Attribute =
            createStringListAttribute(attribute, text)
    }

    @Serializable
    data class IconAttribute(
        override val attribute: TimelineAttribute,
        val icon: TimelineIcon,
    ) : BaseAttribute() {
        override fun asAttribute(): TimelineItem.Attribute = createUIntAttribute(attribute, icon.id)
    }

    @Serializable
    data class ColorAttribute(
        override val attribute: TimelineAttribute,
        val color: PebbleColor,
    ) : BaseAttribute() {
        override fun asAttribute(): TimelineItem.Attribute =
            createUByteAttribute(attribute, color.toProtocolNumber())
    }

    @Serializable
    data class UIntListAttribute(
        override val attribute: TimelineAttribute,
        val list: List<UInt>,
    ) : BaseAttribute() {
        override fun asAttribute(): TimelineItem.Attribute =
            createUIntListAttribute(attribute, list)
    }

    @Serializable
    data class UIntAttribute(
        override val attribute: TimelineAttribute,
        val value: UInt,
    ) : BaseAttribute() {
        override fun asAttribute(): TimelineItem.Attribute =
            createUIntAttribute(attribute, value)
    }

    @Serializable
    data class UByteAttribute(
        override val attribute: TimelineAttribute,
        val value: UByte,
    ) : BaseAttribute() {
        override fun asAttribute(): TimelineItem.Attribute =
            createUByteAttribute(attribute, value)
    }
}

@Serializable
data class BaseAction(
    val actionID: UByte,
    val type: Type,
    val attributes: List<BaseAttribute>,
) {
    fun asAction(): TimelineItem.Action =
        TimelineItem.Action(actionID, type, attributes.map { it.asAttribute() })
}

data class TimelineItemFields(
    val parentId: Uuid,
    val timestamp: MillisecondInstant,
    val duration: MillisecondDuration,
    val flags: List<TimelineItem.Flag>,
    val layout: TimelineItem.Layout,
    val attributes: List<BaseAttribute>,
    val actions: List<BaseAction>,
)

interface DbTimelineItem : BlobDbItem {
    val itemId: Uuid
    val content: TimelineItemFields
    fun type(): TimelineItem.Type

    override fun recordHashCode(): Int = content.hashCode()
    override fun key(): UByteArray = SUUID(StructMapper(), itemId).toBytes()
    override fun value(params: ValueParams): UByteArray? {
        val item = TimelineItem(
            itemId = itemId,
            parentId = content.parentId,
            timestampSecs = content.timestamp.instant.epochSeconds.toUInt(),
            duration = content.duration.duration.inWholeMinutes.toUShort(),
            type = type(),
            flags = TimelineItem.Flag.makeFlags(content.flags),
            layout = content.layout,
            attributes = content.attributes.filterNot {
                it.attribute == TimelineAttribute.VibrationPattern && !params.capabilities.contains(
                    ProtocolCapsFlag.SupportsCustomVibePatterns)
            }.map { it.asAttribute() },
            actions = content.actions.map { it.asAction() },
        )
        return item.toBytes()
    }
}
