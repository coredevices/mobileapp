package coredevices.ring.agent.integrations

import coredevices.mcp.SessionContext
import coredevices.util.integrations.Integration
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Manages reminders for one provider. Like [NoteIntegration], implementations are singletons that
 * manage reminders on a platform/service — not objects representing a single reminder. Obtain the
 * active provider's instance from `ReminderIntegrationFactory`.
 */
interface ReminderIntegration : Integration {
    /**
     * Schedules a reminder and returns its id (throws on failure). [listId] targets a list found
     * via [searchForList]; [notifyBefore] requests an extra early heads-up notification before
     * [deadline]. Integrations that can't honour either simply ignore them. Builtin integrations
     * create the feed item themselves, stamped from [source]; external ones ignore it.
     */
    suspend fun createReminder(
        title: String,
        deadline: Instant?,
        listId: String? = null,
        notifyBefore: Duration? = null,
        source: ItemSource? = null,
    ): String

    suspend fun searchForList(listName: String): List<ReminderListEntry>
}

data class ReminderListEntry(
    val id: String,
    val title: String
)

interface NoteIntegration : Integration {
    suspend fun createNote(content: String, source: ItemSource? = null): String?
}

/**
 * Identifies the recording a tool-created object came from, so builtin integrations can stamp
 * the feed items they create (feed grouping and reminder deep links rely on it). External
 * integrations ignore it.
 */
data class ItemSource(
    val recordingFirestoreId: String?,
    val createdAt: Instant?,
)

fun SessionContext.itemSource(): ItemSource =
    ItemSource(recordingFirestoreId, timeBase)