package coredevices.ring.agent.integrations.memos

import com.russhwolf.settings.MapSettings
import coredevices.util.integrations.IntegrationAuthException
import coredevices.util.integrations.IntegrationTokenStorage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemosIntegrationTest {

    private class FakeMemosApi(
        private val result: String = "memos/42",
        private val credentialsValid: Boolean = true,
        private val createFails: Boolean = false,
    ) : MemosApi {
        val calls = mutableListOf<CreateMemoRequest>()
        val validations = mutableListOf<Pair<String, String>>()
        override suspend fun createMemo(request: CreateMemoRequest): String {
            calls += request
            if (createFails) error("server said no")
            return result
        }
        override suspend fun validateCredentials(baseUrl: String, token: String): Boolean {
            validations += baseUrl to token
            return credentialsValid
        }
    }

    private class FakeTokenStorage : IntegrationTokenStorage {
        private val tokens = mutableMapOf<String, String>()
        override suspend fun saveToken(key: String, token: String) { tokens[key] = token }
        override suspend fun getToken(key: String): String? = tokens[key]
        override suspend fun deleteToken(key: String) { tokens.remove(key) }
    }

    private fun configured(
        api: MemosApi = FakeMemosApi(),
        prefs: MemosPreferences = MemosPreferences(MapSettings()),
        tokens: FakeTokenStorage = FakeTokenStorage(),
    ) = Triple(MemosIntegration(api, prefs, tokens), prefs, tokens)

    @Test
    fun createNoteSendsContentToTheConfiguredServer() = runTest {
        val api = FakeMemosApi()
        val (integration, prefs, tokens) = configured(api = api)
        prefs.setBaseUrl("https://memos.example.com")
        tokens.saveToken(MemosIntegration.TOKEN_KEY, "tok-abc")

        val id = integration.createNote("remember the milk", source = null)

        assertEquals("memos/42", id)
        assertEquals(1, api.calls.size)
        val call = api.calls.single()
        assertEquals("https://memos.example.com", call.baseUrl)
        assertEquals("tok-abc", call.token)
        assertEquals("remember the milk", call.content)
    }

    @Test
    fun createNoteFailsLoudlyWithoutAServer() = runTest {
        val api = FakeMemosApi()
        val (integration, _, tokens) = configured(api = api)
        tokens.saveToken(MemosIntegration.TOKEN_KEY, "tok-abc")

        assertFailsWith<IntegrationAuthException> { integration.createNote("orphan", source = null) }
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun createNoteFailsLoudlyWithoutAToken() = runTest {
        val api = FakeMemosApi()
        val (integration, prefs, _) = configured(api = api)
        prefs.setBaseUrl("https://memos.example.com")

        assertFailsWith<IntegrationAuthException> { integration.createNote("orphan", source = null) }
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun aRejectedNoteIsNotReportedAsSaved() = runTest {
        val api = FakeMemosApi(createFails = true)
        val (integration, prefs, tokens) = configured(api = api)
        prefs.setBaseUrl("https://memos.example.com")
        tokens.saveToken(MemosIntegration.TOKEN_KEY, "tok-abc")

        assertFailsWith<IllegalStateException> { integration.createNote("dropped", source = null) }
    }

    @Test
    fun authorizedOnlyWhenServerAndTokenAreBothPresent() = runTest {
        val (integration, prefs, tokens) = configured()
        assertFalse(integration.isAuthorized())

        prefs.setBaseUrl("https://memos.example.com")
        assertFalse(integration.isAuthorized())

        tokens.saveToken(MemosIntegration.TOKEN_KEY, "tok-abc")
        assertTrue(integration.isAuthorized())
    }

    @Test
    fun unlinkForgetsServerAndToken() = runTest {
        val (integration, prefs, tokens) = configured()
        prefs.setBaseUrl("https://memos.example.com")
        tokens.saveToken(MemosIntegration.TOKEN_KEY, "tok-abc")

        integration.unlink()

        assertNull(prefs.baseUrl.value)
        assertNull(tokens.getToken(MemosIntegration.TOKEN_KEY))
        assertFalse(integration.isAuthorized())
    }

    @Test
    fun connectingVerifiesCredentialsBeforeSavingThem() = runTest {
        val api = FakeMemosApi()
        val (integration, prefs, tokens) = configured(api = api)

        assertTrue(integration.connect("https://memos.example.com/", "tok-abc"))

        assertEquals("https://memos.example.com" to "tok-abc", api.validations.single())
        assertEquals("https://memos.example.com", prefs.baseUrl.value)
        assertEquals("tok-abc", tokens.getToken(MemosIntegration.TOKEN_KEY))
    }

    @Test
    fun rejectedCredentialsAreNotSaved() = runTest {
        val api = FakeMemosApi(credentialsValid = false)
        val (integration, prefs, tokens) = configured(api = api)

        assertFalse(integration.connect("https://memos.example.com", "bad-token"))

        assertNull(prefs.baseUrl.value)
        assertNull(tokens.getToken(MemosIntegration.TOKEN_KEY))
    }

    @Test
    fun aFailedConnectLeavesAnEarlierServerIntact() = runTest {
        val api = FakeMemosApi(credentialsValid = false)
        val (integration, prefs, tokens) = configured(api = api)
        prefs.setBaseUrl("https://good.example.com")
        tokens.saveToken(MemosIntegration.TOKEN_KEY, "good-token")

        assertFalse(integration.connect("https://bad.example.com", "bad-token"))

        assertEquals("https://good.example.com", prefs.baseUrl.value)
        assertEquals("good-token", tokens.getToken(MemosIntegration.TOKEN_KEY))
    }
}
