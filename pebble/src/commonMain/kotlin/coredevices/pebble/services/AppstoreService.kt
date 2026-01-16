package coredevices.pebble.services

import co.touchlab.kermit.Logger
import com.algolia.client.api.SearchClient
import com.algolia.client.exception.AlgoliaApiException
import com.algolia.client.model.search.SearchParamsObject
import com.algolia.client.model.search.TagFilters
import coredevices.database.AppstoreSource
import coredevices.pebble.Platform
import coredevices.pebble.account.FirestoreLockerEntry
import coredevices.pebble.services.AppstoreService.BulkFetchParams.Companion.encodeToJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.http.parseUrl
import io.ktor.util.sha1
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.util.getTempFilePath
import io.rebble.libpebblecommon.web.LockerEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlin.uuid.Uuid

class AppstoreService(
    private val platform: Platform,
    private val appContext: AppContext,
    httpClient: HttpClient,
    val source: AppstoreSource
) {
    companion object {
        private val STORE_APP_CACHE_AGE = 4.hours
    }
    private val scope = CoroutineScope(Dispatchers.Default)

    private val logger =
        Logger.withTag("AppstoreService-${parseUrl(source.url)?.host ?: "unknown"}")
    private val httpClient = httpClient.config {
        install(HttpCache)
    }
    private val searchClient = source.algoliaAppId?.let { appId ->
        source.algoliaApiKey?.let { apiKey ->
            SearchClient(appId, apiKey)
        }
    }
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private fun calculateAppCacheKey(id: String, parameters: Map<String, String>): String {
        val data = source.url + id + parameters.entries.sortedBy { it.key }
            .joinToString(separator = "&") { "${it.key}=${it.value}" }
        val hash = sha1(data.encodeToByteArray())
        return hash.toHexString()
    }

    private fun supportsBulkFetch(): Boolean = source.url.startsWith("https://appstore-api.repebble.com/")

    suspend fun fetchAppStoreApps(
        entries: List<FirestoreLockerEntry>,
        useCache: Boolean = true,
    ): List<LockerEntry> {
        return if (!supportsBulkFetch()) {
            fetchAppStoreAppsOneByOne(entries, useCache)
        } else {
            fetchAppStoreAppsInBulk(entries)
        }
    }

    @Serializable
    data class BulkFetchParams(
        val ids: List<String>,
//        val hardware: String? = null,
    ) {
        companion object {
            fun BulkFetchParams.encodeToJson(): String = Json.encodeToString(this)
        }
    }

    private suspend fun fetchAppStoreAppsInBulk(
        entries: List<FirestoreLockerEntry>,
    ): List<LockerEntry> {
        return entries.chunked(500).also {
            logger.d { "Bulk fetching locker entries in ${it.size} chunks" }
        }.flatMap { lockerEntries ->
            val result = lockerEntries.flatMap { lockerEntry ->
                try {
                    httpClient.post(url = Url("${source.url}/v1/apps/bulk")) {
                        header("Content-Type", "application/json")
                        setBody(BulkFetchParams(entries.map { it.appstoreId }).encodeToJson())
                    }.takeIf { it.status.isSuccess() }?.body<BulkStoreResponse>()
                        ?.data?.map {
                            it.toLockerEntry(
                                sourceUrl = lockerEntry.appstoreSource,
                                timelineToken = lockerEntry.timelineToken,
                            )
                        } ?: emptyList()
                } catch (e: IOException) {
                    logger.w(e) { "Error loading app store app" }
                    emptyList()
                }
            }
            result
        }.filterNotNull()
    }

    private suspend fun fetchAppStoreAppsOneByOne(
        entries: List<FirestoreLockerEntry>,
        useCache: Boolean = true,
    ): List<LockerEntry> {
        return entries.chunked(10).also {
           logger.d { "Fetching locker entries in ${it.size} chunks" }
        }.flatMap { lockerEntries ->
            val result = lockerEntries.map { lockerEntry ->
                scope.async {
                    fetchAppStoreApp(
                        lockerEntry.appstoreId,
                        hardwarePlatform = null,
                        useCache = useCache
                    )?.data?.firstOrNull()?.toLockerEntry(
                        sourceUrl = lockerEntry.appstoreSource,
                        timelineToken = lockerEntry.timelineToken,
                    )
                }
            }.awaitAll()
            if (entries.size > 20) {
                delay(50)
            }
            result
        }.filterNotNull()
    }

    suspend fun fetchAppStoreApp(
        id: String,
        hardwarePlatform: WatchType?,
        useCache: Boolean = true,
    ): StoreAppResponse? {
        val cacheDir = getTempFilePath(appContext, "locker_cache")
        try {
            if (!SystemFileSystem.exists(cacheDir)) {
                SystemFileSystem.createDirectories(cacheDir, false)
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to create cache directory: $cacheDir" }
        }

        val parameters = buildMap {
            put("platform", platform.storeString())
            if (hardwarePlatform != null) {
                put("hardware", hardwarePlatform.codename)
            }
            //            "firmware_version" to "",
            //            "filter_hardware" to "true",
        }

        val hash = calculateAppCacheKey(id, parameters)
        val cacheFile = Path(cacheDir, "$hash.json")
        var result: StoreAppResponse? = null
        if (useCache) {
            try {
                if (SystemFileSystem.exists(cacheFile)) {
                    SystemFileSystem.source(cacheFile).buffered().use {
                        val cached: CachedStoreAppResponse = json.decodeFromString(it.readString())
                        if (Clock.System.now() - cached.lastUpdated < STORE_APP_CACHE_AGE) {
                            result = cached.response
                        }
                    }
                }
            } catch (e: Exception) {
                logger.w(e) { "Failed to read cached appstore app for $id" }
            }
        }
        if (result == null) {
            result = try {
                httpClient.get(url = Url("${source.url}/v1/apps/id/$id")) {
                    parameters.forEach {
                        parameter(it.key, it.value)
                    }
                }.takeIf { it.status.isSuccess() }?.body() ?: return null
            } catch (e: IOException) {
                logger.w(e) { "Error loading app store app" }
                return null
            }
            try {
                SystemFileSystem.sink(cacheFile).buffered().use {
                    val toCache = CachedStoreAppResponse(
                        response = result,
                        lastUpdated = Clock.System.now()
                    )
                    it.writeString(json.encodeToString(toCache))
                }
            } catch (e: Exception) {
                logger.w(e) { "Failed to write cached appstore app for $id" }
            }
        }
        return result
    }

    suspend fun fetchAppStoreHome(type: AppType, hardwarePlatform: WatchType?): AppStoreHome? {
        val typeString = type.storeString()
        val parameters = buildMap {
            set("platform", platform.storeString())
            if (hardwarePlatform != null) {
                set("hardware", hardwarePlatform.codename)
            }
//            set("firmware_version", "")
            set("filter_hardware", "true")
        }
        val home = try {
            httpClient.get(
                url = Url("${source.url}/v1/home/$typeString")
            ) {
                parameters.forEach {
                    parameter(it.key, it.value)
                }
            }
        } catch (e: IOException) {
            logger.w(e) { "Error loading app store home" }
            return null
        }
            .takeIf {
                logger.v { "${it.call.request.url}" }
                if (!it.status.isSuccess()) {
                    logger.w { "Failed to fetch home of type ${type.code}, status: ${it.status}, source = ${source.url}" }
                    false
                } else {
                    true
                }
            }?.body<AppStoreHome>()
        home?.let {
            cacheCategories(home.categories, type)
        }
        return home?.copy(applications = home.applications.filter { app ->
            try {
                if (Uuid.parse(app.uuid) == Uuid.NIL) {
                    logger.w { "App ${app.title} has NIL UUID, skipping" }
                    false
                } else {
                    true
                }
            } catch (_: IllegalArgumentException) {
                logger.w { "App ${app.title} has invalid UUID ${app.uuid}, skipping" }
                false
            }
        })
    }

    private fun calculateCategoryCacheKey(appType: AppType): String {
        val data = source.url + appType.code
        val hash = sha1(data.encodeToByteArray())
        return hash.toHexString()
    }

    private fun cacheCategories(categories: List<StoreCategory>, type: AppType) {
        val cacheDir = getTempFilePath(appContext, "category_cache")
        try {
            if (!SystemFileSystem.exists(cacheDir)) {
                SystemFileSystem.createDirectories(cacheDir, false)
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to create category cache directory: $cacheDir" }
            return
        }
        val hash = calculateCategoryCacheKey(type)
        val cacheFile = Path(cacheDir, "$hash.json")
        try {
            SystemFileSystem.sink(cacheFile).buffered().use {
                it.writeString(Json.encodeToString(categories))
            }
        } catch (e: Exception) {
            logger.w(e) { "Failed to write cached categories for type ${type.code}" }
        }
    }

    suspend fun fetchCategories(type: AppType): List<StoreCategory> {
        val cacheDir = getTempFilePath(appContext, "category_cache")
        try {
            if (!SystemFileSystem.exists(cacheDir)) {
                SystemFileSystem.createDirectories(cacheDir, false)
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to create category cache directory: $cacheDir" }
        }
        val hash = calculateCategoryCacheKey(type)
        val cacheFile = Path(cacheDir, "$hash.json")
        try {
            if (SystemFileSystem.exists(cacheFile)) {
                SystemFileSystem.source(cacheFile).buffered().use {
                    val cachedJson = it.readString()
                    val cachedCategories: List<StoreCategory> = Json.decodeFromString(cachedJson)
                    if (cachedCategories.isNotEmpty()) {
                        return cachedCategories
                    }
                }
            }
        } catch (e: Exception) {
            logger.w(e) { "Failed to read cached categories for type ${type.code}" }
        }

        val categories = fetchAppStoreHome(type, null)?.categories ?: emptyList()
        cacheCategories(categories, type)
        return categories
    }

    suspend fun fetchAppStoreCollection(
        path: String,
        type: AppType?,
        hardwarePlatform: WatchType?,
        offset: Int
    ): StoreAppResponse? {
        val parameters = buildMap {
            put("platform", platform.storeString())
            if (hardwarePlatform != null) {
                put("hardware", hardwarePlatform.codename)
            }
            put("offset", offset.toString())
        }
        val url = buildString {
            append("${source.url}/v1/apps/$path")
            if (type != null) {
                append("/${type.storeString()}")
            }
        }
        logger.v { "get ${url} with parameters $parameters" }
        return try {
            httpClient.get(url = Url(url)) {
                parameters.forEach {
                    parameter(it.key, it.value)
                }
            }.takeIf {
                logger.v { "${it.call.request.url}" }
                if (!it.status.isSuccess()) {
                    logger.w { "Failed to fetch collection $path of type ${type?.code}, status: ${it.status}" }
                    false
                } else {
                    true
                }
            }?.body()
        } catch (e: IOException) {
            logger.w(e) { "Error loading app store collection" }
            null
        }
    }

    suspend fun searchUuid(uuid: String): String? {
        if (searchClient == null) {
            logger.w { "searchClient is null, cannot search" }
            return null
        }
        return try {
            val response = searchClient.searchSingleIndex(
                indexName = source.algoliaIndexName!!,
                searchParams = SearchParamsObject(
                    query = uuid,
                ),
            )
            val found = response.hits.mapNotNull {
                val props = it.additionalProperties ?: return@mapNotNull null
                val jsonText = JsonObject(props)
                try {
                    json.decodeFromJsonElement(
                        StoreSearchResult.serializer(),
                        jsonText,
                    )
                } catch (e: Exception) {
                    logger.w(e) { "error decoding search result" }
                    null
                }
            }.firstOrNull {
                it.uuid.lowercase() == uuid
            }
            found?.id
        } catch (e: AlgoliaApiException) {
            logger.w(e) { "searchSingleIndex" }
            null
        } catch (e: IllegalStateException) {
            logger.w(e) { "searchSingleIndex" }
            null
        }
    }

    suspend fun search(search: String, type: AppType? = null): List<StoreSearchResult> {
        if (searchClient == null) {
            logger.w { "searchClient is null, cannot search" }
            return emptyList()
        }

        return try {
            searchClient.searchSingleIndex(
                indexName = source.algoliaIndexName!!,
//                searchParams = SearchParams.of(SearchParamsString(search)),
                searchParams = SearchParamsObject(
                    query = search,
                    tagFilters = type?.let { TagFilters.of(type.code) },
                ),
            ).hits.mapNotNull {
                it.additionalProperties?.let { props ->
                    val jsonText = JsonObject(props)
//                    logger.v { "jsonText: $jsonText" }
                    try {
                        json.decodeFromJsonElement(
                            StoreSearchResult.serializer(),
                            jsonText,
                        )
                    } catch (e: Exception) {
                        logger.w(e) { "error decoding search result (source ${source.url})" }
                        null
                    }
                }
            }
        } catch (e: Exception) {
            logger.w(e) { "error searching for $search" }
            emptyList()
        }
    }

    @Serializable
    private data class CachedStoreAppResponse(
        val response: StoreAppResponse,
        val lastUpdated: Instant
    )
}