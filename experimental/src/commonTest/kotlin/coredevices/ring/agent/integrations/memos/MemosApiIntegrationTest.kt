package coredevices.ring.agent.integrations.memos

import coredevices.ring.BuildKonfig
import coredevices.ring.api.ApiConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Talks to a real Memos server. Set TESTS_MEMOS_URL and TESTS_MEMOS_TOKEN (env var or gradle
 * property) and drop the @Ignore to run one of these.
 */
class MemosApiIntegrationTest {

    private val baseUrl = BuildKonfig.TESTS_MEMOS_URL.trimEnd('/')
    private val token = BuildKonfig.TESTS_MEMOS_TOKEN

    private fun api() = MemosApiImpl(
        ApiConfig(
            nenyaUrl = "",
            notionOAuthBackendUrl = "",
            notionApiUrl = "",
            bugUrl = "",
            version = "",
            tokenUrl = "",
        )
    )

    @BeforeTest
    fun setUp() {
        stopKoin()
        startKoin {
            modules(
                module {
                    factory {
                        HttpClient().engine
                    } bind HttpClientEngine::class
                }
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Ignore
    @Test
    fun acceptsValidCredentials() = runBlocking {
        assertTrue(api().validateCredentials(baseUrl, token))
    }

    @Ignore
    @Test
    fun rejectsABadToken() = runBlocking {
        assertFalse(api().validateCredentials(baseUrl, "definitely-not-a-real-token"))
    }

    @Ignore
    @Test
    fun createsAMemo() = runBlocking {
        val name = api().createMemo(
            CreateMemoRequest(
                baseUrl = baseUrl,
                token = token,
                content = "Index integration test — safe to delete",
            )
        )
        assertTrue(name.isNotEmpty())
    }
}
