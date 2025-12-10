package coredevices.pebble.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.CarouselState
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coreapp.pebble.generated.resources.Res
import coreapp.pebble.generated.resources.apps
import coreapp.pebble.generated.resources.faces
import coredevices.database.AppstoreCollectionDao
import coredevices.database.AppstoreSource
import coredevices.pebble.PebbleDeepLinkHandler
import coredevices.pebble.Platform
import coredevices.pebble.account.PebbleAccount
import coredevices.pebble.rememberLibPebble
import coredevices.pebble.services.AppStoreHome
import coredevices.pebble.services.RealPebbleWebServices
import coredevices.pebble.services.StoreApplication
import coredevices.pebble.services.StoreCategory
import coredevices.pebble.services.StoreSearchResult
import coredevices.ui.PebbleElevatedButton
import coredevices.util.CoreConfigFlow
import io.rebble.libpebblecommon.connection.AppContext
import io.rebble.libpebblecommon.connection.ConnectedPebbleDevice
import io.rebble.libpebblecommon.connection.KnownPebbleDevice
import io.rebble.libpebblecommon.database.entity.CompanionApp
import io.rebble.libpebblecommon.locker.AppPlatform
import io.rebble.libpebblecommon.locker.AppType
import io.rebble.libpebblecommon.locker.LockerWrapper
import io.rebble.libpebblecommon.locker.SystemApps
import io.rebble.libpebblecommon.locker.findCompatiblePlatform
import io.rebble.libpebblecommon.metadata.WatchType
import io.rebble.libpebblecommon.util.getTempFilePath
import io.rebble.libpebblecommon.web.LockerEntryCompanionApp
import io.rebble.libpebblecommon.web.LockerEntryCompatibility
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import rememberOpenDocumentLauncher
import theme.coreOrange
import kotlin.uuid.Uuid

enum class LockerTab(val title: StringResource) {
    Watchfaces(Res.string.faces),
    Apps(Res.string.apps),
}

const val REBBLE_LOGIN_URI = "https://boot.rebble.io"
private const val LOCKER_UI_LOAD_LIMIT = 100
private val logger = Logger.withTag("LockerScreen")

class LockerViewModel(
    private val pebbleWebServices: RealPebbleWebServices,
    private val collectionsDao: AppstoreCollectionDao,
) : ViewModel() {
    val storeHome = mutableStateOf<List<Pair<AppstoreSource, AppStoreHome?>>>(emptyList())
    val storeSearchResults = MutableStateFlow<List<CommonApp>>(emptyList())

    fun refreshStore(type: AppType, platform: WatchType): Deferred<Unit> {
        val finished = CompletableDeferred<Unit>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { pebbleWebServices.fetchAppStoreHome(type, platform) }
            if (!result.all { it.second == null }) {
                val collectionRules = collectionsDao.getAllCollections().first().groupBy { it.sourceId }
                storeHome.value = result.map { (source, home) ->
                    val homeFiltered = home?.let {
                        val default = source.id == result.first().first.id
                        it.copy(collections = it.collections.filter { col ->
                            collectionRules[source.id]?.firstOrNull { it.slug == col.slug && it.type == type }?.enabled ?: default
                        })
                    }
                    source to homeFiltered
                }
            }
            finished.complete(Unit)
        }
        return finished
    }

    fun searchStore(search: String, watchType: WatchType, platform: Platform, appType: AppType?) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { pebbleWebServices.searchAppStore(search, appType) }
            storeSearchResults.value = result.mapNotNull { (source, app) ->
                app.asCommonApp(watchType, platform, source)
            }.filter {
                it.isCompatible && it.type == appType
            }
//            logger.v { "result: $result" }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LockerScreen(
    navBarNav: NavBarNav,
    topBarParams: TopBarParams,
    tab: LockerTab,
) {
    Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        val viewModel = koinViewModel<LockerViewModel>()
        val scope = rememberCoroutineScope()
        val libPebble = rememberLibPebble()
        val watchesFiltered = remember {
            libPebble.watches.map {
                it.sortedWith(PebbleDeviceComparator).filterIsInstance<KnownPebbleDevice>()
                    .firstOrNull()
            }
        }
        val lastConnectedWatch by watchesFiltered.collectAsState(null)
        val runningApp by (lastConnectedWatch as? ConnectedPebbleDevice)?.runningApp?.collectAsState(
            null
        ) ?: mutableStateOf(null)
        val watchType = lastConnectedWatch?.watchType?.watchType ?: WatchType.DIORITE
        val appContext = koinInject<AppContext>()
        val pebbleAccount = koinInject<PebbleAccount>()
        val loggedIn by pebbleAccount.loggedIn.collectAsState()
        val launchInstallAppDialog = rememberOpenDocumentLauncher {
            it?.firstOrNull()?.let { file ->
                val tempAppPath = getTempFilePath(appContext, "temp.pbw")
                SystemFileSystem.sink(tempAppPath).use { sink ->
                    file.source.use { source ->
                        source.transferTo(sink)
                    }
                }
                scope.launch {
                    libPebble.sideloadApp(tempAppPath)
                }
            }
        }
        val platform: Platform = koinInject()
        var isRefreshing by remember { mutableStateOf(false) }
        val title = stringResource(tab.title)

        fun openInstallAppDialog() {
            launchInstallAppDialog(listOf("*/*"))
        }

        val type = when (tab) {
            LockerTab.Watchfaces -> AppType.Watchface
            LockerTab.Apps -> AppType.Watchapp
        }
        var searchType by remember { mutableStateOf<AppType?>(type) }
        val coreConfigFlow: CoreConfigFlow = koinInject()
        val coreConfig by coreConfigFlow.flow.collectAsState()

        LaunchedEffect(Unit) {
            topBarParams.searchAvailable(true)
            topBarParams.actions {
                TopBarIconButtonWithToolTip(
                    onClick = ::openInstallAppDialog,
                    icon = Icons.Filled.UploadFile,
                    description = "Sideload App",
                )
            }
            topBarParams.title(title)
            topBarParams.canGoBack(false)
            if (coreConfig.useNativeAppStore) {
                isRefreshing = true
                viewModel.refreshStore(type, watchType).invokeOnCompletion {
                    isRefreshing = false
                }
            }
        }
        val deepLinkHandler: PebbleDeepLinkHandler = koinInject()
        val initialLockerSyncInProgress by deepLinkHandler.initialLockerSync.collectAsState()
        LaunchedEffect(initialLockerSyncInProgress) {
            if (initialLockerSyncInProgress) {
                topBarParams.showSnackbar("Loading Locker")
            }
        }

        val uriHandler = LocalUriHandler.current

        val lockerQuery = remember(
            type,
            topBarParams.searchState.query
        ) {
            libPebble.getLocker(
                type = type,
                searchQuery = topBarParams.searchState.query,
                limit = LOCKER_UI_LOAD_LIMIT,
            )
        }
        if (coreConfig.useNativeAppStore) {
            LaunchedEffect(topBarParams.searchState.query, searchType) {
                if (topBarParams.searchState.query.isNotEmpty()) {
                    viewModel.searchStore(topBarParams.searchState.query, watchType, platform, searchType)
                }
            }
        }
        val filteredLockerEntries by lockerQuery.collectAsState(emptyList())
        val onWatch by remember(filteredLockerEntries, watchType) {
            derivedStateOf {
                filteredLockerEntries.filter {
                    it.isSynced() && it.findCompatiblePlatform(
                        watchType
                    ).isCompatible()
                }.map { it.asCommonApp(watchType) }
            }
        }
        val notOnWatch by remember(filteredLockerEntries, watchType) {
            derivedStateOf {
                filteredLockerEntries.filter {
                    !it.isSynced() && it.findCompatiblePlatform(
                        watchType
                    ).isCompatible()
                }.map { it.asCommonApp(watchType) }
            }
        }
        val notCompatible by remember(filteredLockerEntries, watchType) {
            derivedStateOf {
                filteredLockerEntries.filter {
                    !it.findCompatiblePlatform(watchType).isCompatible()
                }.map { it.asCommonApp(watchType) }
            }
        }

        Scaffold(
            floatingActionButton = {
                if (loggedIn != null && !coreConfig.useNativeAppStore) {
                    FloatingActionButton(
                        onClick = {
                            navBarNav.navigateTo(
                                PebbleNavBarRoutes.AppStoreRoute(
                                    when (tab) {
                                        LockerTab.Watchfaces -> AppType.Watchface
                                        LockerTab.Apps -> AppType.Watchapp
                                    }.code
                                )
                            )
                        }
                    ) {
                        Icon(Icons.Filled.Add, "Add")
                    }
                }
            },
        ) {
            var itemWidth by remember { mutableStateOf(0.dp) } // need to keep this even if we return from search to reduce jank
            if (topBarParams.searchState.query.isNotEmpty() && coreConfig.useNativeAppStore) {
                val results by viewModel.storeSearchResults.combine(lockerQuery) { store, locker ->
                    val lockerApps = locker.map { it.asCommonApp(watchType) }
                    if (coreConfig.useNativeAppStore) {
                        lockerApps.filter {
                            it.type == searchType
                        } + store.filter { a ->
                            !lockerApps.any { b -> a.uuid == b.uuid }
                        }
                    } else {
                        lockerApps
                    }
                }.collectAsState(initial = emptyList())

                Column {
                    Row {
                        FilterChip(
                            selected = searchType == null,
                            onClick = { searchType = null },
                            label = { Text("All") },
                            modifier = Modifier.padding(4.dp),
                        )
                        FilterChip(
                            selected = searchType == AppType.Watchapp,
                            onClick = { searchType = AppType.Watchapp },
                            label = { Text("Apps") },
                            modifier = Modifier.padding(4.dp),
                        )
                        FilterChip(
                            selected = searchType == AppType.Watchface,
                            onClick = { searchType = AppType.Watchface },
                            label = { Text("Faces") },
                            modifier = Modifier.padding(4.dp),
                        )
                    }
                    SearchResultsList(
                        results = results,
                        navBarNav = navBarNav,
                        topBarParams = topBarParams,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = {
                    isRefreshing = true
                    logger.v { "set isRefreshing to true" }
                    val lockerFinished = libPebble.requestLockerSync()
                    val storeFinished = if (coreConfig.useNativeAppStore) {
                        viewModel.refreshStore(type, watchType)
                    } else {
                        CompletableDeferred(Unit)
                    }
                    scope.launch {
                        awaitAll(lockerFinished, storeFinished)
                        logger.v { "set isRefreshing to false" }
                        isRefreshing = false
                    }
                }) {
                    if (coreConfig.useNativeAppStore) {
                        val density = LocalDensity.current
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { (width, height) ->
                                    itemWidth = with(density) {
                                        (width.toDp() / 3).coerceAtMost(132.dp) - 14.dp
                                    }
                                }
                        ) {
                            if (loggedIn == null) {
                                item(contentType = "login_button") {
                                    PebbleElevatedButton(
                                        text = "Login to Rebble to see App Store and Locker",
                                        onClick = {
                                            uriHandler.openUri(REBBLE_LOGIN_URI)
                                        },
                                        primaryColor = true,
                                        modifier = Modifier.padding(5.dp).align(Alignment.Center),
                                    )
                                }
                            }
                            @Composable
                            fun Carousel(
                                title: String,
                                items: List<CommonApp>,
                                onClick: (() -> Unit)? = null
                            ) {
                                AppCarousel(
                                    title = title,
                                    items = items,
                                    navBarNav = navBarNav,
                                    runningApp = runningApp,
                                    topBarParams = topBarParams,
                                    itemWidth = itemWidth,
                                    onClick = onClick
                                )
                            }
                            item(contentType = "app_carousel") { Carousel("On My Watch", onWatch) }

                            item(contentType = "app_carousel") { Carousel("Recent", notOnWatch) }

                            val storeHomes by viewModel.storeHome
                            // TODO categories
//                            logger.v { "home.collections (has home = ${home != null}): ${home?.collections}" }
                            storeHomes.forEach { (source, home) ->
                                home?.let {
                                    if (storeHomes.size > 1) {
                                        item(contentType = "source_title") {
                                            Text(source.title, modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp), style = MaterialTheme.typography.headlineMedium)
                                        }
                                    }
                                    items(home.collections.size, contentType = { "app_carousel_collection" }) {
                                        val collection = home.collections[it]
                                        val collectionApps =
                                            remember(
                                                home,
                                                collection,
                                                watchType,
                                                filteredLockerEntries
                                            ) {
                                                collection.applicationIds.mapNotNull { appId ->
                                                    home.applications.find { app ->
                                                        app.id == appId && !filteredLockerEntries.any {
                                                            it.properties.id == Uuid.parse(
                                                                app.uuid
                                                            )
                                                        }
                                                    }?.asCommonApp(watchType, platform, source, home.categories)
                                                }
                                            }
                                        Carousel(collection.name, collectionApps, onClick = {
                                            navBarNav.navigateTo(
                                                PebbleNavBarRoutes.AppStoreCollectionRoute(
                                                    sourceId = source.id,
                                                    path = "collection/${collection.slug}",
                                                    title = collection.name,
                                                    appType = type,
                                                )
                                            )
                                        })
                                    }
                                }
                            }

                            item(contentType = "app_carousel") { Carousel("Not Compatible", notCompatible) }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(4.dp),
                        ) {
                            if (loggedIn == null) {
                                item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                                    PebbleElevatedButton(
                                        text = "Login to Rebble to see App Store and Locker",
                                        onClick = {
                                            uriHandler.openUri(REBBLE_LOGIN_URI)
                                        },
                                        primaryColor = true,
                                        modifier = Modifier.padding(5.dp).align(Alignment.Center),
                                    )
                                }
                            }
                            val watchName = lastConnectedWatch?.displayName() ?: ""
                            if (onWatch.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("On Watch $watchName") }
                                items(
                                    items = onWatch,
                                    key = { it.uuid }
                                ) { entry ->
                                    LegacyWatchfaceCard(
                                        entry,
                                        navBarNav,
                                        runningApp == entry.uuid,
                                        topBarParams,
                                    )
                                }
                            }
                            if (notOnWatch.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Not On Watch $watchName") }
                                items(
                                    items = notOnWatch,
                                    key = { it.uuid }
                                ) { entry ->
                                    LegacyWatchfaceCard(
                                        entry,
                                        navBarNav,
                                        runningApp == entry.uuid,
                                        topBarParams,
                                    )
                                }
                            }
                            if (notCompatible.isNotEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Not Compatible with $watchName") }
                                items(
                                    items = notCompatible,
                                    key = { it.uuid }
                                ) { entry ->
                                    LegacyWatchfaceCard(
                                        entry,
                                        navBarNav,
                                        runningApp == entry.uuid,
                                        topBarParams,
                                    )
                                }
                            }
                        }
                    }
                }
            }

        }
    }
}

@Composable
fun SearchResultsList(
    results: List<CommonApp>,
    navBarNav: NavBarNav,
    topBarParams: TopBarParams,
    pebbleWebServices: RealPebbleWebServices = koinInject(),
    modifier: Modifier = Modifier,
) {
    val storeApps = results.filter { it.commonAppType is CommonAppType.Store }
    val lockerApps = results.filter { it.commonAppType is CommonAppType.Locker }
    val scope = rememberCoroutineScope()
    LazyColumn(modifier) {
        if (lockerApps.isNotEmpty()) {
            items(
                items = lockerApps,
                key = { it.uuid }
            ) { entry ->
                NativeWatchfaceListItem(
                    entry,
                    navBarNav,
                    topBarParams,
                    onClick = {
                        navBarNav.navigateTo(
                            PebbleNavBarRoutes.LockerAppRoute(
                                uuid = entry.uuid.toString(),
                                storedId = (entry.commonAppType as? CommonAppType.Store)?.storedId,
                                storeSource = (entry.commonAppType as? CommonAppType.Store)?.storeSource?.let { Json.encodeToString(it) },
                            )
                        )
                    }
                )
            }
        }
        if (storeApps.isNotEmpty()) {
            item {
                Text(
                    "From the store",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            items(
                items = storeApps,
                key = { it.uuid }
            ) { entry ->
                NativeWatchfaceListItem(
                    entry,
                    navBarNav,
                    topBarParams,
                    onClick = {
                        scope.launch {
                            val sources = withContext(Dispatchers.IO) { pebbleWebServices.searchUuidInSources(entry.uuid) }
                            val (bestId, bestSource) = withContext(Dispatchers.IO) {
                                sources.maxByOrNull { (id, source) ->
                                    pebbleWebServices.fetchAppStoreApp(id, null, source.url)
                                        ?.data
                                        ?.firstOrNull()
                                        ?.latestRelease?.version ?: "0"
                                } ?: (null to null)
                            }
                            navBarNav.navigateTo(
                                PebbleNavBarRoutes.LockerAppRoute(
                                    uuid = entry.uuid.toString(),
                                    storedId = bestId ?:(entry.commonAppType as? CommonAppType.Store)?.storedId,
                                    storeSource = (bestSource ?: (entry.commonAppType as? CommonAppType.Store)?.storeSource)
                                        ?.let { Json.encodeToString(it) },
                                    storeSources = Json.encodeToString(sources)
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AppCarousel(
    title: String,
    items: List<CommonApp>,
    navBarNav: NavBarNav,
    runningApp: Uuid?,
    topBarParams: TopBarParams,
    itemWidth: Dp = 132.dp,
    onClick: (() -> Unit)? = null,
) {
    if (items.isEmpty()) {
        return
    }
    Column {
        Row(modifier = Modifier.padding(horizontal = 16.dp).let {
            if (onClick != null) {
                it.clickable { onClick() }
            } else {
                it
            }
        }) {
            Text(title, fontSize = 24.sp, modifier = Modifier.padding(vertical = 8.dp))
            Spacer(modifier = Modifier.weight(1f))
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Default.ArrowForward, contentDescription = "See all", modifier = Modifier)
            }
        }
        // Do this manually rather than using rememberCarouselState - that breaks when we change the
        // size.
        val state = rememberSaveable(items.size, saver = CarouselState.Saver) {
            CarouselState(
                currentItem = 0,
                currentItemOffsetFraction = 0F,
                itemCount = { items.size },
            )
        }
        HorizontalUncontainedCarousel(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(vertical = 6.dp),
            itemWidth = itemWidth,
            itemSpacing = 8.dp,
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) { i ->
            val entry = items.getOrNull(i) ?: return@HorizontalUncontainedCarousel
            NativeWatchfaceCard(
                entry,
                navBarNav,
                runningApp == entry.uuid,
                topBarParams,
            )
        }
    }
}


@Composable
fun LockerScreenPreviewWrapper(tab: LockerTab) {
    PreviewWrapper {
        LockerScreen(
            navBarNav = NoOpNavBarNav,
            topBarParams = WrapperTopBarParams,
            tab = tab,
        )
    }
}

val testApps = listOf(
    CommonApp(
        title = "Sample Watchface",
        developerName = "Dev Name",
        uuid = Uuid.parse("123e4567-e89b-12d3-a456-426614174000"),
        androidCompanion = null,
        commonAppType = CommonAppType.Locker(
            sideloaded = false,
            configurable = true,
            sync = true,
            order = 0,
        ),
        type = AppType.Watchface,
        category = "Fun",
        version = "1.0",
        listImageUrl = null,
        screenshotImageUrl = null,
        isCompatible = true,
        hearts = 42,
        description = "A sample watchface for preview purposes.",
        isNativelyCompatible = true
    ),
    CommonApp(
        title = "Another Watchface",
        developerName = "Another Dev",
        uuid = Uuid.parse("223e4567-e89b-12d3-a456-426614174000"),
        androidCompanion = null,
        commonAppType = CommonAppType.Locker(
            sideloaded = true,
            configurable = false,
            sync = false,
            order = 1,
        ),
        type = AppType.Watchface,
        category = "Utility",
        version = "2.1",
        listImageUrl = null,
        screenshotImageUrl = null,
        isCompatible = true,
        hearts = 7,
        description = "Another sample watchface for preview purposes.",
        isNativelyCompatible = true
    ),
    CommonApp(
        title = "Third Watchface",
        developerName = "Third Dev",
        uuid = Uuid.parse("323e4567-e89b-12d3-a456-426614174000"),
        androidCompanion = null,
        commonAppType = CommonAppType.Locker(
            sideloaded = false,
            configurable = true,
            sync = true,
            order = 2,
        ),
        type = AppType.Watchface,
        category = "Sport",
        version = "3.3",
        listImageUrl = null,
        screenshotImageUrl = null,
        isCompatible = false,
        hearts = 15,
        description = "Yet another sample watchface for preview purposes.",
        isNativelyCompatible = true
    )
)

@Preview
@Composable
fun LockerCarouselPreview() {
    PreviewWrapper {
        Column(modifier = Modifier.width(700.dp).verticalScroll(rememberScrollState())) {
            AppCarousel(
                title = "On My Watch",
                items = testApps,
                navBarNav = NoOpNavBarNav,
                runningApp = null,
                topBarParams = WrapperTopBarParams,
            )
        }
    }
}

fun LockerWrapper.asCommonApp(watchType: WatchType?): CommonApp {
    val compatiblePlatfom = findCompatiblePlatform(watchType)
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
        listImageUrl = compatiblePlatfom?.listImageUrl,
        screenshotImageUrl = compatiblePlatfom?.screenshotImageUrl,
        isCompatible = compatiblePlatfom.isCompatible(),
        hearts = when (this) {
            is LockerWrapper.NormalApp -> properties.hearts
            is LockerWrapper.SystemApp -> null
        },
        description = compatiblePlatfom?.description,
        isNativelyCompatible = when (this) {
            is LockerWrapper.NormalApp -> {
                val nativelyCompatible = when (watchType) {
                    // Emery is the only platform where "compatible" apps can be used but are
                    // "suboptimal" (need scaling). Enable flagging that.
                    WatchType.EMERY -> properties.platforms.any { it.watchType == watchType }
                    else -> true
                }
                nativelyCompatible
            }

            is LockerWrapper.SystemApp -> true
        },
    )
}

fun LockerEntryCompanionApp.asCompanionApp(): CompanionApp = CompanionApp(
    id = id,
    icon = icon,
    name = name,
    url = url,
    required = required,
    pebblekitVersion = pebblekitVersion,
)

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
    }
    return watchType.getCompatibleAppVariants().intersect(appVariants).isNotEmpty()
}

fun StoreApplication.asCommonApp(watchType: WatchType, platform: Platform, source: AppstoreSource, categories: List<StoreCategory>?): CommonApp? {
    val appType = AppType.fromString(type)
    if (appType == null) {
        logger.w { "StoreApplication.asCommonApp() unknown type: $type" }
        return null
    }
    return CommonApp(
        title = title,
        developerName = author,
        uuid = Uuid.parse(uuid),
        androidCompanion = companions.android?.asCompanionApp(),
        commonAppType = CommonAppType.Store(
            storedId = id,
            storeSource = source,
            developerId = developerId,
            sourceLink = this.source,
            categorySlug = categories?.firstOrNull { it.id == categoryId }?.slug
        ),
        type = appType,
        category = category,
        version = latestRelease.version,
        listImageUrl = listImage.values.firstOrNull(),
        screenshotImageUrl = screenshotImages.firstOrNull()?.values?.firstOrNull(),
        isCompatible = compatibility.isCompatible(watchType, platform),
        hearts = hearts,
        description = description,
        isNativelyCompatible = true, // TODO
    )
}

fun StoreSearchResult.asCommonApp(watchType: WatchType, platform: Platform, source: AppstoreSource): CommonApp? {
    val appType = AppType.fromString(type)
    if (appType == null) {
        logger.w { "StoreApplication.asCommonApp() unknown type: $type" }
        return null
    }
    return CommonApp(
        title = title,
        developerName = author,
        uuid = Uuid.parse(uuid),
        androidCompanion = null,
        commonAppType = CommonAppType.Store(storedId = id, storeSource = source, developerId = null, sourceLink = null, categorySlug = null),
        type = appType,
        category = category,
        version = null,
        listImageUrl = listImage,
        // TODO add fallback hardwarePlatforms
//        screenshotImageUrl = assetCollections.find { it.hardwarePlatform == watchType.codename }?.screenshots?.firstOrNull() ?: screenshotImages.firstOrNull(),
        screenshotImageUrl = screenshotImages.firstOrNull(),
        isCompatible = compatibility.isCompatible(watchType, platform),
        hearts = hearts,
        description = description,
        isNativelyCompatible = true, // TODO
    )
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
)

interface CommonAppTypeLocal {
    val order: Int
}

//interface CommonAppTypeFromStore {
//    val storedId: String
//}

sealed class CommonAppType {
    data class Locker(
        val sideloaded: Boolean,
        val configurable: Boolean,
        val sync: Boolean,
        override val order: Int,
//        override val storedId: String,
    ) : CommonAppType(), CommonAppTypeLocal//, CommonAppTypeFromStore

    data class Store(
        val storedId: String,
        val storeSource: AppstoreSource,
        val developerId: String?,
        val sourceLink: String?,
        val categorySlug: String?
    ) : CommonAppType()//, CommonAppTypeFromStore

    data class System(
        val app: SystemApps,
        override val order: Int,
    ) : CommonAppType(), CommonAppTypeLocal
}

@Composable
fun NativeWatchfaceCard(
    entry: CommonApp,
    navBarNav: NavBarNav,
    running: Boolean,
    topBarParams: TopBarParams,
) {
    Card(
        modifier = Modifier.padding(7.dp)
            .clickable {
                navBarNav.navigateTo(
                    PebbleNavBarRoutes.LockerAppRoute(
                        uuid = entry.uuid.toString(),
                        storedId = (entry.commonAppType as? CommonAppType.Store)?.storedId,
                        storeSource = (entry.commonAppType as? CommonAppType.Store)?.storeSource?.let { Json.encodeToString(it) },
                    )
                )
            }.border(
                width = 2.dp,
                color = if (running) coreOrange else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                val imageModifier =
                    Modifier.padding(top = 8.dp, bottom = 8.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(7.dp))
                if (entry.isCompatible) {
                    AppImage(
                        entry,
                        modifier = imageModifier,
                        size = 116.dp,
                    )
                    val platform: Platform = koinInject()
                    if (platform == Platform.Android) {
                        val companionApp = entry.androidCompanion
                        if (companionApp != null) {
                            IconButton(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(40.dp),
                                onClick = {
                                    topBarParams.showSnackbar("Android companions apps not supported yet")
                                },
                            ) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = "Android companions apps not supported yet",
                                    modifier = Modifier.fillMaxSize(),
                                    tint = coreOrange,
                                )
                            }
                        }
                    }
                } else {
                    Box(modifier = imageModifier, contentAlignment = Alignment.Center) {
                        Text("Not Compatible", fontSize = 15.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            Text(
                entry.title,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Start)
                    .padding(vertical = 5.dp, horizontal = 5.dp),
                fontWeight = FontWeight.Bold,
                overflow = TextOverflow.Ellipsis,
            )
//            Text(
//                entry.developerName,
//                color = Color.Gray,
//                fontSize = 10.sp,
//                lineHeight = 12.sp,
//                maxLines = 1,
//                modifier = Modifier.align(Alignment.CenterHorizontally)
//                    .padding(top = 2.dp, bottom = 5.dp),
//            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegacyWatchfaceCard(
    entry: CommonApp,
    navBarNav: NavBarNav,
    running: Boolean,
    topBarParams: TopBarParams,
) {
    ElevatedCard(
        elevation = CardDefaults.cardElevation(
            defaultElevation = 5.dp
        ),
        modifier = Modifier.padding(7.dp)
            .clickable {
                navBarNav.navigateTo(
                    PebbleNavBarRoutes.LockerAppRoute(
                        uuid = entry.uuid.toString(),
                        storedId = null,
                        storeSource = null
                    )
                )
            }.border(
                width = 2.dp,
                color = if (running) coreOrange else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            ),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                val modifier =
                    Modifier.padding(top = 12.dp, bottom = 8.dp).align(Alignment.Center)
                if (entry.isCompatible) {
                    AppImage(
                        entry,
                        modifier = modifier,
                        size = 120.dp,
                    )
                    if (!entry.isNativelyCompatible) {
                        IconButton(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(7.dp)
                                .size(30.dp),
                            onClick = {
                                topBarParams.showSnackbar("Not natively compatible, but can be scaled")
                            },
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = "Not natively compatible, but can be scaled",
                                modifier = Modifier.fillMaxSize(),
                                tint = coreOrange,
                            )
                        }
                    }
                } else {
                    Box(modifier = modifier, contentAlignment = Alignment.Center) {
                        Text("Not Compatible", fontSize = 15.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            Text(
                entry.title,
                fontSize = 13.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .padding(vertical = 5.dp, horizontal = 2.dp),
                fontWeight = FontWeight.Bold,
            )
            Text(
                entry.developerName,
                color = Color.Gray,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .padding(top = 2.dp, bottom = 5.dp),
            )
        }
    }
}

@Composable
fun NativeWatchfaceListItem(
    entry: CommonApp,
    navBarNav: NavBarNav,
    topBarParams: TopBarParams,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .clickable {
                onClick()
            }
            .padding(vertical = 4.dp),
        headlineContent = {
            Text(entry.title, fontWeight = FontWeight.Bold)
        },
        supportingContent = {
            Text(entry.developerName, color = Color.Gray)
        },
        leadingContent = {
            AppImage(
                entry,
                modifier = Modifier.width(48.dp),
                size = 48.dp,
            )
        },
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
    )
}

fun AppPlatform?.isCompatible(): Boolean = this != null

fun CommonApp.isSynced(): Boolean = when (commonAppType) {
    is CommonAppType.Locker -> commonAppType.sync
    is CommonAppType.Store -> false
    is CommonAppType.System -> true
}

fun LockerWrapper.isSynced(): Boolean = when (this) {
    is LockerWrapper.NormalApp -> sync
    is LockerWrapper.SystemApp -> true
}

@Composable
fun AppImage(entry: CommonApp, modifier: Modifier, size: Dp) {
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    val placeholder = remember {
        ColorPainter(placeholderColor)
    }
    val context = LocalPlatformContext.current
    when (entry.commonAppType) {
        is CommonAppType.Locker, is CommonAppType.Store -> {
            val req = remember(entry) {
                val url = when (entry.type) {
                    AppType.Watchapp -> entry.listImageUrl
                    AppType.Watchface -> entry.screenshotImageUrl
                }
                ImageRequest.Builder(context)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .data(url)
                    .build()
            }
            AsyncImage(
                model = req,
                contentDescription = null,
                modifier = modifier.requiredHeight(size),
                placeholder = placeholder,
                onError = { e ->
                    logger.w(e.result.throwable) { "Error loading app image for ${entry.uuid}" }
                }
            )
        }

        is CommonAppType.System -> {
            val icon = when (entry.commonAppType.app) {
                SystemApps.Calendar -> Icons.Default.DateRange
            }
            Icon(icon, contentDescription = null, modifier = modifier.size(size))
        }
    }
}