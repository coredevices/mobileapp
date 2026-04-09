package coredevices.ring.agent.integrations

import co.touchlab.kermit.Logger
import coredevices.indexai.data.notion.NotionBlock
import coredevices.indexai.data.notion.NotionSearchFilter
import coredevices.ring.agent.builtin_servlets.notes.NoteProvider
import coredevices.ring.agent.builtin_servlets.reminders.ReminderProvider
import coredevices.ring.api.NotionApi
import coredevices.ring.data.IntegrationDefinition
import coredevices.util.integrations.IntegrationTokenStorage
import coredevices.util.integrations.IntegrationAuthException
import coredevices.util.integrations.OAuthIntegration
import coredevices.ring.database.firestore.dao.DaoAuthException
import coredevices.firestore.UsersDao
import kotlinx.coroutines.flow.firstOrNull

class NotionIntegration(
    private val notionApi: NotionApi,
    private val usersDao: UsersDao,
    tokenStorage: IntegrationTokenStorage,
): NoteIntegration, OAuthIntegration(notionApi, tokenStorage, TOKEN_STORAGE_KEY) {
    override val oauthPathSegment: String = "notion"
    companion object {
        private const val TOKEN_STORAGE_KEY = "notion"
        val DEFINITION = IntegrationDefinition(
            title = "Notion",
            reminder = null,
            notes = NoteProvider.Notion
        )
        private val logger = Logger.withTag(NotionIntegration::class.simpleName!!)
    }

    suspend fun hasPage(): Boolean {
        return try {
            findPage()
            true
        } catch (e: NotionPageNotFound) {
            false
        }
    }

    private suspend fun findPage(): String {
        val response = notionApi.search(requireToken(),
            NotionSearchFilter(NotionSearchFilter.Value.page)
        )
        return try {
            val block = response.results.first()
            if (block.archived) {
                throw NotionPageNotFound("Notes page is archived")
            } else {
                block.id
            }
        } catch (e: NoSuchElementException) {
            throw NotionPageNotFound(cause = e)
        }
    }

    private suspend fun getOrPutTodoBlock(pageId: String): NotionBlock {
        val user = usersDao.user.firstOrNull() ?: error("No user")
        val todoBlock = user.user.todoBlockId?.let { notionApi.retrieveBlock(requireToken(), it) }
        return if (todoBlock == null || todoBlock.archived) {
            val block = notionApi.blockAppendChild(requireToken(), pageId, NotionBlock.Companion.heading1("Todo"))
            usersDao.updateTodoBlockId(block.id!!)
            block
        } else {
            todoBlock
        }
    }

    override suspend fun createNote(content: String): String {
        val user = usersDao.user.firstOrNull()
        if (user == null || user.user.notionToken == null) throw IntegrationAuthException("User not authenticated with Notion")
        val pageId = findPage()
        val todoBlock = getOrPutTodoBlock(pageId)
        val child = NotionBlock.bulletedListItem(content)
        return notionApi.blockAppendChild(requireToken(), todoBlock.parent!!.getId(), child, after = todoBlock.id!!).id!!
    }

    class NotionPageNotFound(message: String? = null, cause: Throwable? = null): Exception(message, cause)
}