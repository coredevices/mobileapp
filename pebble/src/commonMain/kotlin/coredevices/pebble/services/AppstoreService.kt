package coredevices.pebble.services

import androidx.paging.PagingSource
import androidx.paging.PagingState
import co.touchlab.kermit.Logger
import com.algolia.client.api.SearchClient
import com.algolia.client.exception.AlgoliaApiException
import com.algolia.client.model.search.SearchParamsObject
import com.algolia.client.model.search.TagFilters
import coredevices.database.AppstoreCollection
import coredevices.database.AppstoreCollectionDao
import coredevices.database.AppstoreSource
import coredevices.database.HeartEntity
import coredevices.database.HeartsDao
import coredevices.pebble.Platform
import coredevices.pebble.account.FirestoreLockerEntry
import coredevices.pebble.services.AppstoreService.BulkFetchParams.Companion.encodeToJson
import coredevices.pebble.services.PebbleHttpClient.Companion.delete
import coredevices.pebble.services.PebbleHttpClient.Companion.post
import coredevices.pebble.ui.CommonApp
import coredevices.pebble.ui.asCommonApp
import coredevices.pebble.ui.cachedCategoriesOrDefaults
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.http.parseUrl
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.web.LockerEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid

class AppstoreService(
    private val platform: Platform,
    httpClient: HttpClient,
    val source: AppstoreSource,
    private val cache: AppstoreCache,
    private val appstoreCollectionDao: AppstoreCollectionDao,
    private val pebbleAccountProvider: PebbleAccountProvider,
    private val pebbleWebServices: PebbleWebServices,
    private val pebbleHttpClient: PebbleHttpClient,
    private val heartsDao: HeartsDao,
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val logger =
        Logger.withTag("AppstoreService-${parseUrl(source.url)?.host ?: "unknown"}")
    private val httpClient = httpClient.config {
        install(HttpCache)
        install(HttpTimeout) {
            requestTimeoutMillis = 15000L
            connectTimeoutMillis = 5000L
            socketTimeoutMillis = 10000L
        }
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

    private fun supportsBulkFetch(): Boolean = !source.isRebbleFeed()

    suspend fun fetchAppStoreApps(
        entries: List<FirestoreLockerEntry>,
        useCache: Boolean = true,
    ): List<LockerEntry> {
        return if (pebbleAccountProvider.isLoggedIn() && source.isRebbleFeed()) {
            fetchAppStoreAppsFromPwsLocker()
        } else if (!supportsBulkFetch()) {
            fetchAppStoreAppsOneByOne(entries, useCache)
        } else {
            fetchAppStoreAppsInBulk(entries)
        }
    }

    private suspend fun fetchAppStoreAppsFromPwsLocker(): List<LockerEntry> {
        val locker = pebbleWebServices.fetchPebbleLocker()
        if (locker == null) {
            logger.w { "Failed to fetch Pebble locker" }
            return emptyList()
        } else {
            return locker.applications
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
        val entriesByAppstoreId = entries.associateBy { it.appstoreId }
        return entries.chunked(500).also {
            logger.d { "Bulk fetching locker entries in ${it.size} chunks" }
        }.flatMap { lockerEntries ->
            try {
                logger.v { "Fetching chunk size = ${lockerEntries.size}" }
                httpClient.post(url = Url("${source.url}/v1/apps/bulk")) {
                    header("Content-Type", "application/json")
                    setBody(BulkFetchParams(lockerEntries.map { it.appstoreId }).encodeToJson())
                }.takeIf { it.status.isSuccess() }?.body<BulkStoreResponse>()
                    ?.data?.mapNotNull { app ->
                        val matchingEntry = entriesByAppstoreId[app.id]
                        app.toLockerEntry(
                            sourceUrl = matchingEntry?.appstoreSource ?: source.url,
                            timelineToken = matchingEntry?.timelineToken,
                        )
                    } ?: emptyList()
            } catch (e: IOException) {
                logger.w(e) { "Error loading app store app" }
                emptyList()
            }
        }
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

    suspend fun addHeart(url: String, appId: String): Boolean {
        val success = when (source.url) {
            PEBBLE_FEED_URL -> {
                pebbleHttpClient.post(url = url, auth = HttpClientAuthType.Core)?.status?.isSuccess() ?: false
            }
            REBBLE_FEED_URL -> {
                pebbleHttpClient.post(url = url, auth = HttpClientAuthType.Pebble)?.status?.isSuccess() ?: false
            }
            else -> false
        }
        if (success) {
            heartsDao.addHeart(HeartEntity(sourceId = source.id, appId = appId))
        }
        return success
    }

    suspend fun removeHeart(url: String, appId: String): Boolean {
        val success = when (source.url) {
            PEBBLE_FEED_URL -> {
                pebbleHttpClient.delete(url = url, auth = HttpClientAuthType.Core)
            }
            REBBLE_FEED_URL -> {
                pebbleHttpClient.post(url = url, auth = HttpClientAuthType.Pebble)?.status?.isSuccess() ?: false
            }
            else -> false
        }
        if (success) {
            heartsDao.removeHeart(HeartEntity(sourceId = source.id, appId = appId))
        }
        return success
    }

    fun isLoggedIn(): Boolean {
        return when (source.url) {
            PEBBLE_FEED_URL -> {
                Firebase.auth.currentUser != null
            }
            REBBLE_FEED_URL -> {
                pebbleAccountProvider.isLoggedIn()
            }
            else -> false
        }
    }

    suspend fun fetchHearts(): List<String>? {
        return when (source.url) {
            PEBBLE_FEED_URL -> {
                val response = pebbleWebServices.fetchUsersMeCore()
                logger.v { "response: $response" }
                null
            }
            REBBLE_FEED_URL -> {
                pebbleWebServices.fetchUsersMePebble()?.users?.firstOrNull()?.votedIds
            }
            else -> null
        }
    }

    suspend fun fetchAppStoreApp(
        id: String,
        hardwarePlatform: WatchType?,
        useCache: Boolean = true,
    ): StoreAppResponse? {
        val parameters = buildMap {
            put("platform", platform.storeString())
            if (hardwarePlatform != null) {
                put("hardware", hardwarePlatform.codename)
            }
            //            "firmware_version" to "",
            //            "filter_hardware" to "true",
        }

        if (useCache) {
            val cacheHit = cache.readApp(id, parameters, source)
            if (cacheHit != null) {
                return cacheHit
            }
        }

        val result: StoreAppResponse = try {
            httpClient.get(url = Url("${source.url}/v1/apps/id/$id")) {
                parameters.forEach {
                    parameter(it.key, it.value)
                }
            }.takeIf { it.status.isSuccess() }?.body() ?: return null
        } catch (e: IOException) {
            logger.w(e) { "Error loading app store app" }
            return null
        }
        cache.writeApp(result, parameters, source)
        return result
    }

    suspend fun fetchAppStoreHome(type: AppType, hardwarePlatform: WatchType?, useCache: Boolean): AppStoreHome? {
        val typeString = type.storeString()
        val parameters = buildMap {
            set("platform", platform.storeString())
            if (hardwarePlatform != null) {
                set("hardware", hardwarePlatform.codename)
            }
//            set("firmware_version", "")
            set("filter_hardware", "true")
        }
        if (useCache) {
            val cacheHit = cache.readHome(type, source, parameters)
            if (cacheHit != null) {
                return cacheHit
            }
        }
        val home = try {
            httpClient.get(
                url = Url("${source.url}/v1/home/$typeString")
            ) {
                parameters.forEach {
                    parameter(it.key, it.value)
                }
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.w(e) { "Error loading app store home" }
            return null
        }
        home?.let {
            cache.writeCategories(home.categories, type, source)
            appstoreCollectionDao.updateListOfCollections(type, home.collections.map {
                AppstoreCollection(
                    sourceId = source.id,
                    title = it.name,
                    slug = it.slug,
                    type = type,
                    enabled = enableByDefault(source, type, it.slug),
                )
            }, sourceId = source.id)
            cache.writeHome(home, type, source, parameters)
        }
        return home?.copy(applications = home.applications.filter { app ->
            if (app.uuid == null) return@filter false
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

    suspend fun cachedCategoriesOrDefaults(appType: AppType?): List<StoreCategory> {
        return source.cachedCategoriesOrDefaults(appType, cache)
    }

    fun fetchAppStoreCollection(
        path: String,
        appType: AppType?,
        hardwarePlatform: WatchType,
    ): PagingSource<Int, CommonApp> {
        return object : PagingSource<Int, CommonApp>() {
            override suspend fun load(params: LoadParams<Int>): LoadResult<Int, CommonApp> {
                val offset = params.key ?: 0
                 val parameters = buildMap {
                    put("platform", platform.storeString())
                    put("hardware", hardwarePlatform.codename)
                    put("offset", offset.toString())
                    put("limit", params.loadSize)
                }
                val url = buildString {
                    append("${source.url}/v1/apps/$path")
                    if (appType != null) {
                        append("/${appType.storeString()}")
                    }
                }
                logger.v { "get ${url} with parameters $parameters" }
                val categories = scope.async {
                    cachedCategoriesOrDefaults(appType)
                }
                return try {
                    val response = httpClient.get(url = Url(url)) {
                        parameters.forEach {
                            parameter(it.key, it.value)
                        }
                    }.takeIf {
                        logger.v { "${it.call.request.url}" }
                        if (!it.status.isSuccess()) {
                            logger.w { "Failed to fetch collection $path of type ${appType?.code}, status: ${it.status}" }
                            false
                        } else {
                            true
                        }
                    }?.body<StoreAppResponse>()
                    if (response != null) {
                        val apps = response.data.mapNotNull {
                            it.asCommonApp(
                                watchType = hardwarePlatform,
                                platform = platform,
                                source = source,
                                categories = categories.await(),
                            )
                        }
                        LoadResult.Page(
                            data = apps,
                            prevKey = if (offset > 0) (offset - params.loadSize).coerceAtLeast(0) else null,
                            nextKey = if (response.links.nextPage != null) offset + params.loadSize else null,
                        )
                    } else {
                        LoadResult.Error(IllegalStateException("Null response"))
                    }
                } catch (e: IOException) {
                    logger.w(e) { "Error loading app store collection" }
                    LoadResult.Error(e)
                }
            }

            override fun getRefreshKey(state: PagingState<Int, CommonApp>): Int {
                return ((state.anchorPosition ?: 0) - state.config.initialLoadSize / 2).coerceAtLeast(0)
            }
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

    suspend fun search(search: String, appType: AppType, watchType: WatchType): List<StoreSearchResult> {
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
                    tagFilters = TagFilters.of(
                        listOf(
                            TagFilters.of(appType.code),
                            TagFilters.of(platform.storeString()),
                            // Don't filter on platform - rebble index doesn't have emery for all
                            // compatible apps (plus we have the incompatible filter..)
//                            TagFilters.of(watchType.codename),
                        )
                    ),
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
}

fun enableByDefault(source: AppstoreSource, type: AppType, slug: String): Boolean {
    val isFirstSource = INITIAL_APPSTORE_SOURCES.first().url == source.url
    return when (slug) {
        "all-generated" -> false
        "all" -> true
        else -> isFirstSource
    }
}