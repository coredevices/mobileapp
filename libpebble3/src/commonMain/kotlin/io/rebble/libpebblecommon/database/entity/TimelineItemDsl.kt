package io.rebble.libpebblecommon.database.entity

import io.rebble.libpebblecommon.database.asMillisecond
import io.rebble.libpebblecommon.packets.blobdb.TimelineAttribute
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import kotlin.time.Instant
import io.rebble.libpebblecommon.util.PebbleColor
import io.rebble.libpebblecommon.util.TimelineAttributeFactory
import kotlin.time.Duration
import kotlin.uuid.Uuid

class ActionBuilder(val actionId: UByte, val type: TimelineItem.Action.Type) {
    private var attributes = listOf<BaseAttribute>()
    fun attributes(block: AttributesListBuilder.() -> Unit) {
        val builder = AttributesListBuilder()
        builder.block()
        attributes = builder.build()
    }

    fun build(): BaseAction {
        return BaseAction(actionId, type, attributes)
    }
}

fun actions(block: ActionsListBuilder.() -> Unit): List<BaseAction> = ActionsListBuilder().apply {
    block()
}.build()

class ActionsListBuilder internal constructor() {
    private val actions = mutableListOf<BaseAction>()
    private var actionId: UByte = 0u

    fun action(type: TimelineItem.Action.Type, block: ActionBuilder.() -> Unit) {
        val builder = ActionBuilder(actionId++, type)
        builder.block()
        actions.add(builder.build())
    }

    internal fun build(): List<BaseAction> {
        return actions
    }
}

fun attributes(block: AttributesListBuilder.() -> Unit): List<BaseAttribute> =
    AttributesListBuilder().apply {
        block()
    }.build()

class AttributesListBuilder internal constructor() {
    private val attributes = mutableListOf<BaseAttribute>()

    fun string(attribute: TimelineAttribute, block: () -> String) {
        attributes.add(BaseAttribute.TextAttribute(attribute, block()))
    }

    fun icon(attribute: TimelineAttribute, block: () -> TimelineIcon) {
        attributes.add(BaseAttribute.IconAttribute(attribute, block()))
    }

    fun stringList(attribute: TimelineAttribute, block: () -> List<String>) {
        attributes.add(BaseAttribute.TextListAttribute(attribute, block()))
    }

    fun uIntList(attribute: TimelineAttribute, block: () -> List<UInt>) {
        attributes.add(BaseAttribute.UIntListAttribute(attribute, block()))
    }

    fun title(block: () -> String) {
        string(TimelineAttribute.Title, block)
    }

    fun subtitle(block: () -> String) {
        string(TimelineAttribute.Subtitle, block)
    }

    fun body(block: () -> String) {
        string(TimelineAttribute.Body, block)
    }

    fun location(block: () -> String) {
        string(TimelineAttribute.LocationName, block)
    }

    fun icon(block: () -> TimelineIcon) {
        icon(TimelineAttribute.Icon, block)
    }

    fun tinyIcon(block: () -> TimelineIcon) {
        icon(TimelineAttribute.TinyIcon, block)
    }

    fun smallIcon(block: () -> TimelineIcon) {
        icon(TimelineAttribute.SmallIcon, block)
    }

    fun largeIcon(block: () -> TimelineIcon) {
        icon(TimelineAttribute.LargeIcon, block)
    }

    fun cannedResponse(block: () -> List<String>) {
        stringList(TimelineAttribute.CannedResponse, block)
    }

    fun sender(block: () -> String) {
        string(TimelineAttribute.Sender, block)
    }

    fun primaryColor(block: () -> PebbleColor) {
        attributes.add(BaseAttribute.ColorAttribute(TimelineAttribute.ForegroundColor, block()))
    }

    fun secondaryColor(block: () -> PebbleColor) {
        attributes.add(BaseAttribute.ColorAttribute(TimelineAttribute.SecondaryColor, block()))
    }

    fun backgroundColor(block: () -> PebbleColor) {
        attributes.add(BaseAttribute.ColorAttribute(TimelineAttribute.BackgroundColor, block()))
    }

    fun lastUpdated(block: () -> Instant) {
        attributes.add(BaseAttribute.UIntAttribute(TimelineAttribute.LastUpdated, block().epochSeconds.toUInt()))
    }

    fun appName(block: () -> String) {
        string(TimelineAttribute.AppName, block)
    }

    fun muteDayOfWeek(block: () -> UByte) {
        attributes.add(BaseAttribute.UByteAttribute(TimelineAttribute.MuteDayOfWeek, block()))
    }

    fun vibrationPattern(block: () -> List<UInt>) {
        uIntList(TimelineAttribute.VibrationPattern, block)
    }

    internal fun build(): List<BaseAttribute> {
        return attributes
    }
}

class FlagsBuilder internal constructor() {
    private val flags = mutableListOf<TimelineItem.Flag>()

    fun isVisible() {
        flags.add(TimelineItem.Flag.IS_VISIBLE)
    }

    fun isFloating() {
        flags.add(TimelineItem.Flag.IS_FLOATING)
    }

    fun isAllDay() {
        flags.add(TimelineItem.Flag.IS_ALL_DAY)
    }

    fun fromWatch() {
        flags.add(TimelineItem.Flag.FROM_WATCH)
    }

    fun fromANCS() {
        flags.add(TimelineItem.Flag.FROM_ANCS)
    }

    fun persistQuickView() {
        flags.add(TimelineItem.Flag.PERSIST_QUICK_VIEW)
    }

    internal fun build(): List<TimelineItem.Flag> {
        return flags.toList()
    }
}

class TimelineNotificationBuilder(
    override val parentId: Uuid,
    override val timestamp: Instant,
) : TimelineItemContainerBuilder() {
    override val duration: Duration = Duration.ZERO
    override var layout: TimelineItem.Layout = TimelineItem.Layout.GenericNotification

    fun build(): TimelineNotification {
        val container = buildContainer()
        return TimelineNotification(itemID, container)
    }
}

class TimelinePinBuilder(
    override val parentId: Uuid,
    override val timestamp: Instant,
) : TimelineItemContainerBuilder() {
    override var duration: Duration = Duration.ZERO
    override var layout: TimelineItem.Layout = TimelineItem.Layout.GenericPin
    var backingId: String? = null

    fun build(): TimelinePin {
        val container = buildContainer()
        return TimelinePin(itemID, container, backingId)
    }
}

class TimelineReminderBuilder(
    override val parentId: Uuid,
    override val timestamp: Instant,
) : TimelineItemContainerBuilder() {
    override val duration: Duration = Duration.ZERO
    override var layout: TimelineItem.Layout = TimelineItem.Layout.GenericReminder

    fun build(): TimelineReminder {
        val container = buildContainer()
        return TimelineReminder(itemID, container)
    }
}

fun buildTimelineNotification(
    parentId: Uuid,
    timestamp: Instant,
    block: TimelineNotificationBuilder.() -> Unit,
): TimelineNotification {
    val builder = TimelineNotificationBuilder(parentId, timestamp)
    builder.block()
    return builder.build()
}

fun buildTimelinePin(
    parentId: Uuid,
    timestamp: Instant,
    block: TimelinePinBuilder.() -> Unit,
): TimelinePin {
    val builder = TimelinePinBuilder(parentId, timestamp)
    builder.block()
    return builder.build()
}

fun buildTimelineReminder(
    parentId: Uuid,
    timestamp: Instant,
    block: TimelineReminderBuilder.() -> Unit,
): TimelineReminder {
    val builder = TimelineReminderBuilder(parentId, timestamp)
    builder.block()
    return builder.build()
}

sealed class TimelineItemContainerBuilder {
    var itemID: Uuid = Uuid.random()
    abstract val parentId: Uuid
    abstract val timestamp: Instant
    abstract val duration: Duration
    private var flags: List<TimelineItem.Flag> = emptyList()
    abstract var layout: TimelineItem.Layout
    private var attributes = listOf<BaseAttribute>()
    private var actions = listOf<BaseAction>()

    fun attributes(block: AttributesListBuilder.() -> Unit) {
        val builder = AttributesListBuilder()
        builder.block()
        attributes = builder.build()
    }

    fun actions(block: ActionsListBuilder.() -> Unit) {
        val builder = ActionsListBuilder()
        builder.block()
        actions = builder.build()
    }

    fun flags(block: FlagsBuilder.() -> Unit) {
        val builder = FlagsBuilder()
        builder.block()
        flags = builder.build()
    }

    protected fun buildContainer(): TimelineItemFields {
        return TimelineItemFields(
            parentId = parentId,
            timestamp = timestamp.asMillisecond(),
            duration = duration.asMillisecond(),
            flags = flags,
            layout = layout,
            attributes = attributes,
            actions = actions,
        )
    }
}
