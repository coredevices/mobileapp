package io.rebble.libpebblecommon.database.entity

import io.rebble.libpebblecommon.database.asMillisecond
import io.rebble.libpebblecommon.packets.blobdb.TimelineActionType
import io.rebble.libpebblecommon.packets.blobdb.TimelineAttribute
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.packets.blobdb.TimelineItemFlag
import io.rebble.libpebblecommon.packets.blobdb.TimelineItemLayout
import kotlin.time.Instant
import io.rebble.libpebblecommon.util.PebbleColor
import io.rebble.libpebblecommon.util.TimelineAttributeFactory
import kotlin.time.Duration
import kotlin.uuid.Uuid

public class ActionBuilder internal constructor(public val actionId: UByte, public val type: TimelineActionType) {
    private var attributes = listOf<BaseAttribute>()
    internal var internalType: String? = null
    internal var pluginAction: PluginActionRef? = null
    public var internalData: String? = null

    internal fun attributes(block: AttributesListBuilder.() -> Unit) {
        val builder = AttributesListBuilder()
        builder.block()
        attributes = builder.build()
    }

    internal fun build(): BaseAction {
        return BaseAction(actionId, type, attributes, internalType, pluginAction, internalData)
    }
}

internal fun actions(block: ActionsListBuilder.() -> Unit): List<BaseAction> = ActionsListBuilder().apply {
    block()
}.build()

public class ActionsListBuilder internal constructor() {
    private val actions = mutableListOf<BaseAction>()
    private var actionId: UByte = 0u

    public fun action(
        type: TimelineActionType,
        internalType: String? = null,
        block: ActionBuilder.() -> Unit,
    ) {
        val builder = ActionBuilder(actionId++, type).apply { this.internalType = internalType }
        builder.block()
        actions.add(builder.build())
    }

    internal fun build(): List<BaseAction> {
        return actions
    }
}

internal fun attributes(block: AttributesListBuilder.() -> Unit): List<BaseAttribute> =
    AttributesListBuilder().apply {
        block()
    }.build()

public class AttributesListBuilder internal constructor() {
    private val attributes = mutableListOf<BaseAttribute>()

    internal fun string(attribute: TimelineAttribute, block: () -> String) {
        attributes.add(BaseAttribute.TextAttribute(attribute, block()))
    }

    internal fun icon(attribute: TimelineAttribute, block: () -> TimelineIcon) {
        attributes.add(BaseAttribute.IconAttribute(attribute, block()))
    }

    internal fun stringList(attribute: TimelineAttribute, block: () -> List<String>) {
        attributes.add(BaseAttribute.TextListAttribute(attribute, block()))
    }

    internal fun uIntList(attribute: TimelineAttribute, block: () -> List<UInt>) {
        attributes.add(BaseAttribute.UIntListAttribute(attribute, block()))
    }

    public fun uByte(attribute: TimelineAttribute, block: () -> UByte) {
        attributes.add(BaseAttribute.UByteAttribute(attribute, block()))
    }

    public fun title(block: () -> String) {
        string(TimelineAttribute.Title, block)
    }

    public fun subtitle(block: () -> String) {
        string(TimelineAttribute.Subtitle, block)
    }

    public fun body(block: () -> String) {
        string(TimelineAttribute.Body, block)
    }

    public fun location(block: () -> String) {
        string(TimelineAttribute.LocationName, block)
    }

    internal fun icon(block: () -> TimelineIcon) {
        icon(TimelineAttribute.Icon, block)
    }

    public fun tinyIcon(block: () -> TimelineIcon) {
        icon(TimelineAttribute.TinyIcon, block)
    }

    internal fun smallIcon(block: () -> TimelineIcon) {
        icon(TimelineAttribute.SmallIcon, block)
    }

    public fun largeIcon(block: () -> TimelineIcon) {
        icon(TimelineAttribute.LargeIcon, block)
    }

    internal fun cannedResponse(block: () -> List<String>) {
        stringList(TimelineAttribute.CannedResponse) { trimCannedResponses(block()) }
    }

    internal fun sender(block: () -> String) {
        string(TimelineAttribute.Sender, block)
    }

    internal fun primaryColor(block: () -> PebbleColor) {
        attributes.add(BaseAttribute.ColorAttribute(TimelineAttribute.ForegroundColor, block()))
    }

    internal fun secondaryColor(block: () -> PebbleColor) {
        attributes.add(BaseAttribute.ColorAttribute(TimelineAttribute.SecondaryColor, block()))
    }

    public fun backgroundColor(block: () -> PebbleColor) {
        attributes.add(BaseAttribute.ColorAttribute(TimelineAttribute.BackgroundColor, block()))
    }

    public fun lastUpdated(block: () -> Instant) {
        attributes.add(BaseAttribute.UIntAttribute(TimelineAttribute.LastUpdated, block().epochSeconds.toUInt()))
    }

    internal fun launchCode(block: () -> UInt) {
        attributes.add(BaseAttribute.UIntAttribute(TimelineAttribute.LaunchCode, block()))
    }

    public fun appName(block: () -> String) {
        string(TimelineAttribute.AppName, block)
    }

    public fun headings(block: () -> List<String>) {
        stringList(TimelineAttribute.Headings, block)
    }

    public fun paragraphs(block: () -> List<String>) {
        stringList(TimelineAttribute.Paragraphs, block)
    }

    internal fun muteDayOfWeek(block: () -> UByte) {
        attributes.add(BaseAttribute.UByteAttribute(TimelineAttribute.MuteDayOfWeek, block()))
    }

    internal fun muteExpiration(block: () -> Instant) {
        attributes.add(BaseAttribute.UIntAttribute(TimelineAttribute.MuteExpiration, block().epochSeconds.toUInt()))
    }

    public fun vibrationPattern(block: () -> List<UInt>) {
        uIntList(TimelineAttribute.VibrationPattern, block)
    }

    internal fun notificationFilteringRules(block: () -> UByteArray) {
        attributes.add(BaseAttribute.UByteArrayAttribute(TimelineAttribute.NotificationFilteringRules, block()))
    }

    internal fun build(): List<BaseAttribute> {
        return attributes
    }
}

internal class FlagsBuilder internal constructor() {
    private val flags = mutableListOf<TimelineItemFlag>()

    internal fun isVisible() {
        flags.add(TimelineItemFlag.IS_VISIBLE)
    }

    internal fun isFloating() {
        flags.add(TimelineItemFlag.IS_FLOATING)
    }

    internal fun isAllDay() {
        flags.add(TimelineItemFlag.IS_ALL_DAY)
    }

    internal fun fromWatch() {
        flags.add(TimelineItemFlag.FROM_WATCH)
    }

    internal fun fromANCS() {
        flags.add(TimelineItemFlag.FROM_ANCS)
    }

    internal fun persistQuickView() {
        flags.add(TimelineItemFlag.PERSIST_QUICK_VIEW)
    }

    internal fun silent() {
        flags.add(TimelineItemFlag.SILENT)
    }

    internal fun build(): List<TimelineItemFlag> {
        return flags.toList()
    }
}

public class TimelineNotificationBuilder internal constructor(
    override val parentId: Uuid,
    override val timestamp: Instant,
) : TimelineItemContainerBuilder() {
    override val duration: Duration = Duration.ZERO
    override var layout: TimelineItemLayout = TimelineItemLayout.GenericNotification

    internal fun build(): TimelineNotification {
        val container = buildContainer()
        return TimelineNotification(itemID, container)
    }
}

public class TimelinePinBuilder internal constructor(
    override val parentId: Uuid,
    override val timestamp: Instant,
) : TimelineItemContainerBuilder() {
    override var duration: Duration = Duration.ZERO
    override var layout: TimelineItemLayout = TimelineItemLayout.GenericPin
    internal var backingId: String? = null

    internal fun build(): TimelinePin {
        val container = buildContainer()
        return TimelinePin(itemID, container, backingId)
    }
}

public class TimelineReminderBuilder internal constructor(
    override val parentId: Uuid,
    override val timestamp: Instant,
) : TimelineItemContainerBuilder() {
    override val duration: Duration = Duration.ZERO
    override var layout: TimelineItemLayout = TimelineItemLayout.GenericReminder

    internal fun build(): TimelineReminder {
        val container = buildContainer()
        return TimelineReminder(itemID, container)
    }
}

public fun buildTimelineNotification(
    parentId: Uuid,
    timestamp: Instant,
    block: TimelineNotificationBuilder.() -> Unit,
): TimelineNotification {
    val builder = TimelineNotificationBuilder(parentId, timestamp)
    builder.block()
    return builder.build()
}

public fun buildTimelinePin(
    parentId: Uuid,
    timestamp: Instant,
    block: TimelinePinBuilder.() -> Unit,
): TimelinePin {
    val builder = TimelinePinBuilder(parentId, timestamp)
    builder.block()
    return builder.build()
}

public fun buildTimelineReminder(
    parentId: Uuid,
    timestamp: Instant,
    block: TimelineReminderBuilder.() -> Unit,
): TimelineReminder {
    val builder = TimelineReminderBuilder(parentId, timestamp)
    builder.block()
    return builder.build()
}

public sealed class TimelineItemContainerBuilder {
    public var itemID: Uuid = Uuid.random()
    internal abstract val parentId: Uuid
    internal abstract val timestamp: Instant
    public abstract val duration: Duration
    private var flags: List<TimelineItemFlag> = emptyList()
    public abstract var layout: TimelineItemLayout
    private var attributes = listOf<BaseAttribute>()
    private var actions = listOf<BaseAction>()

    public fun attributes(block: AttributesListBuilder.() -> Unit) {
        val builder = AttributesListBuilder()
        builder.block()
        attributes = builder.build()
    }

    public fun actions(block: ActionsListBuilder.() -> Unit) {
        val builder = ActionsListBuilder()
        builder.block()
        actions = builder.build()
    }

    internal fun flags(block: FlagsBuilder.() -> Unit) {
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

private const val MAX_CANNED_RESPONSE_BYTES = 512

/**
 * Trim the list to fit within the firmware's 512-byte serialized limit
 * (strings joined by NUL, encoded as UTF-8). Drops items from the end first.
 */
private fun trimCannedResponses(responses: List<String>): List<String> {
    var result = responses
    while (result.isNotEmpty()) {
        val serializedSize = result.joinToString("\u0000").encodeToByteArray().size
        if (serializedSize <= MAX_CANNED_RESPONSE_BYTES) break
        result = result.dropLast(1)
    }
    return result
}
