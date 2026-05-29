@file:OptIn(ExperimentalTime::class)

package coredevices.ring.data.entity.room.indexfeed

import coredevices.indexai.data.entity.ItemDocument
import coredevices.indexai.data.entity.ItemDocument.ItemMetadata
import coredevices.indexai.util.JsonSnake
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Backward-compat bridge: PR #58 moved item type from a free-form `kind: String`
 * + `fieldsJson: String` into a typed [ItemMetadata] sealed class. The UI in
 * #59 was written against the old shape; rather than rewrite every call site
 * we keep the legacy-read helpers and provide a single place that maps a
 * `kind` string back to an [ItemMetadata] default for write paths.
 *
 * Reads return the [SerialName] of the metadata variant ("reminder", "note",
 * "scheduled", etc.) so existing `it.kind == "reminder"` checks keep working.
 *
 * Writes go through [metadataForKind], which produces a default metadata
 * carrying as much of the existing one's typed fields as possible.
 */

val ItemMetadata.kind: String get() = when (this) {
    is ItemMetadata.Reminder -> "reminder"
    is ItemMetadata.Scheduled -> "scheduled"
    is ItemMetadata.Message -> "message"
    is ItemMetadata.Answer -> "answer"
    is ItemMetadata.ActionLog -> "action_log"
    is ItemMetadata.McpCall -> "mcp_call"
    ItemMetadata.Note -> "note"
}

val CachedItem.kind: String get() = metadata.kind
val ItemDocument.kind: String get() = metadata.kind

/**
 * Re-serialise the metadata to a flat JSON object the UI can read by key.
 * The kotlinx.serialization sealed-class encoding adds a `"type"` discriminator
 * which we strip so legacy `fields["fireKind"]` lookups stay clean.
 */
private fun ItemMetadata.toFieldsJsonObject(): JsonObject {
    return when (this) {
        is ItemMetadata.Reminder -> buildJsonObject {
            put("repeat", repeat)
            put("notification", notification)
        }
        is ItemMetadata.Scheduled -> buildJsonObject {
            put("fireKind", when (fireKind) {
                ItemMetadata.Scheduled.FireKind.Alarm -> "alarm"
                ItemMetadata.Scheduled.FireKind.Timer -> "timer"
            })
            fireTime?.let { put("fireTime", it.toString().substringBefore('.').take(5)) }
            duration?.let { put("duration", it) }
            if (repeatDays.isNotEmpty()) {
                put("repeatDays", JsonPrimitive(repeatDays.joinToString(",")))
            }
            enabled?.let { put("enabled", it) }
        }
        is ItemMetadata.Message -> buildJsonObject {
            put("integration", integration)
            put("contact", contact)
            put("recipientName", recipientName)
            put("text", text)
            put("sentAt", sentAt.toEpochMilliseconds())
            put("status", when (status) {
                ItemMetadata.Message.Status.Sent -> "sent"
                ItemMetadata.Message.Status.Failed -> "failed"
            })
            errorMessage?.let { put("errorMessage", it) }
        }
        is ItemMetadata.Answer -> buildJsonObject {
            put("question", question)
        }
        is ItemMetadata.ActionLog -> buildJsonObject {
            put("toolName", toolName)
            put("success", success)
        }
        is ItemMetadata.McpCall -> buildJsonObject {
            put("toolName", toolName)
            put("success", success)
        }
        ItemMetadata.Note -> JsonObject(emptyMap())
    }
}

fun CachedItem.fields(): JsonObject = metadata.toFieldsJsonObject()
fun ItemDocument.fields(): JsonObject = metadata.toFieldsJsonObject()
val CachedItem.fieldsJson: String get() = metadata.toFieldsJsonObject().toString()
val ItemDocument.fieldsJson: String get() = metadata.toFieldsJsonObject().toString()

/** Convenience for the home / object-list views which only care about the
 *  `Scheduled` discriminator string ("alarm" or "timer") and want a `null`
 *  for everything else. */
fun CachedItem.fireKind(): String? = (metadata as? ItemMetadata.Scheduled)?.let {
    when (it.fireKind) {
        ItemMetadata.Scheduled.FireKind.Alarm -> "alarm"
        ItemMetadata.Scheduled.FireKind.Timer -> "timer"
    }
}

/** Returns the message-recipient display name when the metadata is a
 *  [ItemMetadata.Message], otherwise null. Used by recording-detail
 *  views to show "To X: ..." reply bubbles. */
fun CachedItem.recipientName(): String? = (metadata as? ItemMetadata.Message)?.recipientName

/**
 * Given a target [kind] string and (optionally) the existing metadata,
 * produce a default [ItemMetadata] for the new kind. If the existing
 * metadata is already of the requested kind, keep it untouched so we
 * preserve typed fields like `fireKind` / `repeatDays` that the UI
 * doesn't surface.
 */
fun metadataForKind(kind: String, existing: ItemMetadata? = null): ItemMetadata {
    if (existing != null && existing.kind == kind) return existing
    return when (kind) {
        "reminder" -> ItemMetadata.Reminder(repeat = "one_time", notification = "push")
        "scheduled" -> ItemMetadata.Scheduled(
            fireKind = ItemMetadata.Scheduled.FireKind.Alarm,
        )
        "answer" -> ItemMetadata.Answer(question = "")
        "action_log" -> ItemMetadata.ActionLog(toolName = "", success = true)
        "mcp_call" -> ItemMetadata.McpCall(toolName = "", success = true)
        "message" -> ItemMetadata.Message(
            integration = "beeper",
            contact = "",
            recipientName = "",
            text = "",
            sentAt = Clock.System.now(),
            status = ItemMetadata.Message.Status.Sent,
        )
        else -> ItemMetadata.Note
    }
}
