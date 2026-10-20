package coredevices.pebble.services

import com.russhwolf.settings.MapSettings
import coredevices.database.AppstoreSource
import coredevices.database.AppstoreSourceDao
import io.rebble.libpebblecommon.locker.AppType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppstoreSourcesTest {
    private val settings = MapSettings()
    private val dao = InMemorySourceDao()
    private val cache = EmptyAppstoreCache()

    private suspend fun initialize() {
        AppstoreSourceInitializer(dao, settings, cache).initAppstoreSourcesDB()
    }

    @Test
    fun freshInstallEnablesBothStoresWithoutAnAccount() = runBlocking {
        initialize()

        assertEquals(INITIAL_APPSTORE_SOURCES.map { it.url }, dao.sources.value.map { it.url })
        assertTrue(dao.sources.value.all { it.enabled })
        assertEquals(1, cache.clearCount)
    }

    @Test
    fun upgradeEnablesRebbleWithoutChangingOtherSources() = runBlocking {
        val pebble = INITIAL_APPSTORE_SOURCES.first().copy(enabled = false)
        val rebble = INITIAL_APPSTORE_SOURCES.last().copy(enabled = false)
        val custom = AppstoreSource(url = "https://example.com/api", title = "Custom", enabled = false)
        listOf(pebble, rebble, custom).forEach { dao.insertSource(it) }
        val before = dao.sources.value

        initialize()

        assertEquals(before.map { if (it.isRebbleFeed()) it.copy(enabled = true) else it }, dao.sources.value)
        assertEquals(0, cache.clearCount)
    }

    @Test
    fun disabledRebbleStaysDisabledOnSubsequentStartup() = runBlocking {
        initialize()
        val rebble = dao.sources.value.first { it.isRebbleFeed() }
        dao.setSourceEnabled(rebble.id, false)

        // A new initializer uses the same persisted settings and source rows.
        initialize()

        assertFalse(dao.getSourceById(rebble.id)!!.enabled)
        assertEquals(1, cache.clearCount)
    }

    @Test
    fun legacySourceMigrationKeepsCustomSourcesAndEnablesRebble() = runBlocking {
        val custom = AppstoreSource(url = "https://example.com/api", title = "Custom")
        val customId = dao.insertSource(custom).toInt()
        dao.insertSource(AppstoreSource(url = PEBBLE_FEED_URL, title = "Old Pebble Store"))
        dao.insertSource(AppstoreSource(url = REBBLE_FEED_URL, title = "Rebble", enabled = false))

        initialize()

        assertEquals(custom.copy(id = customId), dao.getSourceById(customId))
        assertEquals(3, dao.sources.value.size)
        assertTrue(dao.sources.value.first { it.isRebbleFeed() }.enabled)
        assertEquals(1, cache.clearCount)
    }
}

private class InMemorySourceDao : AppstoreSourceDao {
    val sources = MutableStateFlow<List<AppstoreSource>>(emptyList())
    private var nextId = 1

    override suspend fun insertSource(source: AppstoreSource): Long {
        val id = nextId++
        sources.value += source.copy(id = id)
        return id.toLong()
    }

    override fun getAllSources(): Flow<List<AppstoreSource>> = sources
    override fun getAllEnabledSourcesFlow(): Flow<List<AppstoreSource>> =
        sources.map { entries -> entries.filter { it.enabled } }
    override suspend fun getAllEnabledSources(): List<AppstoreSource> = sources.value.filter { it.enabled }
    override suspend fun deleteSourceById(sourceId: Int) {
        sources.value = sources.value.filterNot { it.id == sourceId }
    }
    override suspend fun setSourceEnabled(sourceId: Int, isEnabled: Boolean) {
        sources.value = sources.value.map { if (it.id == sourceId) it.copy(enabled = isEnabled) else it }
    }
    override suspend fun getSourceById(sourceId: Int): AppstoreSource? =
        sources.value.firstOrNull { it.id == sourceId }
}

private class EmptyAppstoreCache : AppstoreCache {
    var clearCount = 0
    override suspend fun clearCache() { clearCount++ }
    override suspend fun readApp(id: String, parameters: Map<String, String>, source: AppstoreSource): StoreAppResponse? = null
    override suspend fun writeApp(app: StoreAppResponse, parameters: Map<String, String>, source: AppstoreSource) = Unit
    override suspend fun readCategories(type: AppType, source: AppstoreSource): List<StoreCategory>? = null
    override suspend fun writeCategories(categories: List<StoreCategory>, type: AppType, source: AppstoreSource) = Unit
    override suspend fun readHome(type: AppType, source: AppstoreSource, parameters: Map<String, String>): AppStoreHome? = null
    override suspend fun writeHome(home: AppStoreHome, type: AppType, source: AppstoreSource, parameters: Map<String, String>) = Unit
}
