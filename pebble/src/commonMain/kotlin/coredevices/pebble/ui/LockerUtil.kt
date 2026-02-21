package coredevices.pebble.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesomeMotion
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BrowseGallery
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import coredevices.database.AppstoreSource
import coredevices.database.AppstoreSourceDao
import coredevices.database.HeartsDao
import coredevices.pebble.Platform
import coredevices.pebble.account.FirestoreLocker
import coredevices.pebble.account.FirestoreLockerEntry
import coredevices.pebble.rememberLibPebble
import coredevices.pebble.services.AppstoreCache
import coredevices.pebble.services.PebbleAccountProvider
import coredevices.pebble.services.PebbleWebServices
import coredevices.pebble.services.StoreApplication
import coredevices.pebble.services.StoreCategory
import coredevices.pebble.services.StoreSearchResult
import coredevices.pebble.services.isLoggedIn
import coredevices.pebble.services.isRebbleFeed
import coredevices.pebble.services.toLockerEntry
import coredevices.ui.PebbleElevatedButton
import coredevices.util.CoreConfig
import coredevices.util.CoreConfigFlow
import io.rebble.libpebblecommon.SystemAppIDs.KICKSTART_APP_UUID
import io.rebble.libpebblecommon.connection.KnownPebbleDevice
import io.rebble.libpebblecommon.connection.LibPebble
import io.rebble.libpebblecommon.database.entity.CompanionApp
import io.rebble.libpebblecommon.locker.AppCapability
import io.rebble.libpebblecommon.locker.AppPlatform
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.locker.LockerWrapper
import io.rebble.libpebblecommon.locker.SystemApps
import io.rebble.libpebblecommon.locker.findCompatiblePlatform
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.web.LockerEntryCompanionApp
import io.rebble.libpebblecommon.web.LockerEntryCompatibility
import io.rebble.libpebblecommon.web.LockerEntryCompatibilityWatchPlatformDetails
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import theme.coreOrange
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

const val LOCKER_UI_LOAD_LIMIT = 100
private val logger = Logger.withTag("LockerUtil")

@Composable
private fun firestoreLockerContents(coreConfig: CoreConfig): List<FirestoreLockerEntry>? {
    val firestoreLocker: FirestoreLocker = koinInject()
    val firestoreLockerContents by produceState<List<FirestoreLockerEntry>?>(null, coreConfig.useNativeAppStoreV2) {
        if (coreConfig.useNativeAppStoreV2) {
            value = firestoreLocker.readLocker()
        }
    }
    return firestoreLockerContents
}

@Composable
private fun appstoreSources(): List<AppstoreSource>? {
    val appstoreSourceDao: AppstoreSourceDao = koinInject()
    val appstoreSources by appstoreSourceDao.getAllSources().collectAsState(null)
    return appstoreSources
}

private fun LockerWrapper.findStoreSource(
    firestoreLockerContents: List<FirestoreLockerEntry>?,
    appstoreSources: List<AppstoreSource>?,
    coreConfig: CoreConfig,
): AppstoreSource? {
    if (!coreConfig.useNativeAppStoreV2) {
        return null
    }
    val firestoreEntry = firestoreLockerContents?.find { entry ->
        entry.uuid == properties.id
    } ?: return null
    return appstoreSources?.find { source ->
        source.url == firestoreEntry.appstoreSource
    }
}

@Composable
fun appstoreCategories(appType: AppType?, sources: List<AppstoreSource>?): Map<AppstoreSource, List<StoreCategory>>? {
    val cache: AppstoreCache = koinInject()
    if (sources == null || appType == null) {
        return null
    }
    val categories by produceState<Map<AppstoreSource, List<StoreCategory>>?>(null) {
        value = sources.associateWith { it.cachedCategoriesOrDefaults(appType, cache) }
    }
    return categories
}

@Composable
fun currentHearts(): Map<Int, Set<String>>? {
    val heartsDao: HeartsDao = koinInject()
    val hearts by heartsDao.getAllHeartsFlow().map { list ->
        buildMap {
            for (h in list) getOrPut(h.sourceId) { mutableSetOf() }.add(h.appId)
        }
    }.collectAsState(null)
    return hearts
}

fun Map<Int, Set<String>>?.hasHeart(sourceId: Int?, appId: String?): Boolean {
    if (sourceId == null || appId == null) {
        return false
    }
    return this?.get(sourceId)?.contains(appId) ?: false
}

@Composable
fun loadLockerEntries(
    currentHearts: Map<Int, Set<String>>?,
    type: AppType,
    searchQuery: String,
    watchType: WatchType,
    showIncompatible: Boolean,
    showScaled: Boolean,
    hearted: Boolean,
): List<CommonApp>? {
    val libPebble = rememberLibPebble()
    val lockerQuery = remember(
        type,
        searchQuery,
    ) {
        libPebble.getLocker(
            type = type,
            searchQuery = searchQuery,
            limit = LOCKER_UI_LOAD_LIMIT,
        )
    }
    val entries by lockerQuery.collectAsState(null)
    val coreConfigFlow: CoreConfigFlow = koinInject()
    val coreConfig by coreConfigFlow.flow.collectAsState()
    val appstoreSources = appstoreSources()
    val firestoreLockerContents = firestoreLockerContents(coreConfig)
    val categories = appstoreCategories(type, appstoreSources)
    if (entries == null || appstoreSources == null || categories == null || currentHearts == null) {
        return null
    }
    return remember(entries, watchType, appstoreSources, firestoreLockerContents, coreConfig, showIncompatible, showScaled, hearted) {
        entries?.mapNotNull {
            val appstoreSource = it.findStoreSource(firestoreLockerContents, appstoreSources, coreConfig)
            val app = it.asCommonApp(watchType, appstoreSource, categories[appstoreSource])
            if (!showIncompatible && !app.isCompatible) {
                return@mapNotNull null
            }
            if (!showScaled && !app.isNativelyCompatible) {
                return@mapNotNull null
            }
            if (hearted && !currentHearts.hasHeart(sourceId = appstoreSource?.id, appId = app.storeId)) {
                return@mapNotNull null
            }
            app
        }
    }
}

@Composable
fun CommonApp.isHearted(): Boolean? {
    val heartsDao: HeartsDao = koinInject()
    if (appstoreSource == null || storeId == null) {
        return null
    }
    val hearted by heartsDao.isHeartedFlow(sourceId = appstoreSource.id, appId = storeId).collectAsState(null)
    return hearted
}

@Composable
fun loadActiveWatchface(watchType: WatchType): CommonApp? {
    val libPebble = rememberLibPebble()
    val fallback = loadLockerEntry(KICKSTART_APP_UUID, watchType)
    val lockerEntry by libPebble.activeWatchface.collectAsState()
    return lockerEntry?.load(watchType) ?: fallback
}

@Composable
fun loadLockerEntry(uuid: Uuid?, watchType: WatchType): CommonApp? {
    if (uuid == null) {
        return null
    }
    val libPebble = rememberLibPebble()
    val lockerEntry by libPebble.getLockerApp(uuid).collectAsState(null)
    return lockerEntry?.load(watchType)
}

@Composable
fun allCollectionUuids(): List<Uuid> {
    val libPebble = rememberLibPebble()
    val allCollectionUuids by libPebble.getAllLockerUuids().collectAsState(emptyList())
    return allCollectionUuids
}

@Composable
fun CommonApp.inMyCollection(): Boolean {
    val collectionUuids  = allCollectionUuids()
    return remember(this, collectionUuids) {
        when (commonAppType) {
            is CommonAppType.Locker -> true
            is CommonAppType.System -> true
            is CommonAppType.Store -> {
                uuid in collectionUuids
            }
        }
    }
}

@Composable
private fun LockerWrapper.load(watchType: WatchType): CommonApp? {
    val coreConfigFlow: CoreConfigFlow = koinInject()
    val coreConfig by coreConfigFlow.flow.collectAsState()
    val appstoreSources = appstoreSources()
    val firestoreLockerContents = firestoreLockerContents(coreConfig)
    val categories = appstoreCategories(properties.type, appstoreSources)
    if (appstoreSources == null || categories == null) {
        return null
    }
    return remember(this, watchType, appstoreSources, firestoreLockerContents, coreConfig) {
        val appstoreSource = findStoreSource(firestoreLockerContents, appstoreSources, coreConfig)
        logger.v { "appstoreSource = $appstoreSource" }
        asCommonApp(watchType, appstoreSource, categories[appstoreSource])
    }
}

@Composable
fun CommonApp.SettingsButton(
    navBarNav: NavBarNav,
    topBarParams: TopBarParams,
    connected: Boolean,
) {
    if (hasSettings()) {
        val libPebble = rememberLibPebble()
        val scope = rememberCoroutineScope()
        val settingsEnabled =
            remember(
                this,
                connected
            ) { isCompatible && isSynced() && (connected || commonAppType is CommonAppType.System) }

        PebbleElevatedButton(
            text = "Settings",
            onClick = {
                scope.launch {
                    showSettings(navBarNav, libPebble, topBarParams)
                }
            },
            enabled = settingsEnabled,
            icon = Icons.Default.Settings,
            contentDescription = "Settings",
            primaryColor = false,
            modifier = Modifier.padding(5.dp),
        )
    }
}

@Composable
fun CommonApp.CompatibilityWarning(topBarParams: TopBarParams) {
    if (!isCompatible) {
        IconButton(
            modifier = Modifier.size(16.dp).padding(top = 1.dp, end = 6.dp, bottom = 5.dp),
            onClick = {
                topBarParams.showSnackbar("Not compatible with this watch")
            },
        ) {
            Icon(
                Icons.Filled.Block,
                contentDescription = "Not compatible with this watch",
                modifier = Modifier.fillMaxSize(),
                tint = coreOrange,
            )
        }
    } else if (!isNativelyCompatible) {
        IconButton(
            modifier = Modifier.size(16.dp).padding(top = 1.dp, end = 6.dp, bottom = 5.dp),
            onClick = {
                topBarParams.showSnackbar("Not natively compatible, but can be scaled")
            },
        ) {
            Icon(
                Icons.Filled.AspectRatio,
                contentDescription = "Not natively compatible, but can be scaled",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun lastConnectedWatch(): KnownPebbleDevice? {
    val libPebble = rememberLibPebble()
    val watchesFiltered = remember {
        libPebble.watches
    }
    val watches by watchesFiltered.collectAsState()
    val lastConnectedWatch = remember(watches) {
        watches.sortedWith(PebbleDeviceComparator).filterIsInstance<KnownPebbleDevice>()
                .firstOrNull()
    }
    return lastConnectedWatch
}

private suspend fun AppstoreSource.cachedCategoriesOrDefaultsForType(appType: AppType, cache: AppstoreCache): List<StoreCategory> {
    return cache.readCategories(appType, this) ?: when (appType) {
        AppType.Watchface -> DEFAULT_CATEGORIES_FACES
        AppType.Watchapp -> DEFAULT_CATEGORIES_APPS
    }
}

suspend fun AppstoreSource.cachedCategoriesOrDefaults(appType: AppType?, cache: AppstoreCache): List<StoreCategory> {
    return when (appType) {
        null -> cachedCategoriesOrDefaultsForType(AppType.Watchapp, cache) + cachedCategoriesOrDefaultsForType(AppType.Watchface, cache)
        else -> cachedCategoriesOrDefaultsForType(appType, cache)
    }
}

data class CommonApp(
    val title: String,
    val developerName: String,
    val uuid: Uuid,
    val androidCompanion: CompanionApp?,
    val commonAppType: CommonAppType,
    val type: AppType,
    val category: String?,
    val version: String?,
    val listImageUrl: String?,
    val screenshotImageUrl: String?,
    val isCompatible: Boolean,
    val isNativelyCompatible: Boolean,
    val hearts: Int?,
    val description: String?,
    val developerId: String?,
    val categorySlug: String?,
    val storeId: String?,
    val sourceLink: String?,
    val appstoreSource: AppstoreSource?,
    val capabilities: List<AppCapability>,
)

interface CommonAppTypeLocal {
    val order: Int
}

sealed class CommonAppType {
    data class Locker(
        val sideloaded: Boolean,
        val configurable: Boolean,
        val sync: Boolean,
        override val order: Int,
    ) : CommonAppType(), CommonAppTypeLocal

    data class Store(
        val storeApp: StoreApplication?,
        val storeSource: AppstoreSource,
        val headerImageUrl: String?,
        val allScreenshotUrls: List<String>,
        val addHeartUrl: String?,
        val removeHeartUrl: String?,
    ) : CommonAppType()

    data class System(
        val app: SystemApps,
        override val order: Int,
    ) : CommonAppType(), CommonAppTypeLocal
}

fun LockerWrapper.asCommonApp(
    watchType: WatchType?,
    appstoreSource: AppstoreSource?,
    categories: List<StoreCategory>?,
): CommonApp {
    val compatiblePlatform = findCompatiblePlatform(watchType)
    val anyPlatform = properties.platforms.firstOrNull()
    return CommonApp(
        title = properties.title,
        developerName = properties.developerName,
        uuid = properties.id,
        androidCompanion = properties.androidCompanion,
        commonAppType = when (this) {
            is LockerWrapper.NormalApp -> CommonAppType.Locker(
                sideloaded = sideloaded,
                configurable = configurable,
                sync = sync,
                order = properties.order,
            )

            is LockerWrapper.SystemApp -> CommonAppType.System(
                app = systemApp,
                order = properties.order,
            )
        },
        type = properties.type,
        category = properties.category,
        version = properties.version,
        listImageUrl = compatiblePlatform?.listImageUrl ?: anyPlatform?.listImageUrl,
        screenshotImageUrl = compatiblePlatform?.screenshotImageUrl ?: anyPlatform?.screenshotImageUrl,
        isCompatible = compatiblePlatform.isCompatible(),
        hearts = when (this) {
            is LockerWrapper.NormalApp -> properties.hearts
            is LockerWrapper.SystemApp -> null
        },
        description = compatiblePlatform?.description ?: anyPlatform?.description,
        isNativelyCompatible = when (this) {
            is LockerWrapper.NormalApp -> {
                val nativelyCompatible = when {
                    watchType != null && watchType.performsScaling() -> properties.platforms.any { it.watchType == watchType }
                    else -> true
                }
                nativelyCompatible
            }

            is LockerWrapper.SystemApp -> true
        },
        developerId = properties.developerId,
        categorySlug = categories?.firstOrNull { it.name == properties.category }?.slug,
        storeId = properties.storeId,
        sourceLink = properties.sourceLink,
        appstoreSource = appstoreSource,
        capabilities = properties.capabilities,
    )
}

fun WatchType.performsScaling(): Boolean = when (this) {
    WatchType.EMERY, WatchType.GABBRO -> true
    else -> false
}

fun StoreApplication.asCommonApp(
    watchType: WatchType,
    platform: Platform,
    source: AppstoreSource,
    categories: List<StoreCategory>,
): CommonApp? {
    val appType = AppType.fromString(type)
    if (appType == null) {
        logger.w { "StoreApplication.asCommonApp() unknown type: $type" }
        return null
    }
    if (latestRelease == null) {
        logger.w { "StoreApplication.asCommonApp() missing latestRelease" }
        return null
    }
    return CommonApp(
        title = title,
        developerName = author,
        uuid = Uuid.parse(uuid ?: return null),
        androidCompanion = companions.android?.asCompanionApp(),
        commonAppType = CommonAppType.Store(
            storeSource = source,
            storeApp = this,
            headerImageUrl = headerImage,
            allScreenshotUrls = screenshotImages.mapNotNull { it.values.firstOrNull() },
            addHeartUrl = links.addHeart,
            removeHeartUrl = links.removeHeart,
        ),
        type = appType,
        category = category,
        version = latestRelease.version,
        listImageUrl = listImage.values.firstOrNull(),
        screenshotImageUrl = screenshotImages.firstOrNull()?.values?.firstOrNull(),
        isCompatible = compatibility.isCompatible(watchType, platform),
        hearts = hearts,
        description = description,
        isNativelyCompatible = when {
            watchType.performsScaling() -> {
                when {
                    // If store doesn't report binary info, mark as compatible
                    hardwarePlatforms == null -> true
                    // If store has binary info, only natively compatible if there is a matching binary
                    else ->hardwarePlatforms.any { it.name == watchType.codename && it.pebbleProcessInfoFlags != null }
                }
            }
            else -> true
        },
        storeId = id,
        developerId = developerId,
        sourceLink = this.source,
        categorySlug = categories.firstOrNull { it.id == categoryId }?.slug,
        appstoreSource = source,
        capabilities = AppCapability.fromString(capabilities),
    )
}


fun StoreSearchResult.asCommonApp(
    watchType: WatchType,
    platform: Platform,
    source: AppstoreSource,
): CommonApp? {
    val appType = AppType.fromString(type)
    if (appType == null) {
        logger.w { "StoreApplication.asCommonApp() unknown type: $type" }
        return null
    }
    val screenshotWatchType = watchType.getBestVariant(assetCollections.map { it.hardwarePlatform })
    val screenshotPlatform = screenshotWatchType?.let { assetCollections.find { it.hardwarePlatform == screenshotWatchType.codename } }
    return CommonApp(
        title = title,
        developerName = author,
        uuid = Uuid.parse(uuid),
        androidCompanion = null,
        commonAppType = CommonAppType.Store(storeSource = source, storeApp = null, headerImageUrl = null, allScreenshotUrls = emptyList(), addHeartUrl = null, removeHeartUrl = null),
        type = appType,
        category = category,
        version = null,
        listImageUrl = listImage,
        screenshotImageUrl = screenshotPlatform?.screenshots?.firstOrNull() ?: screenshotImages.firstOrNull(),
        isCompatible = compatibility.isCompatible(watchType, platform),
        hearts = hearts,
        description = description,
        isNativelyCompatible = when {
            watchType.performsScaling() -> {
                val platformCompatibility = compatibility.findPlatform(watchType)
                // Mark as compatible if API doesn't set the field
                platformCompatibility?.hasBinary == null || platformCompatibility.hasBinary == true
            }
            else -> true
        },
        storeId = id,
        developerId = null,
        sourceLink = null,
        categorySlug = null,
        appstoreSource = source,
        capabilities = emptyList(),
    )
}

fun AppType.icon(): ImageVector = when (this) {
    AppType.Watchface -> Icons.Filled.BrowseGallery
    AppType.Watchapp -> Icons.Filled.AutoAwesomeMotion
}

fun CommonAppType.canStartApp(): Boolean = when (this) {
    is CommonAppType.Locker -> true
    is CommonAppType.Store -> false
    is CommonAppType.System -> true
}

class NativeLockerAddUtil(
    private val libPebble: LibPebble,
    private val pebbleAccountProvider: PebbleAccountProvider,
    private val webServices: PebbleWebServices,
    private val coreConfig: CoreConfigFlow,
) {
    suspend fun addAppToLocker(
        app: CommonAppType.Store,
        source: AppstoreSource,
    ): Boolean {
        val storeApp = app.storeApp
        if (storeApp == null || storeApp.uuid == null) {
            logger.w { "storeApp is null or has no uuid" }
            return false
        }
        val useLegacyLockerApiToAdd = pebbleAccountProvider.isLoggedIn() && source.isRebbleFeed()
        val lockerEntry = if (useLegacyLockerApiToAdd) {
            webServices.addToLegacyLockerWithResponse(storeApp.uuid)?.application
        } else {
            storeApp.toLockerEntry(source.url, timelineToken = null)
        }
        if (lockerEntry == null) {
            logger.w { "failed to add to locker" }
            return false
        }
        libPebble.addAppToLocker(lockerEntry)
        // Don't delay return for this
        GlobalScope.launch {
            webServices.addToLocker(
                app,
                timelineToken = lockerEntry.userToken,
            )
        }
        return true
    }

    suspend fun removeFromLocker(
        source: AppstoreSource?,
        uuid: Uuid,
    ) {
        if (!coreConfig.value.useNativeAppStoreV2) {
            return
        }
        if (source == null) {
            return
        }
        val useLockerApiToRemove = pebbleAccountProvider.isLoggedIn() && source.isRebbleFeed()
        if (useLockerApiToRemove) {
            webServices.removeFromLegacyLocker(uuid)
        }
    }
}

fun CommonApp.showOnMainLockerScreen(): Boolean = when (commonAppType) {
    is CommonAppType.Locker, is CommonAppType.Store -> true
    // Don't show system apps here (they'd always take up all the horizontal space). Show system
    // watchfaces.
    is CommonAppType.System -> type == AppType.Watchface
}

fun CommonApp.isSynced(): Boolean = when (commonAppType) {
    is CommonAppType.Locker -> commonAppType.sync
    is CommonAppType.Store -> false
    is CommonAppType.System -> true
}

fun LockerWrapper.isSynced(): Boolean = when (this) {
    is LockerWrapper.NormalApp -> sync
    is LockerWrapper.SystemApp -> true
}

fun AppPlatform?.isCompatible(): Boolean = this != null

fun LockerEntryCompanionApp.asCompanionApp(): CompanionApp = CompanionApp(
    id = id,
    icon = icon,
    name = name,
    url = url,
    required = required,
    pebblekitVersion = pebblekitVersion,
)

fun AppType.myCollectionName(): String = when (this) {
    AppType.Watchface -> "My Watchfaces"
    AppType.Watchapp -> "My Apps"
}

fun AppType.shortName(): String = when (this) {
    AppType.Watchface -> "Faces"
    AppType.Watchapp -> "Apps"
}

private var hasShownScrollHint = false

@Composable
fun AppsFilterRow(
    watchType: WatchType,
    selectedType: MutableState<AppType>?,
    showIncompatible: MutableState<Boolean>?,
    showScaled: MutableState<Boolean>?,
    hearted: MutableState<Boolean>?,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(hasShownScrollHint) {
        if (!hasShownScrollHint && scrollState.maxValue > 0) {
            hasShownScrollHint = true
            // Wait a small bit for the layout to settle and user to focus
            delay(500.milliseconds)

            // Scroll right 60dp (approximate)
            scrollState.animateScrollTo(
                value = 150,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
            )
            // Wait a moment at the "peek" position
            delay(200)
            // Scroll back to start
            scrollState.animateScrollTo(
                value = 0,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
            )
        }
    }
    Box(
        modifier = Modifier.padding(top = 0.dp, bottom = 4.dp, start = 12.dp, end = 12.dp)
    ) {
        Row(
            modifier = Modifier.horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val height = 37.dp
            if (selectedType != null) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.height(height).padding(0.dp),
                ) {
                    AppType.entries.forEachIndexed { index, appType ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = AppType.entries.size
                            ),
                            selected = selectedType.value == appType,
                            onClick = { selectedType.value = appType },
                            icon = { },
                            label = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(0.dp)
                                ) {
                                    Icon(
                                        appType.icon(),
                                        contentDescription = appType.shortName(),
                                        modifier = Modifier.size(22.dp).padding(0.dp)
                                    )
                                    Spacer(modifier = Modifier.width(7.dp))
                                    Text(
                                        appType.shortName(),
                                        lineHeight = 13.sp,
                                        modifier = Modifier.padding(0.dp),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                            modifier = Modifier.padding(0.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (watchType.performsScaling() && showScaled != null) {
                FilterChip(
                    selected = showScaled.value,
                    onClick = { showScaled.value = !showScaled.value },
                    label = { Text("Show Scaled") },
                    modifier = Modifier.padding(horizontal = 4.dp),
                    leadingIcon = if (showScaled.value) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Done,
                                contentDescription = "Show Scaled",
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else {
                        null
                    },
                )
            }
            if (hearted != null) {
                FilterChip(
                    selected = hearted.value,
                    onClick = { hearted.value = !hearted.value },
                    label = { Text("Hearted") },
                    modifier = Modifier.padding(horizontal = 4.dp),
                    leadingIcon = if (hearted.value) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = "Hearted",
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else {
                        null
                    },
                )
            }
            if (showIncompatible != null) {
                FilterChip(
                    selected = showIncompatible.value,
                    onClick = { showIncompatible.value = !showIncompatible.value },
                    label = { Text("Show Incompatible") },
                    modifier = Modifier.padding(horizontal = 4.dp),
                    leadingIcon = if (showIncompatible.value) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Done,
                                contentDescription = "Show Incompatible",
                                modifier = Modifier.size(FilterChipDefaults.IconSize)
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

fun LockerEntryCompatibility.isCompatible(watchType: WatchType, platform: Platform): Boolean {
    if (platform == Platform.IOS && !ios.supported) return false
    if (platform == Platform.Android && !android.supported) return false
    val appVariants = buildSet {
        if (aplite.supported) add(WatchType.APLITE)
        if (basalt.supported) add(WatchType.BASALT)
        if (chalk.supported) add(WatchType.CHALK)
        if (diorite.supported) add(WatchType.DIORITE)
        if (emery.supported) add(WatchType.EMERY)
        if (flint?.supported == true) add(WatchType.FLINT)
        if (gabbro?.supported == true) add(WatchType.GABBRO)
    }
    return watchType.getCompatibleAppVariants().intersect(appVariants).isNotEmpty()
}

fun LockerEntryCompatibility.findPlatform(watchType: WatchType): LockerEntryCompatibilityWatchPlatformDetails? {
    return when (watchType) {
        WatchType.APLITE -> aplite
        WatchType.BASALT -> basalt
        WatchType.CHALK -> chalk
        WatchType.DIORITE -> diorite
        WatchType.EMERY -> emery
        WatchType.FLINT -> flint
        WatchType.GABBRO -> gabbro
    }
}

val DEFAULT_CATEGORIES_FACES = listOf(
    StoreCategory(
        applicationIds = emptyList(),
        color = "ffffff",
        icon = emptyMap(),
        id = "528d3ef2dc7b5f580700000a",
        links = mapOf("apps" to "/api/v1/apps/category/faces"),
        name = "Faces",
        slug = "faces"
    ),
)

val DEFAULT_CATEGORIES_APPS = listOf(
    StoreCategory(
        applicationIds = emptyList(),
        color = "3db9e6",
        icon = mapOf("88x88" to "https://assets2.rebble.io/88x88/0QTBuPgXR8GAOMW0fJaA"),
        id = "5261a8fb3b773043d500000c",
        links = mapOf("apps" to "/api/v1/apps/category/daily"),
        name = "Daily",
        slug = "daily"
    ),
    StoreCategory(
        applicationIds = emptyList(),
        color = "fdbf37",
        icon = mapOf("88x88" to "https://assets2.rebble.io/88x88/Lhxn2MNYQruUOPNkreOs"),
        id = "5261a8fb3b773043d500000f",
        links = mapOf("apps" to "/api/v1/apps/category/tools-and-utilities"),
        name = "Tools & Utilities",
        slug = "tools-and-utilities"
    ),
    StoreCategory(
        applicationIds = emptyList(),
        color = "FF9000",
        icon = mapOf("88x88" to "https://assets2.rebble.io/88x88/WLi53fwzS2CKqMOAytF7"),
        id = "5261a8fb3b773043d5000001",
        links = mapOf("apps" to "/api/v1/apps/category/notifications"),
        name = "Notifications",
        slug = "notifications"
    ),
    StoreCategory(
        applicationIds = emptyList(),
        color = "fc4b4b",
        icon = mapOf("88x88" to "https://assets2.rebble.io/88x88/TpLgG0W6TT6Pt6Nm3t91"),
        id = "5261a8fb3b773043d5000008",
        links = mapOf("apps" to "/api/v1/apps/category/remotes"),
        name = "Remotes",
        slug = "remotes"
    ),
    StoreCategory(
        applicationIds = emptyList(),
        color = "98D500",
        icon = mapOf("88x88" to "https://assets2.rebble.io/88x88/xeW2tf3BSmWWBRyfmCZn"),
        id = "5261a8fb3b773043d5000004",
        links = mapOf("apps" to "/api/v1/apps/category/health-and-fitness"),
        name = "Health & Fitness",
        slug = "health-and-fitness"
    ),
    StoreCategory(
        applicationIds = emptyList(),
        color = "b57ad3",
        icon = mapOf("88x88" to "https://assets2.rebble.io/88x88/Xji7xwyYSzqR1ANNhTyi"),
        id = "5261a8fb3b773043d5000012",
        links = mapOf("apps" to "/api/v1/apps/category/games"),
        name = "Games",
        slug = "games"
    ),
)
