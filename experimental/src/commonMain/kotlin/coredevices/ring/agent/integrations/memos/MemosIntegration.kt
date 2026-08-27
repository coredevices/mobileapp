package coredevices.ring.agent.integrations.memos

import PlatformUiContext
import coredevices.ring.agent.builtin_servlets.notes.NoteProvider
import coredevices.ring.agent.integrations.ItemSource
import coredevices.ring.agent.integrations.NoteIntegration
import coredevices.ring.data.IntegrationDefinition
import coredevices.util.integrations.IntegrationAuthException
import coredevices.util.integrations.IntegrationTokenStorage

/**
 * Posts notes to a self-hosted [Memos](https://usememos.com) server. The user supplies the server
 * URL and an access token, so there is no OAuth flow to run.
 */
class MemosIntegration(
    private val api: MemosApi,
    private val prefs: MemosPreferences,
    private val tokenStorage: IntegrationTokenStorage,
) : NoteIntegration {

    companion object {
        const val TOKEN_KEY = "memos"
        val DEFINITION = IntegrationDefinition(
            title = "Memos",
            reminder = null,
            notes = NoteProvider.Memos,
        )
    }

    /** Stores the server and token only once the server has confirmed they work. */
    suspend fun connect(baseUrl: String, token: String): Boolean {
        val normalized = baseUrl.trim().trimEnd('/')
        if (!api.validateCredentials(normalized, token)) return false
        prefs.setBaseUrl(normalized)
        tokenStorage.saveToken(TOKEN_KEY, token)
        return true
    }

    override suspend fun signIn(uiContext: PlatformUiContext): Boolean = isAuthorized()

    override suspend fun unlink() {
        tokenStorage.deleteToken(TOKEN_KEY)
        prefs.clear()
    }

    override suspend fun isAuthorized(): Boolean =
        prefs.baseUrl.value != null && tokenStorage.getToken(TOKEN_KEY) != null

    override suspend fun createNote(content: String, source: ItemSource?): String {
        val baseUrl = prefs.baseUrl.value
        val token = tokenStorage.getToken(TOKEN_KEY)
        if (baseUrl == null || token == null) {
            throw IntegrationAuthException("Memos is not configured")
        }
        return api.createMemo(CreateMemoRequest(baseUrl, token, content))
    }
}
