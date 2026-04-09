package coredevices.ring.agent.builtin_servlets.reminders

import kotlin.time.Instant

interface Reminder {
    val time: Instant?
    val message: String
    suspend fun schedule(): String
    suspend fun cancel()
}

interface ListAssignableReminder : Reminder {
    suspend fun scheduleToList(listName: String): String
    val listTitle: String?
}