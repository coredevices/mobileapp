package io.rebble.libpebblecommon.js

import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.toLowerCase
import co.touchlab.kermit.Logger
import com.eygraber.uri.Uri
import io.rebble.libpebblecommon.WatchConfigFlow
import io.rebble.libpebblecommon.database.dao.TimelinePinRealDao
import io.rebble.libpebblecommon.database.dao.TimelineReminderRealDao
import io.rebble.libpebblecommon.database.entity.AttributesListBuilder
import io.rebble.libpebblecommon.database.entity.TimelinePin
import io.rebble.libpebblecommon.database.entity.TimelineReminder
import io.rebble.libpebblecommon.database.entity.buildTimelinePin
import io.rebble.libpebblecommon.database.entity.buildTimelineReminder
import io.rebble.libpebblecommon.packets.blobdb.TimelineAttribute
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem.Layout.Companion.fromCode
import io.rebble.libpebblecommon.util.PebbleColor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val logger = Logger.withTag("RemoteTimelineEmulator")

class RemoteTimelineEmulator(
    private val watchConfigFlow: WatchConfigFlow,
    private val json: Json,
    private val timelinePinRealDao: TimelinePinRealDao,
    private val timelineReminderRealDao: TimelineReminderRealDao,
) {
    fun shouldIntercept(url: String): Boolean {
        if (!watchConfigFlow.value.emulateRemoteTimeline) {
            return false
        }
        val uri = Uri.parseOrNull(url)
        if (uri?.authority == null) {
            return false
        }
//        logger.v { "shouldIntercept: uri=$uri path=${uri.path}" }
        return TIMELINE_API_AUTHORITIES.any { it.equals(uri.authority, ignoreCase = true) }
                && uri.path?.toLowerCase(Locale.current)?.startsWith("/v1/user/pins") == true
    }

    suspend fun onIntercepted(url: String, method: String, body: String, appUuid: Uuid): String {
        if (!watchConfigFlow.value.emulateRemoteTimeline) {
            logger.w { "Shouldn't have received onIntercepted if we aren't configured to intercept?" }
            return ""
        }
        val uri = Uri.parseOrNull(url)
        if (uri?.authority == null) {
            return ""
        }
//        logger.v { "onIntercepted: uri=$uri method=$method body=$body" }
        if (method == "PUT" || method == "POST") {
            logger.v { "onIntercepted: PUT/POST; inserting pin" }
            insertPin(body, appUuid)
        } else if (method == "DELETE") {
            val pinIdentifer = uri.path?.substringAfter("/user/pins/")
            if (pinIdentifer == null) {
                logger.w { "Unknown DELETE path: ${uri.path}" }
            } else {
                logger.v { "onIntercepted: DELETE; deleting pin" }
                deletePin(appUuid = appUuid, pinIdentifier = pinIdentifer)
            }
        }
        return ""
    }

    suspend fun insertPin(pinJson: String, appUuid: Uuid) {
//        logger.v { "insertPin: pinJson=$pinJson" }
        val pin = try {
            json.decodeFromString(TimelinePinJson.serializer(), pinJson)
        } catch (e: SerializationException) {
            logger.w(e) { "Failed to parse pin JSON" }
            return
        }
//        logger.v { "insertPin: pin=$pin" }
        val existingPin = timelinePinRealDao.getPinsForWatchapp(appUuid).find { it.backingId == pin.id }
        val pinUuid = existingPin?.itemId ?: Uuid.random()
        val timelinePin = asPin(jsonPin = pin, appUuid = appUuid, pinUuid = pinUuid)
//        logger.v { "insertPin: timelinePin=$timelinePin" }
        if (timelinePin == null) {
            logger.w { "insert pin == null" }
            return
        }
        // TODO create/update notifications
        logger.v { "inserting pin ${timelinePin.itemId} (${pin.id})" }
        timelinePinRealDao.insertOrReplace(timelinePin)
        timelineReminderRealDao.markForDeletionByParentId(timelinePin.itemId)
        pin.reminders?.forEach { reminder ->
            val timelineReminder = asReminder(jsonReminder = reminder, pinUuid = pinUuid)
            timelineReminder?.let { timelineReminderRealDao.insertOrReplace(it) }
        }
    }

    suspend fun deletePin(appUuid: Uuid, pinIdentifier: String) {
        logger.v { "deletePin: pinIdentifier=$pinIdentifier" }
        val existingPin = timelinePinRealDao.getPinsForWatchapp(appUuid).find { it.backingId == pinIdentifier }
        if (existingPin == null) {
            logger.i { "deletePin: Unknown pin identifier: $pinIdentifier" }
            return
        }
        timelinePinRealDao.markForDeletionWithReminders(existingPin.itemId, timelineReminderRealDao)
    }

    companion object {
        private val TIMELINE_API_AUTHORITIES = setOf(
            "timeline-api.rebble.io",
            "timeline-api.getpebble.com",
        )
    }
}

private fun asReminder(jsonReminder: TimelineReminderJson, pinUuid: Uuid): TimelineReminder? {
    val timelineLayout = fromCode(jsonReminder.layout.type)
    if (timelineLayout == null) {
        logger.w { "Unknown layout type: ${jsonReminder.layout.type}" }
        return null
    }
    return buildTimelineReminder(
        parentId = pinUuid,
        timestamp = jsonReminder.time,
    ) {
        layout = timelineLayout
        attributes {
            applyAttributesFrom(jsonReminder.layout)
        }
        actions {
            action(TimelineItem.Action.Type.Dismiss) {
                attributes { title { "Dismiss" } }
            }
            action(TimelineItem.Action.Type.OpenPin) {
                attributes { title { "More" } }
            }
        }
    }
}

private fun asPin(jsonPin: TimelinePinJson, appUuid: Uuid, pinUuid: Uuid): TimelinePin? {
    val timelineLayout = fromCode(jsonPin.layout.type)
    if (timelineLayout == null) {
        logger.w { "Unknown layout type: ${jsonPin.layout.type}" }
        return null
    }
    return buildTimelinePin(
        parentId = appUuid,
        timestamp = jsonPin.time,
    ) {
        if (jsonPin.durationMinutes != null) {
            duration = jsonPin.durationMinutes.minutes
        }
        itemID = pinUuid
        backingId = jsonPin.id
        layout = timelineLayout
        attributes {
            applyAttributesFrom(jsonPin.layout)
        }
        actions {
            jsonPin.actions?.forEach { a ->
                val actionType = a.type.asActionType()
                if (actionType == null) {
                    logger.w { "Unknown action type: ${a.type}" }
                } else {
//                    action(actionType) {
//                        // TODO developer actions
//                    }
                }
            }
            action(TimelineItem.Action.Type.Remove) {
                attributes { title { "Remove" } }
            }
        }
    }
}

private fun AttributesListBuilder.applyAttributesFrom(timelineLayoutJson: TimelineLayoutJson) {
    timelineLayoutJson.title?.let { title { it } }
    timelineLayoutJson.subtitle?.let { subtitle { it } }
    timelineLayoutJson.body?.let { body { it } }
    timelineLayoutJson.tinyIcon?.asTimelineIcon()?.let { tinyIcon { it } }
    timelineLayoutJson.smallIcon?.asTimelineIcon()?.let { smallIcon { it } }
    timelineLayoutJson.largeIcon?.asTimelineIcon()?.let { largeIcon { it } }
    timelineLayoutJson.primaryColor?.asPebbleColor()?.let { primaryColor { it } }
    timelineLayoutJson.secondaryColor?.asPebbleColor()?.let { secondaryColor { it } }
    timelineLayoutJson.backgroundColor?.asPebbleColor()?.let { backgroundColor { it } }
    timelineLayoutJson.headings?.let { stringList(TimelineAttribute.Headings) { it } }
    timelineLayoutJson.paragraphs?.let { stringList(TimelineAttribute.Paragraphs) { it } }
    timelineLayoutJson.lastUpdated?.let { lastUpdated { it } }
}


private fun String.asActionType(): TimelineItem.Action.Type? = when (this) {
    "openWatchApp" -> TimelineItem.Action.Type.OpenWatchapp
    "http" -> TimelineItem.Action.Type.HTTP
    else -> null
}

private fun String.asTimelineIcon(): TimelineIcon? = TimelineIcon.fromCode(this)

private fun String.asPebbleColor(): PebbleColor? {
    // TODO
    return null
}

@Serializable
data class TimelinePinJson(
    val id: String,
    val time: Instant,
    @SerialName("duration")
    val durationMinutes: Int? = null,
    val createNotification: TimelineNotificationJson? = null,
    val updateNotification: TimelineNotificationJson? = null,
    val layout: TimelineLayoutJson,
    val reminders: List<TimelineReminderJson>? = null,
    val actions: List<TimelineActionJson>? = null,
)

@Serializable
data class TimelineNotificationJson(
    val layout: TimelineLayoutJson,
    val time: Instant? = null,
)

@Serializable
data class TimelineReminderJson(
    val layout: TimelineLayoutJson,
    val time: Instant,
)

@Serializable
data class TimelineActionJson(
    val title: String,
    val type: String,
)

@Serializable
data class TimelineLayoutJson(
    val type: String,
    val title: String? = null,
    val subtitle: String? = null,
    val body: String? = null,
    val tinyIcon: String? = null,
    val smallIcon: String? = null,
    val largeIcon: String? = null,
    val primaryColor: String? = null,
    val secondaryColor: String? = null,
    val backgroundColor: String? = null,
    val headings: List<String>? = null,
    val paragraphs: List<String>? = null,
    val lastUpdated: Instant? = null,
)