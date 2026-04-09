package coredevices.ring.agent.integrations

import coredevices.util.integrations.Integration
import kotlin.time.Instant
interface ReminderIntegration : Integration {
    suspend fun createReminder(title: String, deadline: Instant?, listId: String?): String?
    suspend fun searchForList(listName: String): List<ReminderListEntry>
}

data class ReminderListEntry(
    val id: String,
    val title: String
)

interface NoteIntegration : Integration {
    suspend fun createNote(content: String): String?
}