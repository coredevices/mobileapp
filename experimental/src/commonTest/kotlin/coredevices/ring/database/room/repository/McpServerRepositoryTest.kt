package coredevices.ring.database.room.repository

import coredevices.indexai.agent.ServletRepository
import coredevices.indexai.data.McpServerDefinition
import coredevices.indexai.data.entity.mcp.DisabledBuiltinMcp
import coredevices.indexai.data.entity.mcp.HttpMcpServerEntity
import coredevices.indexai.database.dao.DisabledBuiltinMcpDao
import coredevices.indexai.database.dao.HttpMcpServerDao
import coredevices.mcp.client.McpIntegration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class McpServerRepositoryTest {

    private class FakeServletRepository : ServletRepository {
        override fun getAllServlets() = listOf(
            McpServerDefinition(name = "builtin_note", title = "Notes"),
            McpServerDefinition(name = "builtin_reminder", title = "Reminders"),
            McpServerDefinition(name = "builtin_clock", title = "Clock", unavailableReason = "Android only"),
        )

        override fun resolveName(name: String): McpIntegration? = null
    }

    private class FakeDisabledBuiltinDao : DisabledBuiltinMcpDao {
        val rows = MutableStateFlow<List<DisabledBuiltinMcp>>(emptyList())
        override fun getAllFlow(): Flow<List<DisabledBuiltinMcp>> = rows
        override suspend fun insert(disabled: DisabledBuiltinMcp) {
            rows.update { if (disabled in it) it else it + disabled }
        }
        override suspend fun delete(disabled: DisabledBuiltinMcp) {
            rows.update { it - disabled }
        }
    }

    private class FakeHttpServerDao : HttpMcpServerDao {
        val rows = MutableStateFlow<List<HttpMcpServerEntity>>(emptyList())
        override suspend fun insertServer(server: HttpMcpServerEntity): Long {
            val id = if (server.id == 0L) (rows.value.maxOfOrNull { it.id } ?: 0L) + 1 else server.id
            rows.update { it.filter { row -> row.id != id } + server.copy(id = id) }
            return id
        }
        override suspend fun deleteServer(server: HttpMcpServerEntity) {
            rows.update { it.filter { row -> row.id != server.id } }
        }
        override suspend fun setEnabled(id: Long, enabled: Boolean) {
            rows.update { it.map { row -> if (row.id == id) row.copy(enabled = enabled) else row } }
        }
        override fun getAllFlow(): Flow<List<HttpMcpServerEntity>> = rows
    }

    private val disabledDao = FakeDisabledBuiltinDao()
    private val httpDao = FakeHttpServerDao()
    private val repo = McpServerRepository(disabledDao, httpDao, FakeServletRepository())

    private fun server(name: String, enabled: Boolean = true) = HttpMcpServerEntity(
        id = 0L,
        cachedTitle = "",
        name = name,
        url = "https://$name.example.com/mcp",
        streamable = false,
        authHeader = null,
        includedPrompts = emptyList(),
        enabled = enabled,
    )

    private suspend fun enabledNames(): List<String> = repo.enabledServerEntriesFlow().first().map {
        when (it) {
            is McpServerEntry.BuiltinMcpEntry -> it.builtinMcpName
            is McpServerEntry.HttpServerEntry -> it.server.name
        }
    }

    @Test
    fun everyAvailableBuiltinIsEnabledUntilSwitchedOff() = runTest {
        assertEquals(listOf("builtin_note", "builtin_reminder"), enabledNames())

        repo.setBuiltinEnabled("builtin_note", false)
        assertEquals(listOf("builtin_reminder"), enabledNames())

        repo.setBuiltinEnabled("builtin_note", true)
        assertEquals(listOf("builtin_note", "builtin_reminder"), enabledNames())
        assertEquals(emptySet(), repo.disabledBuiltinsFlow().first())
    }

    @Test
    fun unavailableBuiltinsAreNeverServed() = runTest {
        assertEquals(false, "builtin_clock" in enabledNames())
    }

    @Test
    fun onlyEnabledHttpServersAreServed() = runTest {
        repo.addOrUpdateHttpServer(server("on"))
        val off = repo.addOrUpdateHttpServer(server("off", enabled = false))

        assertEquals(listOf("builtin_note", "builtin_reminder", "on"), enabledNames())

        repo.setHttpServerEnabled(httpDao.rows.value.single { it.id == off }, true)
        assertEquals(listOf("builtin_note", "builtin_reminder", "on", "off"), enabledNames())
    }
}
