package coredevices.ring.agent.builtin_servlets.notes

import PlatformUiContext
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.NoteIntegration
import coredevices.ring.database.room.repository.ItemRepository
import coredevices.ring.service.indexfeed.ItemFactory
import kotlin.time.Clock

/** The built-in note provider: notes are feed items in the local Notes list. */
class LocalNoteClient(
    private val itemFactory: ItemFactory,
    private val itemRepository: ItemRepository,
) : NoteIntegration {
    override suspend fun createNote(content: String, source: ItemSource?): String {
        val id = itemFactory.simpleUid()
        itemRepository.setItem(
            id,
            itemFactory.noteItem(
                sourceRecordingId = source?.recordingFirestoreId,
                createdAt = source?.createdAt ?: Clock.System.now(),
                title = content,
                listHint = null,
                toolCallId = null,
            ),
        )
        return id
    }

    override suspend fun signIn(uiContext: PlatformUiContext): Boolean {
        TODO("Not yet implemented")
    }

    override suspend fun unlink() {
        TODO("Not yet implemented")
    }

    override suspend fun isAuthorized(): Boolean = true
}