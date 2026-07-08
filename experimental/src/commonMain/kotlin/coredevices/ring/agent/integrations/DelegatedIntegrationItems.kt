package coredevices.ring.agent.integrations

import co.touchlab.kermit.Logger
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.service.indexfeed.ItemFactory
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Persists the "Sent to <integration>" marker item for notes/reminders handled by an external
 * integration. The real object lives in the external service; builtin integrations create full
 * local items instead and are never wrapped.
 */
class DelegatedIntegrationItems(
    private val itemFactory: ItemFactory,
    private val itemRepository: ItemRepository,
) {
    /** Failures are logged, not thrown — the external create already succeeded. */
    suspend fun record(integrationTitle: String, title: String, source: ItemSource?) {
        val item = itemFactory.delegatedItem(
            sourceRecordingId = source?.recordingFirestoreId,
            createdAt = source?.createdAt ?: Clock.System.now(),
            title = title,
            integrationName = integrationTitle,
            toolCallId = null,
        )
        runCatching { itemRepository.setItem(itemFactory.simpleUid(), item) }
            .onFailure { logger.e(it) { "Failed to persist delegated item for $integrationTitle" } }
    }

    companion object {
        private val logger = Logger.withTag("DelegatedIntegrationItems")
    }
}

/** Wraps an external note integration so each successful create also records a delegated item. */
class DelegatingNoteIntegration(
    private val delegate: NoteIntegration,
    private val integrationTitle: String,
    private val items: DelegatedIntegrationItems,
) : NoteIntegration by delegate {
    override suspend fun createNote(content: String, source: ItemSource?): String? {
        val id = delegate.createNote(content, source)
        // Null id means the integration created nothing.
        if (id != null) items.record(integrationTitle, content, source)
        return id
    }
}

/** Wraps an external reminder integration so each successful create also records a delegated item. */
class DelegatingReminderIntegration(
    private val delegate: ReminderIntegration,
    private val integrationTitle: String,
    private val items: DelegatedIntegrationItems,
) : ReminderIntegration by delegate {
    override suspend fun createReminder(
        title: String,
        deadline: Instant?,
        listId: String?,
        notifyBefore: Duration?,
        source: ItemSource?,
    ): String {
        val id = delegate.createReminder(title, deadline, listId, notifyBefore, source)
        items.record(integrationTitle, title, source)
        return id
    }
}
