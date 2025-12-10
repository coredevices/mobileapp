package coredevices.pebble.services

import co.touchlab.kermit.Logger
import com.algolia.client.exception.AlgoliaApiException
import coredevices.database.AppstoreSource
import coredevices.database.AppstoreSourceDao
import coredevices.pebble.Platform
import coredevices.pebble.account.BootConfig
import coredevices.pebble.account.BootConfigProvider
import coredevices.pebble.account.FirestoreLocker
import coredevices.pebble.account.PebbleAccount
import coredevices.pebble.account.UsersMeResponse
import coredevices.pebble.firmware.FirmwareUpdateCheck
import coredevices.pebble.services.PebbleHttpClient.Companion.delete
import coredevices.pebble.services.PebbleHttpClient.Companion.get
import coredevices.pebble.services.PebbleHttpClient.Companion.getWithWeatherAuth
import coredevices.pebble.services.PebbleHttpClient.Companion.put
import coredevices.pebble.weather.WeatherResponse
import coredevices.util.CoreConfigFlow
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.http.path
import io.ktor.serialization.ContentConvertException
import io.rebble.libpebblecommon.connection.FirmwareUpdateCheckResult
import io.rebble.libpebblecommon.connection.WebServices
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.services.WatchInfo
import io.rebble.libpebblecommon.util.GeolocationPositionResult
import io.rebble.libpebblecommon.web.LockerEntry
import io.rebble.libpebblecommon.web.LockerEntryCompanions
import io.rebble.libpebblecommon.web.LockerEntryCompatibility
import io.rebble.libpebblecommon.web.LockerEntryDeveloper
import io.rebble.libpebblecommon.web.LockerEntryLinks
import io.rebble.libpebblecommon.web.LockerEntryPBW
import io.rebble.libpebblecommon.web.LockerEntryPlatform
import io.rebble.libpebblecommon.web.LockerEntryPlatformImages
import io.rebble.libpebblecommon.web.LockerModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.parametersOf
import kotlin.uuid.Uuid

interface PebbleBootConfigService {
    suspend fun getBootConfig(url: String): BootConfig?
}

interface PebbleAccountProvider {
    fun get(): PebbleAccount
}

class PebbleHttpClient(
    private val pebbleAccount: PebbleAccountProvider,
    httpClient: HttpClient = HttpClient(),
) : PebbleBootConfigService {
    private val logger = Logger.withTag("PebbleHttpClient")
    private val httpClient = httpClient.config {
        install(HttpCache)
    }
    companion object {
        internal suspend fun PebbleHttpClient.put(
            url: String,
            auth: Boolean,
        ): Boolean {
            val token = pebbleAccount.get().loggedIn.value
            if (auth && token == null) {
                logger.i("not logged in")
                return false
            }
            val response = try {
                httpClient.put(url) {
                    if (auth && token != null) {
                        bearerAuth(token)
                    }
                }
            } catch (e: IOException) {
                logger.w(e) { "Error doing put: ${e.message}" }
                return false
            }
            logger.v { "post url=$url result=${response.status}" }
            return response.status.isSuccess()
        }

        internal suspend fun PebbleHttpClient.delete(
            url: String,
            auth: Boolean,
        ): Boolean {
            val token = pebbleAccount.get().loggedIn.value
            if (auth && token == null) {
                logger.i("not logged in")
                return false
            }
            val response = try {
                httpClient.delete(url) {
                    if (auth && token != null) {
                        bearerAuth(token)
                    }
                }
            } catch (e: IOException) {
                logger.w(e) { "Error doing put: ${e.message}" }
                return false
            }
            logger.v { "delete url=$url result=${response.status}" }
            return response.status.isSuccess()
        }

        internal suspend inline fun <reified T> PebbleHttpClient.getWithWeatherAuth(
            url: String,
        ): T? {
            val token = pebbleAccount.get().loggedIn.value
            if (token == null) {
                logger.i("not logged in")
                return null
            }
            return get(url = url, auth = false, parameters = mapOf(
                "access_token" to token
            ))
        }

        internal suspend inline fun <reified T> PebbleHttpClient.get(
            url: String,
            auth: Boolean,
            parameters: Map<String, String> = emptyMap(),
        ): T? {
            logger.v("get: $url auth=$auth")
            val token = pebbleAccount.get().loggedIn.value
            if (auth && token == null) {
                logger.i("not logged in")
                return null
            }
            val response = try {
                httpClient.get(url) {
                    if (auth && token != null) {
                        bearerAuth(token)
                    }
                    parameters.forEach {
                        parameter(it.key, it.value)
                    }
                }
            } catch (e: IOException) {
                logger.w(e) { "Error doing get: ${e.message}" }
                return null
            }
            if (!response.status.isSuccess()) {
                logger.i("http call failed: $response")
                return null
            }
            return try {
                response.body<T>()
            } catch (e: NoTransformationFoundException) {
                logger.e("error: ${e.message}", e)
                null
            } catch (e: ContentConvertException) {
                logger.e("error: ${e.message}", e)
                null
            }
        }
    }

    override suspend fun getBootConfig(url: String): BootConfig? = get(url, auth = false)
}

class RealPebbleWebServices(
    private val httpClient: PebbleHttpClient,
    private val firmwareUpdateCheck: FirmwareUpdateCheck,
    private val bootConfig: BootConfigProvider,
    private val memfault: Memfault,
    private val platform: Platform,
    private val appstoreSourceDao: AppstoreSourceDao,
    private val firestoreLocker: FirestoreLocker,
    private val coreConfig: CoreConfigFlow
) : WebServices, KoinComponent {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val scope = CoroutineScope(Dispatchers.Default)

    private val logger = Logger.withTag("PebbleWebServices")

    companion object {
        private suspend inline fun <reified T> RealPebbleWebServices.get(
            url: BootConfig.Config.() -> String,
            auth: Boolean,
        ): T? {
            val bootConfig = bootConfig.getBootConfig()
            if (bootConfig == null) {
                logger.i("No bootconfig!")
                return null
            }
            return httpClient.get(url(bootConfig.config), auth)
        }

        private suspend fun RealPebbleWebServices.put(
            url: BootConfig.Config.() -> String,
            auth: Boolean,
        ): Boolean {
            val bootConfig = bootConfig.getBootConfig()
            if (bootConfig == null) {
                logger.i("No bootconfig!")
                return false
            }
            return httpClient.put(url(bootConfig.config), auth)
        }

        private suspend fun RealPebbleWebServices.delete(
            url: BootConfig.Config.() -> String,
            auth: Boolean,
        ): Boolean {
            val bootConfig = bootConfig.getBootConfig()
            if (bootConfig == null) {
                logger.i("No bootconfig!")
                return false
            }
            return httpClient.delete(url(bootConfig.config), auth)
        }
    }

    private suspend fun getAllSources(enabledOnly: Boolean = true): List<AppstoreSource> {
        return if (enabledOnly) {
            appstoreSourceDao.getAllEnabledSources().first()
        } else {
            appstoreSourceDao.getAllSources().first()
        }
    }

    private val appstoreServices = mutableMapOf<String, AppstoreService>()

    private fun appstoreServiceForSource(source: AppstoreSource): AppstoreService {
        return appstoreServices.getOrPut(source.url) {
            get {
                parametersOf(source)
            }
        }
    }

    private fun appstoreServiceForUrl(sourceUrl: String): AppstoreService {
        return get {
            parametersOf(AppstoreSource(
                url = sourceUrl,
                title = ""
            ))
        }
    }

    suspend fun fetchPebbleLocker(): LockerModel? = get({ locker.getEndpoint }, auth = true)

    override suspend fun fetchLocker(): LockerModel? {
        return if (coreConfig.value.useNativeAppStore) {
            firestoreLocker.fetchLocker()
        } else {
            fetchPebbleLocker()
        }
    }

    override suspend fun removeFromLocker(id: Uuid): Boolean {
        if (coreConfig.value.useNativeAppStore) {
            firestoreLocker.removeApp(id)
            return true
        } else {
            return delete({ locker.removeEndpoint.replace("\$\$app_uuid\$\$", id.toString()) }, auth = true)
        }
    }

    override suspend fun checkForFirmwareUpdate(watch: WatchInfo): FirmwareUpdateCheckResult =
        firmwareUpdateCheck.checkForUpdates(watch)

    override suspend fun uploadMemfaultChunk(chunk: ByteArray, watchInfo: WatchInfo) {
        memfault.uploadChunk(chunk, watchInfo)
    }

    suspend fun addToLegacyLocker(uuid: String): Boolean =
        put({ locker.addEndpoint.replace("\$\$app_uuid\$\$", uuid) }, auth = true)

    suspend fun addToLocker(id: String, sourceUrl: String): Boolean = firestoreLocker.addApp(id, sourceUrl)

    suspend fun fetchUsersMe(): UsersMeResponse? = get({ links.usersMe }, auth = true)

    suspend fun fetchAppStoreHome(type: AppType, hardwarePlatform: WatchType?, enabledOnly: Boolean = true): List<Pair<AppstoreSource, AppStoreHome?>> {
        val typeString = type.storeString()
        val parameters = buildMap {
            set("platform", platform.storeString())
            if (hardwarePlatform != null) {
                set("hardware", hardwarePlatform.codename)
            }
//            set("firmware_version", "")
            set("filter_hardware", "true")
        }
        return getAllSources(enabledOnly).map {
            it to appstoreServiceForSource(it).fetchAppStoreHome(type, hardwarePlatform)
        }
    }

    suspend fun fetchAppStoreApp(id: String, hardwarePlatform: WatchType?, sourceUrl: String, useCache: Boolean = true): StoreAppResponse? {
        val appstore = appstoreServiceForUrl(sourceUrl)
        return appstore.fetchAppStoreApp(id, hardwarePlatform, useCache)
    }

    suspend fun getWeather(location: GeolocationPositionResult.Success): WeatherResponse? {
        val url = "https://weather.rebble.io/api/v1/geocode/${location.latitude}/${location.longitude}/"
        return httpClient.getWithWeatherAuth(url)
    }

    suspend fun searchUuidInSources(uuid: Uuid): List<Pair<String, AppstoreSource>> {
        return getAllSources().map { source ->
            scope.async {
                appstoreServiceForSource(source).searchUuid(uuid.toString())?.let { Pair(it, source) }
            }
        }.awaitAll().filterNotNull()
    }

    suspend fun searchAppStore(search: String, type: AppType?): List<Pair<AppstoreSource, StoreSearchResult>> {
//        val params = SearchMethodParams()
        return getAllSources().map { source ->
            scope.async {
                val appstore = appstoreServiceForSource(source)
                try {
                    appstore.search(search, type).map {
                        Pair(source, it)
                    }
                } catch (e: AlgoliaApiException) {
                    logger.w(e) { "searchSingleIndex" }
                    emptyList()
                } catch (e: IllegalStateException) {
                    logger.w(e) { "searchSingleIndex" }
                    emptyList()
                }
            }
        }.awaitAll().flatten().distinctBy { it.second.uuid }
//        logger.v { "search response: $response" }
    }
}

fun AppType.storeString() = when (this) {
    AppType.Watchapp -> "apps"
    AppType.Watchface -> "faces"
}

fun Platform.storeString() = when (this) {
    Platform.Android -> "android"
    Platform.IOS -> "ios"
}

/**
 * {
 *   "_tags": [
 *     "watchface",
 *     "aplite",
 *     "basalt",
 *     "diorite",
 *     "emery",
 *     "android",
 *     "ios"
 *   ],
 *   "asset_collections": [
 *     {
 *       "description": "Simple watchface with time and date",
 *       "hardware_platform": "aplite",
 *       "screenshots": [
 *         "https://assets2.rebble.io/exact/144x168/W0QXA4pCSS6eM7Fw7blQ"
 *       ]
 *     }
 *   ],
 *   "author": "cbackas",
 *   "capabilities": [
 *     "location"
 *   ],
 *   "category": "Faces",
 *   "category_color": "ffffff",
 *   "category_id": "528d3ef2dc7b5f580700000a",
 *   "collections": [
 *
 *   ],
 *   "companions": "00",
 *   "compatibility": {
 *     "android": {
 *       "supported": true
 *     },
 *     "aplite": {
 *       "firmware": {
 *         "major": 3
 *       },
 *       "supported": true
 *     },
 *     "basalt": {
 *       "firmware": {
 *         "major": 3
 *       },
 *       "supported": true
 *     },
 *     "chalk": {
 *       "firmware": {
 *         "major": 3
 *       },
 *       "supported": false
 *     },
 *     "diorite": {
 *       "firmware": {
 *         "major": 3
 *       },
 *       "supported": true
 *     },
 *     "emery": {
 *       "firmware": {
 *         "major": 3
 *       },
 *       "supported": true
 *     },
 *     "ios": {
 *       "min_js_version": 1,
 *       "supported": true
 *     }
 *   },
 *   "description": "Simple watchface with time and date",
 *   "developer_id": "54b8292d986a2265350000a2",
 *   "hearts": 6,
 *   "icon_image": "",
 *   "id": "5504fca40c9d58b521000065",
 *   "js_versions": [
 *     "-1",
 *     "-1",
 *     "-1"
 *   ],
 *   "list_image": "https://assets2.rebble.io/exact/144x144/W0QXA4pCSS6eM7Fw7blQ",
 *   "screenshot_hardware": "aplite",
 *   "screenshot_images": [
 *     "https://assets2.rebble.io/exact/144x168/W0QXA4pCSS6eM7Fw7blQ"
 *   ],
 *   "source": null,
 *   "title": "B",
 *   "type": "watchface",
 *   "uuid": "4039e5d4-acb5-47a8-a382-8e9c0fd66ade",
 *   "website": null
 * }
 */

@Serializable
data class StoreSearchResult(
    val author: String,
    val category: String,
    val compatibility: LockerEntryCompatibility,
    val description: String,
    val hearts: Int,
    @SerialName("icon_image")
    val iconImage: String,
    @SerialName("list_image")
    val listImage: String,
    val title: String,
    val type: String,
    val uuid: String,
    val id: String,
    @SerialName("screenshot_images")
    val screenshotImages: List<String>,
    @SerialName("asset_collections")
    val assetCollections: List<StoreAssetCollection>,
)

@Serializable
data class StoreAssetCollection(
    val description: String,
    @SerialName("hardware_platform")
    val hardwarePlatform: String,
    val screenshots: List<String>,
)

@Serializable
data class StoreAppResponse(
    val data: List<StoreApplication>,
    val limit: Int,
    val links: StoreResponseLinks,
    val offset: Int,
)

@Serializable
data class StoreResponseLinks(
    val nextPage: String?,
)

@Serializable
data class AppStoreHome(
    val applications: List<StoreApplication>,
    val categories: List<StoreCategory>,
    val collections: List<StoreCollection>,
)

@Serializable
data class StoreCategory(
    @SerialName("application_ids")
    val applicationIds: List<String>,
//    val banners: List<StoreBanner>,
    val color: String,
    val icon: Map<String, String?>,
    val id: String,
    val links: Map<String, String>,
    val name: String,
    val slug: String,
)

/**
 *       "banners": [
 *         {
 *           "application_id": "67c3afe7d2acb30009a3c7c2",
 *           "image": {
 *             "720x320": "https://assets2.rebble.io/720x320/bobby-banner-diorite-2.png"
 *           },
 *           "title": "Bobby"
 *         }
 *       ],
 */

@Serializable
data class StoreCollection(
    @SerialName("application_ids")
    val applicationIds: List<String>,
    val links: Map<String, String>,
    val name: String,
    val slug: String,
)

@Serializable
data class StoreApplication(
    val author: String,
    val capabilities: List<String>,
    val category: String,
    @SerialName("category_color")
    val categoryColor: String,
    @SerialName("category_id")
    val categoryId: String,
    val changelog: List<StoreChangelogEntry>,
    val companions: LockerEntryCompanions,
    val compatibility: LockerEntryCompatibility,
    @SerialName("created_at")
    val createdAt: String,
    val description: String,
    @SerialName("developer_id")
    val developerId: String,
//    @SerialName("header_images")
//    val headerImages: List<Map<String, String>>,
    val hearts: Int,
    @SerialName("icon_image")
    val iconImage: Map<String, String>,
    val id: String,
    @SerialName("latest_release")
    val latestRelease: StoreLatestRelease,
//    val links: StoreLinks,
    @SerialName("list_image")
    val listImage: Map<String, String>,
    @SerialName("published_date")
    val publishedDate: String?,
    @SerialName("screenshot_hardware")
    val screenshotHardware: String?,
    @SerialName("screenshot_images")
    val screenshotImages: List<Map<String, String>>,
    val source: String?,
    val title: String,
    val type: String,
    val uuid: String = Uuid.NIL.toString(),
    val visible: Boolean,
    val website: String?,
)

/**
 *       "links": {
 *         "add": "https://a",
 *         "add_flag": "https://appstore-api.rebble.io/api/v0/applications/68bc78afe4686f0009f3c34a/add_flag",
 *         "add_heart": "https://appstore-api.rebble.io/api/v0/applications/68bc78afe4686f0009f3c34a/add_heart",
 *         "remove": "https://b",
 *         "remove_flag": "https://appstore-api.rebble.io/api/v0/applications/68bc78afe4686f0009f3c34a/remove_flag",
 *         "remove_heart": "https://appstore-api.rebble.io/api/v0/applications/68bc78afe4686f0009f3c34a/remove_heart",
 *         "share": "https://apps.rebble.io/application/68bc78afe4686f0009f3c34a"
 *       },
 */

@Serializable
data class StoreLatestRelease(
    val id: String,
    @SerialName("js_md5")
    val jsMd5: String?,
    @SerialName("js_version")
    val jsVersion: Int,
    @SerialName("pbw_file")
    val pbwFile: String,
    @SerialName("published_date")
    val publishedDate: String,
    @SerialName("release_notes")
    val releaseNotes: String,
    val version: String,
)

@Serializable
data class StoreChangelogEntry(
    @SerialName("published_date")
    val publishedDate: String,
    @SerialName("release_notes")
    val releaseNotes: String,
    @SerialName("version")
    val version: String,
)

//@Serializable
//data class StoreHeaderImage(
//    @SerialName("720x320")
//    val x720: String,
//    val orig: String,
//)

fun StoreAppResponse.toLockerEntry(sourceUrl: String? = null): LockerEntry? {
    val app = data.firstOrNull() ?: return null
    return LockerEntry(
        id = app.id,
        uuid = app.uuid,
        hearts = app.hearts,
        version = app.latestRelease.version,
        title = app.title,
        type = app.type,
        developer = LockerEntryDeveloper(id = app.developerId, name = app.author, contactEmail = ""),
        isConfigurable = app.capabilities.contains("configurable"),
        isTimelineEnabled = app.capabilities.contains("timeline"),
        pbw = LockerEntryPBW(
            file = app.latestRelease.pbwFile.let {
                if (!it.startsWith("http") && sourceUrl != null) {
                    val sourcePrefix = Url(sourceUrl)
                    URLBuilder(sourcePrefix).apply {
                        path(it)
                    }.buildString()
                } else {
                    it
                }
            },
            iconResourceId = 0,
            releaseId = ""
        ),
        links = LockerEntryLinks("", "", ""),
        compatibility = app.compatibility,
        companions = app.companions,
        category = app.category,
        hardwarePlatforms = buildList {
            var flags = 0
            if (app.type == AppType.Watchface.code) {
                flags = flags or (0x1 shl 0)
            }
            app.compatibility.aplite.takeIf { it.supported }?.let {
                val flagsFinal = flags or (0x1 shl 6)
                add(
                    LockerEntryPlatform(
                        name = "aplite",
                        sdkVersion = "5.86", //TODO: grab from API if we can
                        pebbleProcessInfoFlags = flagsFinal, //TODO: ditto
                        description = app.description,
                        images = LockerEntryPlatformImages(
                            icon = app.iconImage["48x48"] ?: "",
                            list = app.listImage["144x144"] ?: "",
                            screenshot = app.screenshotImages.firstOrNull()?.values?.firstOrNull() ?: "",
                        )
                    )
                )
            }
            app.compatibility.basalt.takeIf { it.supported }?.let {
                val flagsFinal = flags or (0x2 shl 6)
                add(
                    LockerEntryPlatform(
                        name = "basalt",
                        sdkVersion = "5.86", //TODO
                        pebbleProcessInfoFlags = flagsFinal,
                        description = app.description,
                        images = LockerEntryPlatformImages(
                            icon = app.iconImage["48x48"] ?: "",
                            list = app.listImage["144x144"] ?: "",
                            screenshot = app.screenshotImages.firstOrNull()?.values?.firstOrNull() ?: "",
                        )
                    )
                )
            }
            app.compatibility.chalk.takeIf { it.supported }?.let {
                val flagsFinal = flags or (0x3 shl 6)
                add(
                    LockerEntryPlatform(
                        name = "chalk",
                        sdkVersion = "5.86",
                        pebbleProcessInfoFlags = flagsFinal,
                        description = app.description,
                        images = LockerEntryPlatformImages(
                            icon = app.iconImage["48x48"] ?: "",
                            list = app.listImage["144x144"] ?: "",
                            screenshot = app.screenshotImages.firstOrNull()?.values?.firstOrNull() ?: "",
                        )
                    )
                )
            }
            app.compatibility.diorite.takeIf { it.supported }?.let {
                val flagsFinal = flags or (0x4 shl 6)
                add(
                    LockerEntryPlatform(
                        name = "diorite",
                        sdkVersion = "5.86",
                        pebbleProcessInfoFlags = flagsFinal,
                        description = app.description,
                        images = LockerEntryPlatformImages(
                            icon = app.iconImage["48x48"] ?: "",
                            list = app.listImage["144x144"] ?: "",
                            screenshot = app.screenshotImages.firstOrNull()?.values?.firstOrNull() ?: "",
                        )
                    )
                )
            }
            app.compatibility.emery.takeIf { it.supported }?.let {
                val flagsFinal = flags or (0x5 shl 6)
                add(
                    LockerEntryPlatform(
                        name = "emery",
                        sdkVersion = "5.86",
                        pebbleProcessInfoFlags = flagsFinal,
                        description = app.description,
                        images = LockerEntryPlatformImages(
                            icon = app.iconImage["48x48"] ?: "",
                            list = app.listImage["144x144"] ?: "",
                            screenshot = app.screenshotImages.firstOrNull()?.values?.firstOrNull() ?: "",
                        )
                    )
                )
            }
            app.compatibility.flint.takeIf { it?.supported ?: false }?.let {
                val flagsFinal = flags or (0x6 shl 6)
                add(
                    LockerEntryPlatform(
                        name = "flint",
                        sdkVersion = "5.86",
                        pebbleProcessInfoFlags = flagsFinal,
                        description = app.description,
                        images = LockerEntryPlatformImages(
                            icon = app.iconImage["48x48"] ?: "",
                            list = app.listImage["144x144"] ?: "",
                            screenshot = app.screenshotImages.firstOrNull()?.values?.firstOrNull() ?: "",
                        )
                    )
                )
            }
        }
    )
}