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
import coredevices.pebble.services.PebbleHttpClient.Companion.put
import coredevices.pebble.ui.CommonAppType
import coredevices.pebble.weather.WeatherResponse
import coredevices.util.CoreConfigFlow
import coredevices.util.WeatherUnit
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
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
import io.rebble.libpebblecommon.web.LockerAddResponse
import io.rebble.libpebblecommon.web.LockerEntry
import io.rebble.libpebblecommon.web.LockerEntryCompanions
import io.rebble.libpebblecommon.web.LockerEntryCompatibility
import io.rebble.libpebblecommon.web.LockerEntryDeveloper
import io.rebble.libpebblecommon.web.LockerEntryLinks
import io.rebble.libpebblecommon.web.LockerEntryPBW
import io.rebble.libpebblecommon.web.LockerEntryPlatform
import io.rebble.libpebblecommon.web.LockerEntryPlatformImages
import io.rebble.libpebblecommon.web.LockerModel
import io.rebble.libpebblecommon.web.LockerModelWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.io.IOException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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

fun PebbleAccountProvider.isLoggedIn(): Boolean = get().loggedIn.value != null

private val logger = Logger.withTag("PebbleHttpClient")

class PebbleHttpClient(
    private val pebbleAccount: PebbleAccountProvider,
    httpClient: HttpClient = HttpClient(),
) : PebbleBootConfigService {
    private val httpClient = httpClient.config {
        install(HttpCache)
    }
    companion object {
        internal suspend fun PebbleHttpClient.put(
            url: String,
            auth: Boolean,
        ): HttpResponse? {
            val token = pebbleAccount.get().loggedIn.value
            if (auth && token == null) {
                logger.i("not logged in")
                return null
            }
            val response = try {
                httpClient.put(url) {
                    if (auth && token != null) {
                        bearerAuth(token)
                    }
                }
            } catch (e: IOException) {
                logger.w(e) { "Error doing put: ${e.message}" }
                return null
            }
            logger.v { "post url=$url result=${response.status}" }
            return response
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

interface PebbleWebServices {
    suspend fun fetchUsersMe(): UsersMeResponse?
    suspend fun fetchPebbleLocker(): LockerModel?
    suspend fun addToLegacyLocker(uuid: String): Boolean
    suspend fun fetchAppStoreHome(type: AppType, hardwarePlatform: WatchType?, enabledOnly: Boolean, useCache: Boolean): List<AppStoreHomeResult>
    suspend fun searchAppStore(search: String, appType: AppType, watchType: WatchType): List<Pair<AppstoreSource, StoreSearchResult>>
    suspend fun addToLegacyLockerWithResponse(uuid: String): LockerAddResponse?
    suspend fun addToLocker(entry: CommonAppType.Store, timelineToken: String?): Boolean
    suspend fun removeFromLegacyLocker(id: Uuid): Boolean
    suspend fun getWeather(latitude: Double, longitude: Double, units: WeatherUnit, language: String): WeatherResponse?
}

class RealPebbleWebServices(
    private val httpClient: PebbleHttpClient,
    private val firmwareUpdateCheck: FirmwareUpdateCheck,
    private val bootConfig: BootConfigProvider,
    private val memfault: Memfault,
    private val appstoreSourceDao: AppstoreSourceDao,
    private val firestoreLocker: FirestoreLocker,
    private val coreConfig: CoreConfigFlow
) : WebServices, PebbleWebServices, KoinComponent {
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
        ): HttpResponse? {
            val bootConfig = bootConfig.getBootConfig()
            if (bootConfig == null) {
                logger.i("No bootconfig!")
                return null
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
            appstoreSourceDao.getAllEnabledSourcesFlow().first()
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

    override suspend fun fetchPebbleLocker(): LockerModel? = get({ locker.getEndpoint }, auth = true)

    override suspend fun fetchLocker(): LockerModelWrapper? {
        return if (coreConfig.value.useNativeAppStore) {
            firestoreLocker.fetchLocker()
        } else {
            fetchPebbleLocker()?.let { LockerModelWrapper(it, emptySet()) }
        }
    }

    override suspend fun removeFromLocker(id: Uuid): Boolean {
        if (coreConfig.value.useNativeAppStore) {
            firestoreLocker.removeApp(id)
            return true
        } else {
            return removeFromLegacyLocker(id)
        }
    }

    override suspend fun removeFromLegacyLocker(id: Uuid): Boolean {
        return delete({ locker.removeEndpoint.replace("\$\$app_uuid\$\$", id.toString()) }, auth = true)
    }

    override suspend fun checkForFirmwareUpdate(watch: WatchInfo): FirmwareUpdateCheckResult =
        firmwareUpdateCheck.checkForUpdates(watch)

    override suspend fun uploadMemfaultChunk(chunk: ByteArray, watchInfo: WatchInfo) {
        memfault.uploadChunk(chunk, watchInfo)
    }

    override suspend fun addToLegacyLocker(uuid: String): Boolean =
        put({ locker.addEndpoint.replace("\$\$app_uuid\$\$", uuid) }, auth = true)?.status?.isSuccess() == true

    override suspend fun addToLegacyLockerWithResponse(uuid: String): LockerAddResponse? {
        return put({ locker.addEndpoint.replace("\$\$app_uuid\$\$", uuid) }, auth = true)?.body()
    }

    override suspend fun addToLocker(entry: CommonAppType.Store, timelineToken: String?): Boolean = firestoreLocker.addApp(entry, timelineToken)

    override suspend fun fetchUsersMe(): UsersMeResponse? = get({ links.usersMe }, auth = true)

    override suspend fun fetchAppStoreHome(type: AppType, hardwarePlatform: WatchType?, enabledOnly: Boolean, useCache: Boolean): List<AppStoreHomeResult> {
        return getAllSources(enabledOnly).mapNotNull {
            val home = appstoreServiceForSource(it).fetchAppStoreHome(type, hardwarePlatform, useCache)
            if (home == null) return@mapNotNull null
            AppStoreHomeResult(it, home)
        }
    }

    override suspend fun getWeather(latitude: Double, longitude: Double, units: WeatherUnit, language: String): WeatherResponse? {
        val url = "https://weather-api.repebble.com/api/v1/geocode/$latitude/$longitude?language=$language&units=${units.code}"
        return httpClient.get(url, auth = false)
    }

    suspend fun searchUuidInSources(uuid: Uuid): List<Pair<String, AppstoreSource>> {
        return getAllSources().map { source ->
            scope.async {
                appstoreServiceForSource(source).searchUuid(uuid.toString())?.let { Pair(it, source) }
            }
        }.awaitAll().filterNotNull()
    }

    override suspend fun searchAppStore(search: String, appType: AppType, watchType: WatchType): List<Pair<AppstoreSource, StoreSearchResult>> {
//        val params = SearchMethodParams()
        return getAllSources().map { source ->
            scope.async {
                val appstore = appstoreServiceForSource(source)
                try {
                    appstore.search(search, appType, watchType).map {
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

data class AppStoreHomeResult(
    val source: AppstoreSource,
    val result: AppStoreHome,
)

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
data class BulkStoreResponse(
    val data: List<StoreApplication>
)

object HeaderImageSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("HeaderImage", PrimitiveKind.STRING).nullable

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> element.contentOrNull
            is JsonArray -> {
                val firstMap = element.firstOrNull() as? JsonObject
                val firstValue = firstMap?.values?.firstOrNull() as? JsonPrimitive
                firstValue?.contentOrNull
            }
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeString(value)
        }
    }
}

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
    @SerialName("hardware_platforms")
    val hardwarePlatforms: List<StoreHardwarePlatform>? = null,
    @SerialName("header_images")
    @Serializable(with = HeaderImageSerializer::class)
    val headerImage: String?,
    val hearts: Int,
    @SerialName("icon_image")
    val iconImage: Map<String, String>,
    @SerialName("icon_resource_id")
    val iconResourceId: Int? = null,
    val id: String,
    @SerialName("latest_release")
    val latestRelease: StoreLatestRelease? = null,
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
    val uuid: String? = Uuid.NIL.toString(),
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
    val releaseNotes: String?,
    val version: String?,
)

@Serializable
data class StoreHardwarePlatform(
    val name: String,
    @SerialName("sdk_version")
    val sdkVersion: String?,
    @SerialName("pebble_process_info_flags")
    val pebbleProcessInfoFlags: Int?,
    val description: String,
    val images: Map<String, String>,
)

@Serializable
data class StoreChangelogEntry(
    @SerialName("published_date")
    val publishedDate: String,
    @SerialName("release_notes")
    val releaseNotes: String?,
    @SerialName("version")
    val version: String?,
)

//@Serializable
//data class StoreHeaderImage(
//    @SerialName("720x320")
//    val x720: String,
//    val orig: String,
//)

private const val FALLBACK_SDK_VERSION = "5.86"
private const val FALLBACK_ICON_RESOURCE_ID = 0

fun StoreApplication.asLockerEntryPlatform(
    platformName: String,
    fallbackFlags: Int,
): LockerEntryPlatform? {
    val lockerEntryPlatform = hardwarePlatforms?.firstOrNull { it.name == platformName }
    val sdkVersion = if (hardwarePlatforms != null) {
        lockerEntryPlatform?.sdkVersion
    } else {
        FALLBACK_SDK_VERSION
    }
    val pebbleProcessInfoFlags = if (hardwarePlatforms != null) {
        lockerEntryPlatform?.pebbleProcessInfoFlags
    } else {
        fallbackFlags
    }
    if (sdkVersion == null || pebbleProcessInfoFlags == null) {
        return null
    }
    return LockerEntryPlatform(
        name = platformName,
        sdkVersion = sdkVersion,
        pebbleProcessInfoFlags = pebbleProcessInfoFlags,
        description = description,
        images = LockerEntryPlatformImages(
            icon = iconImage["48x48"] ?: "",
            list = listImage["144x144"] ?: "",
            screenshot = screenshotImages.firstOrNull()?.values?.firstOrNull() ?: "",
        )
    )
}

fun StoreApplication.toLockerEntry(sourceUrl: String, timelineToken: String?): LockerEntry? {
    val app = this
    if (app.latestRelease == null) {
        logger.w { "no latest release" }
        return null
    }
    if (app.uuid == null) {
        logger.w { "no uuid" }
        return null
    }
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
                if (!it.startsWith("http")) {
                    val sourcePrefix = Url(sourceUrl)
                    URLBuilder(sourcePrefix).apply {
                        path(it)
                    }.buildString()
                } else {
                    it
                }
            },
            iconResourceId = app.iconResourceId ?: FALLBACK_ICON_RESOURCE_ID,
            releaseId = ""
        ),
        links = LockerEntryLinks("", "", ""),
        capabilities = app.capabilities,
        compatibility = app.compatibility,
        companions = app.companions,
        category = app.category,
        userToken = timelineToken,
        hardwarePlatforms = buildList {
            var fallbackFlags = 0
            if (app.type == AppType.Watchface.code) {
                fallbackFlags = fallbackFlags or (0x1 shl 0)
            }
            app.compatibility.aplite.takeIf { it.supported }?.let {
                val fallbackFlagsFinal = fallbackFlags or (0x1 shl 6)
                app.asLockerEntryPlatform("aplite", fallbackFlagsFinal)?.let { add(it) }
            }
            app.compatibility.basalt.takeIf { it.supported }?.let {
                val fallbackFlagsFinal = fallbackFlags or (0x2 shl 6)
                app.asLockerEntryPlatform("basalt", fallbackFlagsFinal)?.let { add(it) }
            }
            app.compatibility.chalk.takeIf { it.supported }?.let {
                val fallbackFlagsFinal = fallbackFlags or (0x3 shl 6)
                app.asLockerEntryPlatform("chalk", fallbackFlagsFinal)?.let { add(it) }
            }
            app.compatibility.diorite.takeIf { it.supported }?.let {
                val fallbackFlagsFinal = fallbackFlags or (0x4 shl 6)
                app.asLockerEntryPlatform("diorite", fallbackFlagsFinal)?.let { add(it) }
            }
            app.compatibility.emery.takeIf { it.supported }?.let {
                val fallbackFlagsFinal = fallbackFlags or (0x5 shl 6)
                app.asLockerEntryPlatform("emery", fallbackFlagsFinal)?.let { add(it) }
            }
            app.compatibility.flint.takeIf { it?.supported ?: false }?.let {
                val fallbackFlagsFinal = fallbackFlags or (0x6 shl 6)
                app.asLockerEntryPlatform("flint", fallbackFlagsFinal)?.let { add(it) }
            }
            app.compatibility.gabbro.takeIf { it?.supported ?: false }?.let {
                val fallbackFlagsFinal = fallbackFlags or (0x6 shl 7)
                app.asLockerEntryPlatform("gabbro", fallbackFlagsFinal)?.let { add(it) }
            }
        },
        source = sourceUrl,
    )
}