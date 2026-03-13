package coredevices.coreapp.push

import co.touchlab.kermit.Logger
import io.rebble.libpebblecommon.database.entity.TimelinePin
import io.rebble.libpebblecommon.database.entity.buildTimelinePin
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Parser for FCM message payloads that directly creates Timeline notifications from FCM data
 */
class FCMPayloadParser {
    private val logger = Logger.withTag("FCMPayloadParser")

    /**
     * Configure timeline attributes from PinData using serialized data classes
     */
    private fun io.rebble.libpebblecommon.database.entity.AttributesListBuilder.configureAttributesFromPinData(
        pinData: PinData,
    ) {
        val layout = pinData.layout
        if (layout != null) {
            layout.title?.let { title { it } }
            layout.subtitle?.let { subtitle { it } }

            layout.tinyIcon?.let { iconStr ->
                parseTimelineIcon(iconStr)?.let { tinyIcon { it } }
            }
        }
    }

    /**
     * Parse layout from LayoutData
     */
    private fun parseLayoutFromData(layoutData: LayoutData?): TimelineItem.Layout {
        //TODO parse layout data
        return if (layoutData?.type == "genericPin") {
            TimelineItem.Layout.GenericPin
        } else {
            TimelineItem.Layout.GenericNotification
        }
    }

    /**
     * Parse TimelineIcon from string (name, ID, or system path)
     */
    private fun parseTimelineIcon(iconStr: String?): TimelineIcon? {
        if (iconStr.isNullOrBlank()) return TimelineIcon.NotificationGeneric
        return TimelineIcon.NotificationFlag
    }

    /**
     * Parse timestamp from various possible formats
     */
    private fun parseTimestamp(timestampStr: String?): Instant? {
        if (timestampStr.isNullOrBlank()) {
            logger.w { "Timestamp string is null or blank" }
            return null
        }

        return try {
            Instant.parse(timestampStr)
        } catch (e: Exception) {
            logger.w(e) { "Failed to parse as ISO timestamp: $timestampStr, error: ${e.message}" }
            return null
        }
    }

    /**
     * Parse UUID from string, handling various formats
     */
    private fun parseUuid(uuidString: String?): Uuid? {
        if (uuidString.isNullOrBlank()) {
            logger.d { "UUID string is null or blank" }
            return null
        }

        return try {
            Uuid.parse(uuidString)
        } catch (e: Exception) {
            logger.w(e) { "Failed to parse as standard UUID: $uuidString, error: ${e.message}" }
            return null
        }
    }

    /**
     * Parse pins from sync response using serialized data classes
     */
    fun parsePinsFromSyncResponse(syncResponse: SyncResponse): List<TimelinePin> {
        logger.d { "Parsing ${syncResponse.updates.size} updates from sync response" }

        return try {
            syncResponse.updates.mapNotNull { update ->
                convertPinDataToTimelinePin(update.data)
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to parse sync response: ${e.message}" }
            emptyList()
        }
    }

    /**
     * Convert PinData to TimelinePin using serialized data classes
     */
    private fun convertPinDataToTimelinePin(pinData: PinData): TimelinePin? {
        return try {
            //TODO correct parentId this is Item ID
            val parentId = parseUuid(pinData.guid) ?: Uuid.random()
            val timestamp = parseTimestamp(pinData.time) ?: Clock.System.now()

            buildTimelinePin(parentId, timestamp) {
                itemID = parentId
                layout = parseLayoutFromData(pinData.layout)
                flags { isVisible() }
                attributes { configureAttributesFromPinData(pinData) }
                // TODO add actions
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to convert Pin ${pinData.guid} : ${e.message}" }
            return null
        }
    }
}