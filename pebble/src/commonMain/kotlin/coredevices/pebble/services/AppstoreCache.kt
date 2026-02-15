package coredevices.pebble.services

import co.touchlab.kermit.Logger
import coredevices.database.AppstoreSource
import io.ktor.util.sha1
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.util.getTempFilePath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

interface AppstoreCache {
    suspend fun readApp(
        id: String,
        parameters: Map<String, String>,
        source: AppstoreSource,
    ): StoreAppResponse?

    suspend fun writeApp(
        app: StoreAppResponse,
        parameters: Map<String, String>,
        source: AppstoreSource,
    )

    suspend fun writeCategories(
        categories: List<StoreCategory>,
        type: AppType,
        source: AppstoreSource,
    )

    suspend fun readCategories(
        type: AppType,
        source: AppstoreSource,
    ): List<StoreCategory>?

    suspend fun writeHome(
        home: AppStoreHome,
        type: AppType,
        source: AppstoreSource,
        parameters: Map<String, String>,
    )

    suspend fun readHome(
        type: AppType,
        source: AppstoreSource,
        parameters: Map<String, String>,
    ): AppStoreHome?

    suspend fun clearCache()
}

class RealAppstoreCache(
    appContext: AppContext,
) : AppstoreCache {
    private val appCacheDir by lazy { getTempFilePath(appContext, "locker_cache") }
    private val categoryCacheDir by lazy {  getTempFilePath(appContext, "category_cache") }
    private val homeCacheDir by lazy {  getTempFilePath(appContext, "home_cache") }
    private val logger = Logger.withTag("AppstoreCache")
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    companion object {
        private val STORE_APP_CACHE_AGE = 4.hours
        private val STORE_HOME_CACHE_AGE = 4.hours
    }

    // Apps

    override suspend fun readApp(
        id: String,
        parameters: Map<String, String>,
        source: AppstoreSource,
    ): StoreAppResponse? = withContext(Dispatchers.Default) {
        val cacheFile = cacheFileForApp(id, parameters, source)
        try {
            if (SystemFileSystem.exists(cacheFile)) {
                SystemFileSystem.source(cacheFile).buffered().use {
                    val cached: CachedStoreAppResponse = json.decodeFromString(it.readString())
                    if (Clock.System.now() - cached.lastUpdated < STORE_APP_CACHE_AGE) {
                        return@withContext cached.response
                    }
                }
            }
        } catch (e: Exception) {
            logger.w(e) { "Failed to read cached appstore app for $id" }
        }
        return@withContext null
    }

    override suspend fun writeApp(
        app: StoreAppResponse,
        parameters: Map<String, String>,
        source: AppstoreSource,
    ) = withContext(Dispatchers.Default) {
        val id = app.data.firstOrNull()?.id ?: return@withContext
        val cacheFile = cacheFileForApp(id, parameters, source)
        try {
            SystemFileSystem.sink(cacheFile).buffered().use {
                val toCache = CachedStoreAppResponse(
                    response = app,
                    lastUpdated = Clock.System.now(),
                )
                it.writeString(json.encodeToString(toCache))
            }
        } catch (e: Exception) {
            logger.w(e) { "Failed to write cached appstore app for ${app.data.firstOrNull()?.id}" }
        }
    }

    private fun createCacheDir(dir: Path) {
        try {
            if (!SystemFileSystem.exists(dir)) {
                SystemFileSystem.createDirectories(dir, false)
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to create cache directory: $dir" }
        }
    }

    private fun calculateAppCacheKey(
        id: String,
        parameters: Map<String, String>,
        source: AppstoreSource,
    ): String {
        val data = source.url + id + parameters.entries.sortedBy { it.key }
            .joinToString(separator = "&") { "${it.key}=${it.value}" }
        val hash = sha1(data.encodeToByteArray())
        return hash.toHexString()
    }

    private fun cacheFileForApp(
        id: String,
        parameters: Map<String, String>,
        source: AppstoreSource,
    ): Path {
        createCacheDir(appCacheDir)
        val hash = calculateAppCacheKey(id, parameters, source)
        return Path(appCacheDir, "$hash.json")
    }

    // Categories

    override suspend fun writeCategories(
        categories: List<StoreCategory>,
        type: AppType,
        source: AppstoreSource,
    ) = withContext(Dispatchers.Default) {
        val cacheFile = cacheFileForCategories(type, source)
        try {
            SystemFileSystem.sink(cacheFile).buffered().use {
                it.writeString(Json.encodeToString(categories))
            }
        } catch (e: Exception) {
            logger.w(e) { "Failed to write cached categories for type ${type.code}" }
        }
    }

    override suspend fun readCategories(
        type: AppType,
        source: AppstoreSource,
    ): List<StoreCategory>? = withContext(Dispatchers.Default) {
        val cacheFile = cacheFileForCategories(type, source)
        try {
            if (SystemFileSystem.exists(cacheFile)) {
                SystemFileSystem.source(cacheFile).buffered().use {
                    val cachedJson = it.readString()
                    val cachedCategories: List<StoreCategory> = Json.decodeFromString(cachedJson)
                    if (cachedCategories.isNotEmpty()) {
                        return@withContext cachedCategories
                    }
                }
            }
        } catch (e: Exception) {
            logger.w(e) { "Failed to read cached categories for type ${type.code}" }
        }
        return@withContext null
    }

    private fun cacheFileForCategories(appType: AppType, source: AppstoreSource): Path {
        createCacheDir(categoryCacheDir)
        val hash = calculateCategoryCacheKey(appType, source)
        return Path(categoryCacheDir, "$hash.json")
    }

    private fun calculateCategoryCacheKey(appType: AppType, source: AppstoreSource): String {
        val data = source.url + appType.code
        val hash = sha1(data.encodeToByteArray())
        return hash.toHexString()
    }

    // Home

    private fun cacheFileForHome(
        appType: AppType,
        source: AppstoreSource,
        parameters: Map<String, String>,
    ): Path {
        createCacheDir(homeCacheDir)
        val hash = calculateHomeCacheKey(appType, source, parameters)
        return Path(homeCacheDir, "$hash.json")
    }

    private fun calculateHomeCacheKey(
        appType: AppType,
        source: AppstoreSource,
        parameters: Map<String, String>,
    ): String {
        val data = source.url + appType.code + parameters.entries.sortedBy { it.key }
        val hash = sha1(data.encodeToByteArray())
        return hash.toHexString()
    }

    override suspend fun writeHome(
        home: AppStoreHome,
        type: AppType,
        source: AppstoreSource,
        parameters: Map<String, String>,
    ) = withContext(Dispatchers.Default) {
        val cacheFile = cacheFileForHome(type, source, parameters)
        val toCache = CachedStoreHome(
            home = home,
            lastUpdated = Clock.System.now(),
        )
        try {
            SystemFileSystem.sink(cacheFile).buffered().use {
                it.writeString(Json.encodeToString(toCache))
            }
        } catch (e: Exception) {
            logger.w(e) { "Failed to write cached home for type ${type.code}" }
        }
    }

    override suspend fun readHome(
        type: AppType,
        source: AppstoreSource,
        parameters: Map<String, String>,
    ): AppStoreHome? = withContext(Dispatchers.Default) {
        val cacheFile = cacheFileForHome(type, source, parameters)
        try {
            if (SystemFileSystem.exists(cacheFile)) {
                SystemFileSystem.source(cacheFile).buffered().use {
                    val cachedJson = it.readString()
                    val cached: CachedStoreHome = Json.decodeFromString(cachedJson)
                    if (Clock.System.now() - cached.lastUpdated < STORE_HOME_CACHE_AGE) {
                        return@withContext cached.home
                    }
                }
            }
        } catch (e: Exception) {
            logger.w(e) { "Failed to read cached home for type ${type.code}" }
        }
        return@withContext null
    }

    // Maintainence

    override suspend fun clearCache() = withContext(Dispatchers.Default) {
        logger.i { "Clearing cache!" }
        homeCacheDir.clear()
        categoryCacheDir.clear()
        appCacheDir.clear()
    }
}

private suspend fun Path.clear() {
    try {
        if (SystemFileSystem.exists(this@clear)) {
            // list handles getting all children in the directory
            SystemFileSystem.list(this@clear).forEach { child ->
                // Use SystemFileSystem.delete to remove each file or directory
                // mustSetDeleteOnExit is usually false for immediate deletion
                SystemFileSystem.delete(child, mustExist = false)
            }
        }
    } catch (e: Exception) {
        Logger.withTag("AppstoreCache").e(e) { "Failed to clear directory: ${this@clear}" }
    }
}

@Serializable
private data class CachedStoreAppResponse(
    val response: StoreAppResponse,
    val lastUpdated: Instant
)

@Serializable
private data class CachedStoreHome(
    val home: AppStoreHome,
    val lastUpdated: Instant
)
