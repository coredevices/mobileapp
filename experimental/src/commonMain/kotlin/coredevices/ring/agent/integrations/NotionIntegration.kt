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
import coredevices.indexai.data.notion.NotionSearchResult
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        private val logger = Logger.withTag("NotionIntegration")
    }

    /**
     * Whether the OAuth grant gives access to at least one page the user can pick for notes.
     * Used to validate a fresh sign-in; deliberately does not require a page to be selected yet
     * (the picker is shown afterwards), so it must not be confused with [findPage].
     */
    suspend fun hasPage(): Boolean = listPages().isNotEmpty()

    /** All non-archived pages the integration has been granted access to. */
    suspend fun listPages(): List<NotionPage> {
        val response = notionApi.search(requireToken(),
            NotionSearchFilter(NotionSearchFilter.Value.page)
        )
        return response.results
            .filter { !it.archived }
            .map { NotionPage(it.id, it.pageTitle()) }
    }

    /** The page currently chosen to hold the Todo block, if any. */
    suspend fun selectedPageId(): String? =
        usersDao.user.firstOrNull()?.user?.notionPageId

    /** Choose which page the Todo block lives in. */
    suspend fun selectPage(pageId: String) {
        usersDao.updateNotionPageId(pageId)
    }

    private suspend fun findPage(): String {
        val pages = notionApi.search(requireToken(),
            NotionSearchFilter(NotionSearchFilter.Value.page)
        ).results.filter { !it.archived }
        if (pages.isEmpty()) {
            throw NotionPageNotFound("No accessible Notion page")
        }
        val selectedId = selectedPageId()
            ?: throw NotionPageNotFound("No Notion page selected for notes")
        return pages.firstOrNull { it.id == selectedId }?.id
            ?: throw NotionPageNotFound("Selected page not found or archived")
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

    override suspend fun createNote(content: String, source: ItemSource?): String {
        val user = usersDao.user.firstOrNull()
            ?: throw IntegrationAuthException("User not authenticated with Notion")
        val pageId = findPage()
        val todoBlock = getOrPutTodoBlock(pageId)
        val child = NotionBlock.bulletedListItem(content)
        return notionApi.blockAppendChild(requireToken(), todoBlock.parent!!.getId(), child, after = todoBlock.id!!).id!!
    }

    class NotionPageNotFound(message: String? = null, cause: Throwable? = null): Exception(message, cause)

    data class NotionPage(val id: String, val title: String)
}

private fun NotionSearchResult.pageTitle(): String {
    val titleText = properties.values
        .firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "title" }
        ?.get("title")?.jsonArray
        ?.mapNotNull { it.jsonObject["plain_text"]?.jsonPrimitive?.contentOrNull }
        ?.joinToString("")
        ?.takeIf { it.isNotBlank() }
    return titleText ?: "Untitled"
}