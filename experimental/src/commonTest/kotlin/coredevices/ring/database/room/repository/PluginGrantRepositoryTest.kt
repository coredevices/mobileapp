package coredevices.ring.database.room.repository

import coredevices.ring.agent.pebble_plugin.PluginGrant
import coredevices.ring.data.entity.room.PluginGrantDenial
import coredevices.ring.database.room.dao.PluginGrantDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class PluginGrantRepositoryTest {

    private class FakeDao : PluginGrantDao {
        val rows = MutableStateFlow<List<PluginGrantDenial>>(emptyList())
        override fun getAllFlow(): Flow<List<PluginGrantDenial>> = rows
        override suspend fun insert(denial: PluginGrantDenial) {
            rows.update { if (denial in it) it else it + denial }
        }
        override suspend fun delete(denial: PluginGrantDenial) {
            rows.update { it - denial }
        }
    }

    private val things = Uuid.parse("00000000-0000-0000-0002-000000000001")
    private val weather = Uuid.parse("00000000-0000-0000-0002-000000000002")

    @Test
    fun nothingIsDeniedUntilTheUserSaysSo() = runTest {
        val repo = PluginGrantRepository(FakeDao())
        assertEquals(emptySet(), repo.disabledPlugins(PluginGrant.Actions).first())
        assertEquals(emptySet(), repo.disabledPluginsNow(PluginGrant.Sources))
    }

    @Test
    fun denyingOneGrantLeavesTheOtherAlone() = runTest {
        val repo = PluginGrantRepository(FakeDao())

        repo.setPluginGrantEnabled(things, PluginGrant.Actions, false)
        repo.setPluginGrantEnabled(weather, PluginGrant.Sources, false)

        assertEquals(setOf(things), repo.disabledPlugins(PluginGrant.Actions).first())
        assertEquals(setOf(weather), repo.disabledPlugins(PluginGrant.Sources).first())
    }

    @Test
    fun reEnablingRemovesTheDenialAndRepeatsAreIdempotent() = runTest {
        val dao = FakeDao()
        val repo = PluginGrantRepository(dao)

        repo.setPluginGrantEnabled(things, PluginGrant.Actions, false)
        repo.setPluginGrantEnabled(things, PluginGrant.Actions, false)
        assertEquals(1, dao.rows.value.size)

        repo.setPluginGrantEnabled(things, PluginGrant.Actions, true)
        repo.setPluginGrantEnabled(things, PluginGrant.Actions, true)
        assertEquals(emptySet(), repo.disabledPlugins(PluginGrant.Actions).first())
    }

    @Test
    fun aDenialOfOneNamedItemDoesNotDisableTheWholePlugin() = runTest {
        val dao = FakeDao()
        dao.insert(PluginGrantDenial(things, PluginGrant.Actions, name = "create_todo"))

        assertEquals(emptySet(), PluginGrantRepository(dao).disabledPlugins(PluginGrant.Actions).first())
    }
}
