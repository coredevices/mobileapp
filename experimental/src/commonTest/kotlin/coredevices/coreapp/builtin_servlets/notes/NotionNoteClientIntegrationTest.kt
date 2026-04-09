package coredevices.coreapp.agent.builtin_servlets.notes

import coredevices.experimentalModule
import coredevices.firestore.PebbleUser
import coredevices.firestore.User
import coredevices.firestore.UsersDao
import coredevices.ring.BuildKonfig
import coredevices.ring.agent.integrations.NotionIntegration
import coredevices.ring.api.ApiConfig
import coredevices.ring.api.NotionApi
import coredevices.ring.firestoreModule
import coredevices.ring.mcpModule
import coredevices.util.integrations.IntegrationTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class NotionNoteClientIntegrationTest {

    class FakeUsersDao: UsersDao {
        var todoBlockId: String? = null
        private val _user = MutableStateFlow<PebbleUser?>(PebbleUser(false, User(
            notionToken = null,
            todoBlockId = todoBlockId,
        )))
        override val user: Flow<PebbleUser?> = _user.asStateFlow()

        override suspend fun updateNotionToken(notionToken: String?) {
            TODO("Not yet implemented")
        }

        override suspend fun updateMcpRunToken(mcpRunToken: String?) {
            TODO("Not yet implemented")
        }

        override suspend fun updateTodoBlockId(todoBlockId: String) {
            this.todoBlockId = todoBlockId

        }

        override suspend fun initUserDevToken(rebbleUserToken: String?) {
            TODO("Not yet implemented")
        }

        override suspend fun updateLastConnectedWatch(serial: String) {
            TODO("Not yet implemented")
        }

        override fun init() {
            TODO("Not yet implemented")
        }
    }

    class TestIntegrationTokenStorage: IntegrationTokenStorage {

        override suspend fun saveToken(key: String, token: String) {

        }

        override suspend fun getToken(key: String): String? {
            return BuildKonfig.TESTS_NOTION_TOKEN
        }

        override suspend fun deleteToken(key: String) {

        }
    }

    @BeforeTest
    fun setUp() {
        startKoin {
          modules(
              module {
                  factory {
                      HttpClient().engine
                  } bind HttpClientEngine::class
              },
              firestoreModule,
              experimentalModule,
              mcpModule
          )
        }
    }

    @Ignore
    @Test
    fun createsNote() = runBlocking {
        val fakeUsersDao = FakeUsersDao()
        val api = NotionApi(
            ApiConfig(
                nenyaUrl = "",
                notionOAuthBackendUrl = "",
                notionApiUrl = "https://api.notion.com/v1",
                bugUrl = "",
                version = "",
                tokenUrl = "",
            )
        )
        val client = NotionIntegration(api, fakeUsersDao, TestIntegrationTokenStorage())
        val id = client.createNote("Test note")
        assertNotNull(id)
        assertNotNull(fakeUsersDao.todoBlockId)
        val resultTodo = api.retrieveBlock(fakeUsersDao.user.first()!!.user.notionToken!!, fakeUsersDao.todoBlockId!!)
        assertEquals("Todo", resultTodo.heading1!!.richText.first().text.content)
        val resultBlock = api.retrieveBlock(fakeUsersDao.user.first()!!.user.notionToken!!, id)
        assertFalse(resultBlock.archived)
        assertEquals("Test note", resultBlock.bulletedListItem!!.richText.first().text.content)
    }
}