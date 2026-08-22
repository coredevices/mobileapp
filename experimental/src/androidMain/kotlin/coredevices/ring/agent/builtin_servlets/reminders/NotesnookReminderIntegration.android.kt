package coredevices.ring.agent.builtin_servlets.reminders

import PlatformUiContext
import coredevices.ring.agent.builtin_servlets.notes.NOTESNOOK_TOKEN_STORAGE_KEY
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.ReminderIntegration
import coredevices.ring.agent.integrations.ReminderListEntry
import coredevices.ring.external.indexlocal.IndexLocalCaptureApi
import coredevices.util.integrations.IntegrationTokenStorage
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

actual fun createNotesnookReminderIntegration(): ReminderIntegration = NotesnookReminderIntegration()

/**
 * Sends Index reminders to Notesnook as a note + reminder alarm via the same local capture
 * intent as notes. Connecting shares the Notesnook opt-in token with [NotesnookNoteClient].
 *
 * [deadline] is forwarded as UTC epoch milliseconds and ISO-8601 (`Instant.toString()`).
 * A null deadline becomes a permanent Notesnook reminder (no Once date).
 */
class NotesnookReminderIntegration : ReminderIntegration, KoinComponent {
    private val tokenStorage: IntegrationTokenStorage by inject()
    private val api: IndexLocalCaptureApi by inject()

    override suspend fun createReminder(
        title: String,
        deadline: Instant?,
        listId: String?,
        notifyBefore: Duration?,
        source: ItemSource?,
    ): String {
        val recordingId = source?.recordingFirestoreId
            ?: source?.toolCallId
            ?: "reminder-${Clock.System.now().toEpochMilliseconds()}"
        api.deliverReminder(
            title = title,
            deadline = deadline,
            notifyBefore = notifyBefore,
            recordingId = recordingId,
            recordedAt = source?.createdAt ?: Clock.System.now(),
        )
        return recordingId
    }

    override suspend fun searchForList(listName: String): List<ReminderListEntry> =
        listOf(ReminderListEntry(id = listName, title = listName))

    override suspend fun signIn(uiContext: PlatformUiContext): Boolean {
        if (!api.isNotesnookInstalled()) return false
        tokenStorage.saveToken(NOTESNOOK_TOKEN_STORAGE_KEY, "enabled")
        return true
    }

    override suspend fun unlink() {
        tokenStorage.deleteToken(NOTESNOOK_TOKEN_STORAGE_KEY)
    }

    override suspend fun isAuthorized(): Boolean =
        api.isNotesnookInstalled() && tokenStorage.getToken(NOTESNOOK_TOKEN_STORAGE_KEY) != null

    override suspend fun getAllLists(): List<ReminderListEntry> = emptyList()
}
