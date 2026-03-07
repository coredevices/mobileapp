package coredevices.coreapp.push

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.database.entity.TimelineNotification
import io.rebble.libpebblecommon.database.entity.buildTimelineNotification
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import io.rebble.libpebblecommon.util.PebbleColor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Parser for FCM message payloads that directly creates Timeline notifications from FCM data
 */
class FCMPayloadParser {
    private val logger = Logger.withTag("FCMPayloadParser")
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parse FCM data map into TimelineNotification
     */
    fun parseToTimelineNotification(data: Map<String, String>): TimelineNotification? {
        logger.d { "Parsing FCM data to TimelineNotification: ${data.keys.joinToString(", ")}" }

        // Check if this is a timeline pin creation message
        if (data["type"] == "timeline.pin.create") {
            val pinJson = data["pin"]
            if (!pinJson.isNullOrBlank()) {
                return parseTimelinePinFromJson(pinJson)
            }
        }

        return null
    }

    /**
     * Parse timeline pin from JSON string
     */
    private fun parseTimelinePinFromJson(pinJson: String): TimelineNotification {
        logger.d { "Parsing timeline pin from JSON: ${pinJson.take(200)}..." }


        try {
            val jsonObject = json.parseToJsonElement(pinJson) as JsonObject

            val parentId = parseUuid(jsonObject["guid"]?.jsonPrimitive?.content) ?: Uuid.random()
            val timestamp = parseTimestamp(jsonObject["time"]?.jsonPrimitive?.content) ?: Clock.System.now()

            return buildTimelineNotification(parentId, timestamp) {
                // Set custom itemId if provided
                jsonObject["guid"]?.jsonPrimitive?.content?.let { itemIdStr ->
                    parseUuid(itemIdStr)?.let { itemID = it }
                }

                // Set layout
                layout = parseLayout(jsonObject["layout"]?.jsonObject) ?: TimelineItem.Layout.GenericNotification

                // Set flags
                flags {
                    if (jsonObject["visible"]?.jsonPrimitive?.content != "false") isVisible()
                    if (jsonObject["floating"]?.jsonPrimitive?.content == "true") isFloating()
                    if (jsonObject["allDay"]?.jsonPrimitive?.content == "true") isAllDay()
                    if (jsonObject["fromWatch"]?.jsonPrimitive?.content == "true") fromWatch()
                    if (jsonObject["fromANCS"]?.jsonPrimitive?.content == "true") fromANCS()
                    if (jsonObject["persistQuickView"]?.jsonPrimitive?.content == "true") persistQuickView()
                }

                // Build attributes from JSON data
                attributes {
                    configureAttributesFromJson(jsonObject)
                }

                // Build actions if provided
                val actionCount = jsonObject["actionCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                if (actionCount > 0) {
                    actions {
                        configureActionsFromJson(jsonObject, actionCount)
                    }
                }
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to parse timeline pin JSON: ${e.message}" }
            // Return a fallback notification
            return createFallbackNotification("Timeline Pin", "Failed to parse timeline data")
        }
    }

    /**
     * Configure timeline attributes from JSON data
     */
    private fun io.rebble.libpebblecommon.database.entity.AttributesListBuilder.configureAttributesFromJson(
        jsonObject: JsonObject
    ) {
        // Text attributes
        val jsonObject = jsonObject["layout"]?.jsonObject ?: return
        jsonObject["title"]?.jsonPrimitive?.content?.let { title { it } }
        jsonObject["subtitle"]?.jsonPrimitive?.content?.let { subtitle { it } }
        jsonObject["body"]?.jsonPrimitive?.content?.let { body { it } }
        jsonObject["sender"]?.jsonPrimitive?.content?.let { sender { it } }
        jsonObject["appName"]?.jsonPrimitive?.content?.let { appName { it } }
        jsonObject["location"]?.jsonPrimitive?.content?.let { location { it } }

        // Icon attributes
        jsonObject["icon"]?.jsonPrimitive?.content?.let { iconStr ->
            parseTimelineIcon(iconStr)?.let { icon { it } }
        }
        jsonObject["tinyIcon"]?.jsonPrimitive?.content?.let { iconStr ->
            parseTimelineIcon(iconStr)?.let { tinyIcon { it } }
        }
        jsonObject["smallIcon"]?.jsonPrimitive?.content?.let { iconStr ->
            parseTimelineIcon(iconStr)?.let { smallIcon { it } }
        }
        jsonObject["largeIcon"]?.jsonPrimitive?.content?.let { iconStr ->
            parseTimelineIcon(iconStr)?.let { largeIcon { it } }
        }

        // Color attributes
        jsonObject["primaryColor"]?.jsonPrimitive?.content?.let { colorStr ->
            parsePebbleColor(colorStr)?.let { primaryColor { it } }
        }
        jsonObject["backgroundColor"]?.jsonPrimitive?.content?.let { colorStr ->
            parsePebbleColor(colorStr)?.let { backgroundColor { it } }
        }
        jsonObject["secondaryColor"]?.jsonPrimitive?.content?.let { colorStr ->
            parsePebbleColor(colorStr)?.let { secondaryColor { it } }
        }

        // List attributes
        jsonObject["cannedResponses"]?.jsonPrimitive?.content?.let { responses ->
            val responseList = responses.split("|").filter { it.isNotBlank() }
            if (responseList.isNotEmpty()) {
                cannedResponse { responseList }
            }
        }

        jsonObject["headings"]?.jsonPrimitive?.content?.let { headings ->
            val headingList = headings.split("|").filter { it.isNotBlank() }
            if (headingList.isNotEmpty()) {
                headings { headingList }
            }
        }

        jsonObject["paragraphs"]?.jsonPrimitive?.content?.let { paragraphs ->
            val paragraphList = paragraphs.split("|").filter { it.isNotBlank() }
            if (paragraphList.isNotEmpty()) {
                paragraphs { paragraphList }
            }
        }

        // Timestamp attributes
        jsonObject["lastUpdated"]?.jsonPrimitive?.content?.let { timestampStr ->
            parseTimestamp(timestampStr)?.let { lastUpdated { it } }
        }

        // Custom vibration pattern
        jsonObject["vibrationPattern"]?.jsonPrimitive?.content?.let { pattern ->
            val patternList = pattern.split(",").mapNotNull { it.trim().toUIntOrNull() }
            if (patternList.isNotEmpty()) {
                vibrationPattern { patternList }
            }
        }
    }

    /**
     * Configure timeline attributes from FCM data (backward compatibility)
     */
    private fun io.rebble.libpebblecommon.database.entity.AttributesListBuilder.configureAttributes(
        data: Map<String, String>,
    ) {
        // Text attributes
        data["title"]?.let { title { it } }
        data["subtitle"]?.let { subtitle { it } }
        data["body"]?.let { body { it } }
        data["sender"]?.let { sender { it } }
        data["appName"]?.let { appName { it } }
        data["location"]?.let { location { it } }

        // Icon attributes
        data["icon"]?.let { iconStr ->
            parseTimelineIcon(iconStr)?.let { icon { it } }
        }
        data["tinyIcon"]?.let { iconStr ->
            parseTimelineIcon(iconStr)?.let { tinyIcon { it } }
        }
        data["smallIcon"]?.let { iconStr ->
            parseTimelineIcon(iconStr)?.let { smallIcon { it } }
        }
        data["largeIcon"]?.let { iconStr ->
            parseTimelineIcon(iconStr)?.let { largeIcon { it } }
        }

        // Color attributes
        data["primaryColor"]?.let { colorStr ->
            parsePebbleColor(colorStr)?.let { primaryColor { it } }
        }
        data["backgroundColor"]?.let { colorStr ->
            parsePebbleColor(colorStr)?.let { backgroundColor { it } }
        }
        data["secondaryColor"]?.let { colorStr ->
            parsePebbleColor(colorStr)?.let { secondaryColor { it } }
        }

        // List attributes
        data["cannedResponses"]?.let { responses ->
            val responseList = responses.split("|").filter { it.isNotBlank() }
            if (responseList.isNotEmpty()) {
                cannedResponse { responseList }
            }
        }

        data["headings"]?.let { headings ->
            val headingList = headings.split("|").filter { it.isNotBlank() }
            if (headingList.isNotEmpty()) {
                headings { headingList }
            }
        }

        data["paragraphs"]?.let { paragraphs ->
            val paragraphList = paragraphs.split("|").filter { it.isNotBlank() }
            if (paragraphList.isNotEmpty()) {
                paragraphs { paragraphList }
            }
        }

        // Timestamp attributes
        data["lastUpdated"]?.let { timestampStr ->
            parseTimestamp(timestampStr)?.let { lastUpdated { it } }
        }

        // Custom vibration pattern
        data["vibrationPattern"]?.let { pattern ->
            val patternList = pattern.split(",").mapNotNull { it.trim().toUIntOrNull() }
            if (patternList.isNotEmpty()) {
                vibrationPattern { patternList }
            }
        }
    }

    /**
     * Configure timeline actions from JSON data
     */
    private fun io.rebble.libpebblecommon.database.entity.ActionsListBuilder.configureActionsFromJson(
        jsonObject: JsonObject,
        actionCount: Int
    ) {
        for (i in 0 until actionCount) {
            val actionType = parseActionType(jsonObject["action${i}_type"]?.jsonPrimitive?.content) ?: TimelineItem.Action.Type.Generic

            action(actionType) {
                attributes {
                    // Action title
                    jsonObject["action${i}_title"]?.jsonPrimitive?.content?.let { actionTitle ->
                        title { actionTitle }
                    }

                    // Action body/description
                    jsonObject["action${i}_body"]?.jsonPrimitive?.content?.let { actionBody ->
                        body { actionBody }
                    }

                    // Canned responses for reply actions
                    jsonObject["action${i}_cannedResponses"]?.jsonPrimitive?.content?.let { responses ->
                        val responseList = responses.split("|").filter { it.isNotBlank() }
                        if (responseList.isNotEmpty()) {
                            cannedResponse { responseList }
                        }
                    }

                    // Action icon
                    jsonObject["action${i}_icon"]?.jsonPrimitive?.content?.let { iconStr ->
                        parseTimelineIcon(iconStr)?.let { icon { it } }
                    }
                }
            }
        }
    }

    /**
     * Configure timeline actions from FCM data (backward compatibility)
     */
    private fun io.rebble.libpebblecommon.database.entity.ActionsListBuilder.configureActions(
        data: Map<String, String>,
        actionCount: Int,
    ) {
        for (i in 0 until actionCount) {
            val actionType =
                parseActionType(data["action${i}_type"]) ?: TimelineItem.Action.Type.Generic

            action(actionType) {
                attributes {
                    // Action title
                    data["action${i}_title"]?.let { actionTitle ->
                        title { actionTitle }
                    }

                    // Action body/description
                    data["action${i}_body"]?.let { actionBody ->
                        body { actionBody }
                    }

                    // Canned responses for reply actions
                    data["action${i}_cannedResponses"]?.let { responses ->
                        val responseList = responses.split("|").filter { it.isNotBlank() }
                        if (responseList.isNotEmpty()) {
                            cannedResponse { responseList }
                        }
                    }

                    // Action icon
                    data["action${i}_icon"]?.let { iconStr ->
                        parseTimelineIcon(iconStr)?.let { icon { it } }
                    }
                }
            }
        }
    }

    /**
     * Parse layout from string
     */
    private fun parseLayout(jsonObject: JsonObject?): TimelineItem.Layout? {
        if (jsonObject.isNullOrEmpty()) return null

        return try {
            // Try parsing as enum name first
            if(jsonObject["type"]?.jsonPrimitive?.content == "genericPin") {
                TimelineItem.Layout.GenericPin
            } else {
                TimelineItem.Layout.GenericNotification
            }
        } catch (e: Exception) {
            logger.w(e) { "Failed to parse layout: ${jsonObject.jsonPrimitive.content}" }
            null
        }
    }

    /**
     * Parse action type from string
     */
    private fun parseActionType(actionTypeStr: String?): TimelineItem.Action.Type? {
        if (actionTypeStr.isNullOrBlank()) return null

        return try {
            TimelineItem.Action.Type.entries.find {
                it.name.equals(actionTypeStr, ignoreCase = true)
            }
        } catch (e: Exception) {
            logger.w(e) { "Failed to parse action type: $actionTypeStr" }
            null
        }
    }

    /**
     * Parse TimelineIcon from string (name or ID)
     */
    private fun parseTimelineIcon(iconStr: String?): TimelineIcon? {
        if (iconStr.isNullOrBlank()) return null

        return try {
            // Try parsing as enum name first
            TimelineIcon.entries.find {
                it.name.equals(iconStr, ignoreCase = true)
            } ?: run {
                // Try parsing as icon ID
                val iconId = iconStr.toUIntOrNull()
                iconId?.let { id ->
                    TimelineIcon.entries.find { it.id == id }
                }
            }
        } catch (e: Exception) {
            logger.w(e) { "Failed to parse icon: $iconStr" }
            null
        }
    }

    /**
     * Parse PebbleColor from string (name or hex)
     */
    private fun parsePebbleColor(colorStr: String?): PebbleColor? {
        if (colorStr.isNullOrBlank()) return null
        return PebbleColor(255U, 0U, 0U, 0U)

//        return try {
//            // Try parsing as enum name first
//            PebbleColor.entries.find {
//                it.name.equals(colorStr, ignoreCase = true)
//            } ?: run {
//                // Try parsing as hex color (not directly supported, use closest match)
//                when (colorStr.lowercase()) {
//                    "#ff0000", "#f00", "red" -> PebbleColor( 255U, 0U, 0U, 0U)
//                    "#00ff00", "#0f0", "green" -> PebbleColor.Green
//                    "#0000ff", "#00f", "blue" -> PebbleColor.Blue
//                    "#ffff00", "#ff0", "yellow" -> PebbleColor.Yellow
//                    "#ff8000", "orange" -> PebbleColor.Orange
//                    "#800080", "purple" -> PebbleColor.Purple
//                    "#ffffff", "#fff", "white" -> PebbleColor.White
//                    "#000000", "#000", "black" -> PebbleColor.Black
//                    "#808080", "gray", "grey" -> PebbleColor.LightGray
//                    else -> null
//                }
//            }
//        } catch (e: Exception) {
//            logger.w(e) { "Failed to parse color: $colorStr" }
//            null
//        }
    }

    /**
     * Parse timestamp from various possible formats
     */
    private fun parseTimestamp(timestampStr: String?): Instant? {
        if (timestampStr.isNullOrBlank()) return null

        return try {
            // Try parsing as ISO string first
            Instant.parse(timestampStr)
        } catch (e: Exception) {
            try {
                // Try parsing as unix timestamp (seconds)
                val seconds = timestampStr.toLongOrNull()
                seconds?.let { Instant.fromEpochSeconds(it) }
            } catch (e2: Exception) {
                logger.w(e2) { "Failed to parse timestamp: $timestampStr" }
                null
            }
        }
    }

    /**
     * Parse duration from string value
     */
    private fun parseDuration(durationStr: String?): Duration? {
        if (durationStr.isNullOrBlank()) return null

        return try {
            // Try parsing as seconds first
            val seconds = durationStr.toLongOrNull()
            if (seconds != null) {
                Duration.parse("${seconds}s")
            } else {
                // Try parsing as ISO duration
                Duration.parse(durationStr)
            }
        } catch (e: Exception) {
            logger.w(e) { "Failed to parse duration: $durationStr" }
            null
        }
    }

    /**
     * Parse UUID from string, handling various formats
     */
    private fun parseUuid(uuidString: String?): Uuid? {
        if (uuidString.isNullOrBlank()) return null

        return try {
            Uuid.parse(uuidString)
        } catch (e: Exception) {
            try {
                // Try creating a UUID from the hash of the string
                val hash = uuidString.hashCode()
                Uuid.fromLongs(hash.toLong(), hash.toLong())
            } catch (e2: Exception) {
                logger.w(e2) { "Failed to parse UUID from: $uuidString" }
                null
            }
        }
    }

    /**
     * Create a fallback timeline notification for backward compatibility
     */
    fun createFallbackNotification(
        title: String?,
        body: String?,
        appName: String? = null,
    ): TimelineNotification {
        logger.d { "Creating fallback notification: title='$title', body='$body'" }

        val parentId = Uuid.random()
        val timestamp = Clock.System.now()

        return buildTimelineNotification(parentId, timestamp) {
            layout = TimelineItem.Layout.GenericNotification

            flags {
                isVisible()
            }

            attributes {
                title?.let { title { it } }
                body?.let { body { it } }
                appName?.let { appName { it } }

                // Default icon for fallback notifications
                icon { TimelineIcon.NotificationGeneric }
                primaryColor { PebbleColor(255U, 255U, 255U, 255U) }
                backgroundColor { PebbleColor(255U, 0U, 0U, 0U) }
            }
        }
    }
}