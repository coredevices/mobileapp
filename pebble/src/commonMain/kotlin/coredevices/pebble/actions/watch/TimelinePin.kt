package coredevices.pebble.actions.watch

import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.database.entity.buildTimelinePin
import io.rebble.libpebblecommon.packets.blobdb.TimelineIcon
import io.rebble.libpebblecommon.packets.blobdb.TimelineItem
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Builds a timeline pin from individual components, generates a UUID for it, inserts it, and
 * returns the generated pin ID.
 *
 * @param epochSeconds Unix epoch of the pin time. Uses current time if null or <= 0.
 */
fun insertTimelinePinRich(
    libPebble: LibPebble,
    appUuid: Uuid,
    title: String,
    body: String,
    subtitle: String?,
    iconCode: String?,
    epochSeconds: Long?,
): String {
    val pinUuid = Uuid.random()
    val instant = if (epochSeconds != null && epochSeconds > 0) {
        Instant.fromEpochSeconds(epochSeconds)
    } else {
        Clock.System.now()
    }

    val pin = buildTimelinePin(
        parentId = appUuid,
        timestamp = instant,
    ) {
        itemID = pinUuid
        duration = Duration.ZERO
        layout = TimelineItem.Layout.GenericPin
        attributes {
            this.title { title }
            this.body { if (body.isEmpty()) " " else body }
            if (!subtitle.isNullOrEmpty()) {
                this.subtitle { subtitle }
            }
            if (!iconCode.isNullOrEmpty()) {
                val icon = TimelineIcon.fromCode(iconCode)
                if (icon != null) {
                    this.tinyIcon { icon }
                }
            }
        }
        actions {
            action(TimelineItem.Action.Type.Remove) {
                attributes { this.title { "Remove" } }
            }
        }
    }

    libPebble.insertOrReplace(pin)
    return pinUuid.toString()
}

/** Deletes a timeline pin by id for the given app. */
fun deleteTimelinePin(
    libPebble: LibPebble,
    appUuid: Uuid,
    pinId: String,
) {
    val uuid = try { Uuid.parse(pinId) } catch (e: Exception) { null }
    if (uuid != null) {
        libPebble.delete(uuid)
    }
}
