package coredevices.ring.agent.builtin_servlets.reminders

import co.touchlab.kermit.Logger
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.ReminderListEntry
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.database.room.repository.ListRepository
import coredevices.ring.service.indexfeed.DefaultListsBootstrap.Companion.LIST_NOTES_SELF_ID
import coredevices.ring.service.indexfeed.ItemFactory
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Feed-item side of the built-in reminder integration, shared by the Android and iOS actuals.
 * The platform classes schedule the OS alarm/notification; this creates the feed item the user
 * sees (external providers create no local item — theirs lives in the external service).
 */
class BuiltInReminderFeedItems(
    private val itemFactory: ItemFactory,
    private val itemRepository: ItemRepository,
    private val listRepository: ListRepository,
) {
    /**
     * Resolves a list-name hint against the local lists (same matching ListTool uses). Falls
     * back to the Notes list when nothing matches, so an unrecognised hint yields a note there
     * rather than a reminder in Todos.
     */
    suspend fun searchForList(listName: String): List<ReminderListEntry> {
        val lists = listRepository.getAllFlow().first()
        val id = ListTool.matchListIdByHint(lists, listName) ?: LIST_NOTES_SELF_ID
        val title = lists.firstOrNull { it.firestoreId == id }?.title ?: "Notes"
        return listOf(ReminderListEntry(id, title))
    }

    /**
     * Creates the feed item for a built-in reminder: a note in [listId] when targeted at a list
     * (ListTool path), else a reminder in the todos list.
     */
    suspend fun createFeedItem(
        localReminderId: Int,
        title: String,
        deadline: Instant?,
        listId: String?,
        notifyBefore: Duration?,
        source: ItemSource?,
    ) {
        val createdAt = source?.createdAt ?: Clock.System.now()
        val item = if (listId != null) {
            itemFactory.noteItem(
                sourceRecordingId = source?.recordingFirestoreId,
                createdAt = createdAt,
                title = title,
                listHint = null,
                toolCallId = null,
                resolvedListId = listId,
            )
        } else {
            itemFactory.reminderItem(
                sourceRecordingId = source?.recordingFirestoreId,
                createdAt = createdAt,
                title = title,
                dueAt = deadline,
                toolCallId = null,
                localReminderId = localReminderId,
                notifyBeforeMillis = notifyBefore?.inWholeMilliseconds,
            )
        }
        itemRepository.setItem(itemFactory.simpleUid(), item)
    }

    companion object {
        private val logger = Logger.withTag("BuiltInReminderFeedItems")
    }
}
